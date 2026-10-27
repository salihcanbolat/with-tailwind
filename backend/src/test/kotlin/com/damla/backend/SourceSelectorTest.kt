package com.damla.backend

import com.damla.backend.prices.MarketData
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.prices.SourceSelector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class SourceSelectorTest {
    private val t0 = WEDNESDAY_NOON

    private fun selector() =
        SourceSelector(
            preferences = mapOf("btcusdt" to listOf("primary", "backup")),
            staleAfter = mapOf("primary" to 30.seconds, "backup" to 30.seconds),
        )

    @Test
    fun `the primary source wins while it is fresh`() {
        val selector = selector()
        selector.seen("btcusdt", "backup", t0)

        assertEquals("primary", selector.seen("btcusdt", "primary", t0 + 1.seconds))
        assertEquals("primary", selector.seen("btcusdt", "backup", t0 + 2.seconds))
    }

    @Test
    fun `a silent primary hands over to the backup and takes over again when it recovers`() {
        val selector = selector()
        selector.seen("btcusdt", "primary", t0)

        assertEquals("primary", selector.seen("btcusdt", "backup", t0 + 30.seconds))
        assertEquals("backup", selector.seen("btcusdt", "backup", t0 + 31.seconds))
        assertEquals("primary", selector.seen("btcusdt", "primary", t0 + 40.seconds))
    }

    @Test
    fun `when every source is silent the last choice stays`() {
        val selector = selector()
        selector.seen("btcusdt", "backup", t0)

        assertEquals("backup", selector.select("btcusdt", t0 + 1.days))
        assertNull(selector.select("ethusdt", t0))
    }

    @Test
    fun `market data only applies updates from the active source`() =
        runTest {
            val market = MarketData(listOf("btcusdt"), selector(), retention = 1.days)
            market.accept(PriceUpdate.Tick("btcusdt", "primary", 100.0, t0), t0)

            market.accept(PriceUpdate.Tick("btcusdt", "backup", 90.0, t0 + 1.seconds), t0 + 1.seconds)
            assertEquals(100.0, market.quote("btcusdt")?.price)

            market.accept(PriceUpdate.Tick("btcusdt", "backup", 91.0, t0 + 31.seconds), t0 + 31.seconds)
            assertEquals(MarketData.Quote(91.0, t0 + 31.seconds, "backup"), market.quote("btcusdt"))

            market.accept(PriceUpdate.Tick("unknown", "primary", 1.0, t0), t0)
            assertNull(market.quote("unknown"))
        }
}
