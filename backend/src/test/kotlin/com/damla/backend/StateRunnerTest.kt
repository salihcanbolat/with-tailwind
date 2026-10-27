package com.damla.backend

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.catalog.Target
import com.damla.backend.market.MarketCalendar
import com.damla.backend.prices.MarketData
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.prices.SourceSelector
import com.damla.backend.state.GroupKey
import com.damla.backend.state.StateRunner
import com.damla.backend.store.InMemoryStateStore
import com.damla.engine.DropStateChanged
import com.damla.engine.GaugeEngine
import com.damla.engine.State
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StateRunnerTest {
    private val now = WEDNESDAY_NOON
    private val store = InMemoryStateStore()
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
    private val runner = StateRunner(GaugeEngine(), market, MarketCalendar.bundled(), store)

    private suspend fun flat(
        asset: String,
        price: Double,
        end: Instant = now,
    ) = market.load(asset, flatCandles(end, count = 2 * 24 * 60, price = price))

    private fun key(id: String) = GroupKey.default(Target.parse(id)!!)

    @Test
    fun `registering a group evaluates it at once`() =
        runTest {
            flat("usdtry", 100.0)

            runner.register(listOf(key("usdtry")), now)

            val states = assertNotNull(runner.states(key("usdtry")))
            assertEquals(
                listOf(State.FLAT, State.FLAT, State.FLAT),
                listOf(states.drop, states.upperRing, states.lowerRing).map { it.state },
            )
            assertEquals(100.0, runner.price(key("usdtry")))
        }

    @Test
    fun `a held change is reported once and its memory saved`() =
        runTest {
            flat("usdtry", 100.0)
            runner.register(listOf(key("usdtry")), now)
            market.accept(PriceUpdate.Tick("usdtry", "test", 100.2, now), now)

            assertEquals(emptyList(), runner.evaluateAll(now + 1.seconds))
            val events = runner.evaluateAll(now + 61.seconds)

            assertEquals(listOf(key("usdtry")), events.map { it.key })
            assertEquals(listOf(DropStateChanged("usdtry", State.FLAT, State.UP, now + 61.seconds)), events.single().events)
            assertTrue(key("usdtry").id in store.loadAll())
            assertEquals(emptyList(), runner.evaluateAll(now + 62.seconds))
        }

    @Test
    fun `a restarted runner continues from the saved memory`() =
        runTest {
            flat("usdtry", 100.0)
            runner.register(listOf(key("usdtry")), now)
            runner.saveAll(now)
            val restarted = StateRunner(GaugeEngine(), market, MarketCalendar.bundled(), store)
            restarted.restore()

            restarted.register(listOf(key("usdtry")), now + 10.seconds)

            assertEquals(now, restarted.states(key("usdtry"))?.drop?.since)
        }

    @Test
    fun `parts are closed while the market is closed`() =
        runTest {
            val saturday = Instant.parse("2026-10-31T12:00:00Z")
            flat("usdtry", 100.0, end = saturday)

            runner.register(listOf(key("usdtry")), saturday)

            assertEquals(State.CLOSED, runner.states(key("usdtry"))?.drop?.state)
            assertEquals(State.FLAT, runner.states(key("usdtry"))?.drop?.shapeState)
        }

    @Test
    fun `cross rates divide the two sides`() =
        runTest {
            flat("xau_gram", 100.0)
            flat("xag_gram", 50.0)

            runner.register(listOf(key("xau_gram/xag_gram"), key("xag_gram/try")), now)

            assertEquals(2.0, runner.price(key("xau_gram/xag_gram")))
            assertEquals(State.FLAT, runner.states(key("xau_gram/xag_gram"))?.drop?.state)
            assertEquals(50.0, runner.price(key("xag_gram/try")))
        }

    @Test
    fun `sides in different currencies meet in lira through USD TRY`() =
        runTest {
            flat("btcusdt", 10.0)
            flat("usdtry", 40.0)
            flat("xau_gram", 100.0)

            runner.register(listOf(key("btcusdt/xau_gram")), now)

            assertEquals(4.0, assertNotNull(runner.price(key("btcusdt/xau_gram"))), absoluteTolerance = 1e-12)
            assertEquals(
                listOf("btcusdt", "xau_gram", "usdtry"),
                StateRunner.marketComponents(Target.parse("btcusdt/xau_gram")!!).map { it.id },
            )
            assertEquals(listOf("btcusdt", "ethusdt"), StateRunner.marketComponents(Target.parse("btcusdt/ethusdt")!!).map { it.id })
        }

    @Test
    fun `a cross rate closes with either side`() =
        runTest {
            val saturday = Instant.parse("2026-10-31T12:00:00Z")
            flat("btcusdt", 10.0, end = saturday)
            flat("ethusdt", 5.0, end = saturday)
            flat("usdtry", 40.0, end = saturday)
            flat("xau_gram", 100.0, end = saturday)

            runner.register(listOf(key("btcusdt/ethusdt"), key("btcusdt/xau_gram")), saturday)

            assertEquals(State.FLAT, runner.states(key("btcusdt/ethusdt"))?.drop?.state)
            assertEquals(State.CLOSED, runner.states(key("btcusdt/xau_gram"))?.drop?.state)
        }
}
