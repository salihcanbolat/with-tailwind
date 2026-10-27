package com.damla.backend

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.AssetSettings
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.DeviceSettings
import com.damla.backend.alarms.AlarmRunner
import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.devices.InMemoryDeviceStore
import com.damla.backend.devices.SettingsResolver
import com.damla.backend.market.MarketCalendar
import com.damla.backend.prices.MarketData
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.prices.SourceSelector
import com.damla.backend.push.PushMessage
import com.damla.backend.push.SendResult
import com.damla.backend.state.StateRunner
import com.damla.backend.store.InMemoryStateStore
import com.damla.engine.GaugeEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class AlarmRunnerTest {
    private val now = WEDNESDAY_NOON
    private val market =
        MarketData(
            assets = AssetCatalog.assets.map { it.id },
            selector =
                SourceSelector(
                    preferences = AssetCatalog.assets.associate { it.id to listOf("test") },
                    staleAfter = mapOf("test" to 10.seconds),
                ),
            retention = 31.days,
        )
    private val runner = StateRunner(GaugeEngine(), market, MarketCalendar.bundled(), InMemoryStateStore())
    private val registry = DeviceRegistry(InMemoryDeviceStore())
    private val sender = RecordingSender()
    private val alarms = AlarmRunner(registry, runner, sender)

    private val above =
        Alarm("a1", "usdtry", listOf(Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 101.0)), createdAt = now.toString())

    private suspend fun watch(
        vararg alarm: Alarm,
        token: String? = "token-1",
        assets: List<String> = listOf("usdtry"),
    ) {
        for (asset in listOf("usdtry", "xau_gram", "xag_gram")) market.load(asset, flatCandles(now, 8 * 24 * 60, 100.0))
        val result = registry.put("device-1", DeviceSettings(token, assets.map { AssetSettings(it) }, alarm.toList()), now)
        assertIs<SettingsResolver.Result.Valid>(result)
        runner.register(result.groups.values, now)
    }

    @Test
    fun `an alarm rings once when its conditions become true, with high priority`() =
        runTest {
            watch(above)
            alarms.evaluateAll(now)
            market.accept(PriceUpdate.Tick("usdtry", "test", 102.0, now + 1.seconds), now + 1.seconds)
            runner.evaluateAll(now + 1.seconds)

            alarms.evaluateAll(now + 1.seconds)
            alarms.evaluateAll(now + 2.seconds)

            assertEquals(
                listOf(
                    PushMessage(
                        "token-1",
                        mapOf(
                            "type" to "alarm",
                            "alarmId" to "a1",
                            "asset" to "usdtry",
                            "met" to "0",
                            "at" to (now + 1.seconds).toString(),
                        ),
                        PushMessage.Priority.HIGH,
                        6 * 3_600,
                    ),
                ),
                sender.sent,
            )
        }

    @Test
    fun `state conditions follow the device's own groups`() =
        runTest {
            val flat = Alarm("a2", "usdtry", listOf(Condition(ConditionType.STATE, AlarmPart.DROP, "FLAT")), createdAt = now.toString())
            watch(flat)

            alarms.evaluateAll(now)

            assertEquals(listOf("a2"), sender.sent.map { it.data["alarmId"] })
        }

    @Test
    fun `cross rates get windows for every alarm period`() =
        runTest {
            watch(assets = listOf("xau_gram/xag_gram"))
            val key = registry.get("device-1")!!.groups.getValue("xau_gram/xag_gram")

            val input = runner.alarmInput(key, now)

            assertEquals(setOf("15m", "1h", "1d", "1w"), input.windows.keys)
            assertEquals(1.0, input.price)
            assertEquals("FLAT", input.states[AlarmPart.DROP])
            assertTrue(input.marketOpen)
        }

    @Test
    fun `an unknown group has no states or price`() =
        runTest {
            val key =
                com.damla.backend.state.GroupKey
                    .default(
                        com.damla.backend.catalog.Target
                            .parse("usdtry")!!,
                    )

            val input = runner.alarmInput(key, now)

            assertNull(input.price)
            assertEquals(emptyMap(), input.states)
        }

    @Test
    fun `alarms must watch an asset of the list and be complete`() =
        runTest {
            val elsewhere = above.copy(id = "a3", assetId = "btcusdt")
            val empty = above.copy(id = "a4", conditions = emptyList())

            val result =
                registry.put(
                    "device-1",
                    DeviceSettings("t", listOf(AssetSettings("usdtry")), listOf(elsewhere, empty, empty)),
                    now,
                )

            assertEquals(
                listOf(
                    "duplicate alarm ids",
                    "a3: asset btcusdt is not in the watch list",
                    "a4: 1 to 3 conditions",
                    "a4: 1 to 3 conditions",
                ),
                assertIs<SettingsResolver.Result.Invalid>(result).errors,
            )
            val tooMany = List(SettingsResolver.MAX_ALARMS + 1) { above.copy(id = "a$it") }
            val crowded = registry.put("device-1", DeviceSettings("t", listOf(AssetSettings("usdtry")), tooMany), now)
            assertEquals(listOf("at most 50 alarms"), assertIs<SettingsResolver.Result.Invalid>(crowded).errors)
        }

    @Test
    fun `devices without a token are not evaluated and dead tokens are forgotten`() =
        runTest {
            watch(above.copy(conditions = listOf(Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 101.0))), token = null)
            alarms.evaluateAll(now)
            assertEquals(emptyList(), sender.sent)

            sender.result = SendResult.Unregistered
            watch(above.copy(conditions = listOf(Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 101.0))))
            alarms.evaluateAll(now)
            assertNull(registry.get("device-1")!!.token)

            sender.result = SendResult.Failed("down")
            watch(above.copy(id = "a5", conditions = listOf(Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 101.0))))
            alarms.evaluateAll(now)
            assertEquals(2, sender.sent.size)
        }
}
