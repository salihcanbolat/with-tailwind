package com.damla.backend

import com.damla.backend.catalog.Target
import com.damla.backend.devices.AssetSettings
import com.damla.backend.devices.DeviceSettings
import com.damla.backend.devices.SettingsResolver
import com.damla.backend.state.GroupKey
import com.damla.backend.state.MemoryCodec
import com.damla.engine.AssetMemory
import com.damla.engine.Interval
import com.damla.engine.PartMemory
import com.damla.engine.PartState
import com.damla.engine.Pending
import com.damla.engine.State
import com.damla.engine.Thresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StateTest {
    @Test
    fun `engine memory survives a round trip through JSON`() {
        val part =
            PartMemory(
                confirmed = PartState(State.CLOSED, State.UP, 0.12, 0.3, Interval.FIFTEEN_MINUTES, WEDNESDAY_NOON),
                raw = State.UP,
                pending = Pending(State.DOWN, WEDNESDAY_NOON + 5.seconds),
            )
        val memory = AssetMemory(marketOpen = false, drop = part, upperRing = part.copy(pending = null), lowerRing = null)

        assertEquals(memory, MemoryCodec.decode(MemoryCodec.encode(memory)))
    }

    @Test
    fun `group keys name the target, intervals and thresholds`() {
        val key = GroupKey.default(Target.parse("usdtry")!!)

        assertEquals("usdtry|15m|4h|1d|0.1|0.3", key.id)
        assertEquals(Interval.ONE_DAY, key.longest)
        assertEquals("usdtry", key.config.asset)
        assertEquals("btcusdt|15m|4h|1d|0.5|1.5", GroupKey.default(Target.parse("btcusdt")!!).id)
        assertEquals("btcusdt/xau_gram|15m|4h|1d|0.5|1.5", GroupKey.default(Target.parse("btcusdt/xau_gram")!!).id)
    }

    @Test
    fun `device settings resolve to groups with defaults filled in`() {
        val settings =
            DeviceSettings(
                fcmToken = "token",
                assets =
                    listOf(
                        AssetSettings(id = "usdtry"),
                        AssetSettings(id = "xau_gram/xag_gram", drop = "1h", upperRing = "1d", lowerRing = "1w", stable = 0.2, wave = 0.5),
                    ),
            )

        val groups = assertIs<SettingsResolver.Result.Valid>(SettingsResolver.resolve(settings)).groups

        assertEquals(GroupKey.default(Target.parse("usdtry")!!), groups["usdtry"])
        val cross = groups.getValue("xau_gram/xag_gram")
        assertEquals(listOf(Interval.ONE_HOUR, Interval.ONE_DAY, Interval.ONE_WEEK), listOf(cross.drop, cross.upperRing, cross.lowerRing))
        assertEquals(Thresholds(0.2, 0.5), cross.thresholds)
    }

    @Test
    fun `invalid device settings are explained`() {
        val settings =
            DeviceSettings(
                assets =
                    listOf(
                        AssetSettings(id = "nope"),
                        AssetSettings(id = "usdtry", drop = "15x"),
                        AssetSettings(id = "eurtry", stable = 0.5, wave = 0.4),
                        AssetSettings(id = "eurtry"),
                    ),
            )

        val errors = assertIs<SettingsResolver.Result.Invalid>(SettingsResolver.resolve(settings)).errors

        assertEquals(4, errors.size, errors.toString())
        assertTrue(errors.any { "duplicate" in it })
        assertTrue(errors.any { "nope" in it })
        assertTrue(errors.any { "usdtry.drop" in it })
        assertTrue(errors.any { it.startsWith("eurtry:") })
    }

    @Test
    fun `device ids and list sizes are limited`() {
        assertTrue(SettingsResolver.isValidDeviceId("a1b2c3d4-e5f6"))
        assertTrue(!SettingsResolver.isValidDeviceId("short"))
        assertTrue(!SettingsResolver.isValidDeviceId("has space in it"))
        val tooMany = DeviceSettings(assets = (1..51).map { AssetSettings(id = "usdtry") })
        assertIs<SettingsResolver.Result.Invalid>(SettingsResolver.resolve(tooMany))
    }
}
