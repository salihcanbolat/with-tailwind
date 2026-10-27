package com.damla.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class PriceWindowTest {
    private fun candle(
        index: Int,
        close: Double,
        high: Double = close,
        low: Double = close,
    ) = Candle(start = minute(index), open = close, high = high, low = low, close = close)

    @Test
    fun `window starts with the close of the last candle that ended before it`() {
        val candles = (0..20).map { candle(it, close = 100.0 + it) }

        val window = priceWindow(candles, Interval.FIFTEEN_MINUTES, now = minute(20))

        // Window starts at minute 5; the candle starting at minute 4 ends exactly then.
        assertEquals(PriceWindow(p0 = 104.0, p1 = 120.0, hi = 120.0, lo = 104.0), window)
    }

    @Test
    fun `extremes come from candles overlapping the window`() {
        val candles =
            listOf(
                candle(0, close = 100.0, high = 130.0, low = 70.0),
                candle(1, close = 101.0, high = 103.0, low = 99.0),
                candle(2, close = 102.0, high = 108.0, low = 101.0),
            )

        // The window starts at 1:30. The first candle ended before it and only supplies the base;
        // the second one straddles the start and counts towards the extremes.
        val window = priceWindow(candles, Interval(1), now = minute(2) + 30.seconds)

        assertEquals(PriceWindow(p0 = 100.0, p1 = 102.0, hi = 108.0, lo = 99.0), window)
    }

    @Test
    fun `range includes the base price`() {
        val candles = listOf(candle(0, close = 100.0), candle(1, close = 101.0), candle(2, close = 101.0))

        val window = priceWindow(candles, Interval(1), now = minute(2))

        assertEquals(PriceWindow(p0 = 100.0, p1 = 101.0, hi = 101.0, lo = 100.0), window)
    }

    @Test
    fun `quiet window keeps the base price`() {
        val candles = listOf(candle(0, close = 100.0))

        val window = priceWindow(candles, Interval.FIFTEEN_MINUTES, now = minute(30))

        assertEquals(PriceWindow(p0 = 100.0, p1 = 100.0, hi = 100.0, lo = 100.0), window)
    }

    @Test
    fun `candles after now are ignored`() {
        val candles = listOf(candle(0, close = 100.0), candle(1, close = 101.0), candle(5, close = 150.0))

        val window = priceWindow(candles, Interval(1), now = minute(2))

        assertEquals(PriceWindow(p0 = 100.0, p1 = 101.0, hi = 101.0, lo = 100.0), window)
    }

    @Test
    fun `not enough history gives no window`() {
        val candles = (0..5).map { candle(it, close = 100.0) }

        assertNull(priceWindow(candles, Interval.FIFTEEN_MINUTES, now = minute(5)))
        assertNull(priceWindow(emptyList(), Interval.FIFTEEN_MINUTES, now = minute(5)))
    }
}
