package com.damla.backend

import com.damla.backend.prices.CandleBook
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import com.damla.engine.Interval
import com.damla.engine.PriceWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CandleBookTest {
    private val t0 = WEDNESDAY_NOON
    private val book = CandleBook(retention = 1.days)

    private fun tick(
        price: Double,
        secondsAfter: Int,
    ) = book.apply(PriceUpdate.Tick("usdtry", "test", price, t0 + secondsAfter.seconds), receivedAt = t0 + secondsAfter.seconds)

    @Test
    fun `ticks build one-minute candles and close them when the next minute starts`() {
        assertEquals(emptyList(), tick(10.0, 0))
        tick(12.0, 20)
        tick(9.0, 40)
        tick(11.0, 59)

        val closed = tick(11.5, 61)

        assertEquals(listOf(Candle(t0, 10.0, 12.0, 9.0, 11.0)), closed)
        assertEquals(11.5, book.lastPrice)
        assertEquals(t0 + 61.seconds, book.lastUpdate)
        assertEquals(listOf(Candle(t0, 10.0, 12.0, 9.0, 11.0), Candle(t0 + 1.minutes, 11.5, 11.5, 11.5, 11.5)), book.candlesSince(t0))
    }

    @Test
    fun `late ticks for a closed minute are ignored`() {
        tick(10.0, 0)
        tick(11.0, 61)

        assertEquals(emptyList(), tick(50.0, 30))
        assertEquals(11.0, book.lastPrice)
    }

    @Test
    fun `candle updates replace the current candle and close it when told`() {
        val first = Candle(t0, 10.0, 11.0, 9.0, 10.5)
        book.apply(PriceUpdate.CandleState("btcusdt", "binance", first, closed = false), t0)
        val updated = first.copy(close = 10.8, high = 11.2)

        val closed = book.apply(PriceUpdate.CandleState("btcusdt", "binance", updated, closed = true), t0 + 59.seconds)

        assertEquals(listOf(updated), closed)
        assertEquals(10.8, book.lastPrice)
        assertEquals(emptyList(), book.closeIfDue(t0 + 2.minutes))
    }

    @Test
    fun `a newer candle closes the current one`() {
        val first = Candle(t0, 10.0, 10.0, 10.0, 10.0)
        val second = Candle(t0 + 1.minutes, 11.0, 11.0, 11.0, 11.0)
        book.apply(PriceUpdate.CandleState("btcusdt", "binance", first, closed = false), t0)

        assertEquals(listOf(first), book.apply(PriceUpdate.CandleState("btcusdt", "binance", second, closed = false), t0 + 61.seconds))
    }

    @Test
    fun `a correction of a closed minute replaces it without moving the latest price`() {
        val first = Candle(t0, 10.0, 10.0, 10.0, 10.0)
        val second = Candle(t0 + 1.minutes, 11.0, 11.0, 11.0, 11.0)
        book.apply(PriceUpdate.CandleState("btcusdt", "binance", first, closed = false), t0)
        book.apply(PriceUpdate.CandleState("btcusdt", "binance", second, closed = false), t0 + 61.seconds)
        val corrected = first.copy(close = 10.4)

        val persisted = book.apply(PriceUpdate.CandleState("btcusdt", "binance", corrected, closed = true), t0 + 62.seconds)

        assertEquals(listOf(corrected), persisted)
        assertEquals(11.0, book.lastPrice)
        assertEquals(listOf(corrected, second), book.candlesSince(t0))
    }

    @Test
    fun `the current minute is closed once it is over`() {
        tick(10.0, 5)

        assertEquals(emptyList(), book.closeIfDue(t0 + 59.seconds))
        assertEquals(listOf(Candle(t0, 10.0, 10.0, 10.0, 10.0)), book.closeIfDue(t0 + 60.seconds))
        assertEquals(emptyList(), book.closeIfDue(t0 + 61.seconds))
    }

    @Test
    fun `windows include the base candle before the window and the current minute`() {
        book.load(flatCandles(t0, count = 20, price = 100.0))
        tick(100.2, 10)

        val window = book.window(Interval.FIFTEEN_MINUTES, now = t0 + 10.seconds)

        assertEquals(PriceWindow(p0 = 100.0, p1 = 100.2, hi = 100.2, lo = 100.0), window)
    }

    @Test
    fun `loading history sets the latest price and merges duplicates`() {
        book.load(flatCandles(t0, count = 3, price = 50.0))
        book.load(listOf(Candle(t0 - 1.minutes, 51.0, 51.0, 51.0, 51.0)))

        assertEquals(51.0, book.lastPrice)
        assertEquals(t0, book.lastUpdate)
        assertEquals(listOf(50.0, 50.0, 51.0), book.candlesSince(t0 - 1.days).map { it.close })
    }

    @Test
    fun `old candles are trimmed`() {
        book.load(flatCandles(t0, count = 10, price = 1.0))

        book.trim(now = t0 + 1.days - 5.minutes)

        assertEquals(5, book.candlesSince(t0 - 2.days).size)
        assertNull(CandleBook(1.days).lastPrice)
    }
}
