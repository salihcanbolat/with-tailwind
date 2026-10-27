package com.damla.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossRateTest {
    private fun candle(
        index: Int,
        close: Double,
    ) = Candle(start = minute(index), open = close - 1.0, high = close + 1.0, low = close - 2.0, close = close)

    private fun point(
        index: Int,
        price: Double,
    ) = Candle(start = minute(index), open = price, high = price, low = price, close = price)

    @Test
    fun `cross candles are the ratio of closes`() {
        val gold = listOf(candle(0, close = 10.0), candle(1, close = 12.0))
        val silver = listOf(candle(0, close = 2.0), candle(1, close = 3.0))

        assertEquals(listOf(point(0, 5.0), point(1, 4.0)), CrossRate.compose(gold, silver))
    }

    @Test
    fun `a side without a candle in a minute carries its last close forward`() {
        val numerator = listOf(candle(0, close = 10.0), candle(1, close = 20.0), candle(3, close = 30.0))
        val denominator = listOf(candle(0, close = 2.0), candle(2, close = 4.0), candle(3, close = 5.0))

        assertEquals(
            listOf(point(0, 5.0), point(1, 10.0), point(2, 5.0), point(3, 6.0)),
            CrossRate.compose(numerator, denominator),
        )
    }

    @Test
    fun `minutes before both sides have a price are skipped`() {
        val numerator = listOf(candle(2, close = 9.0))
        val denominator = listOf(candle(0, close = 3.0), candle(1, close = 3.0))

        assertEquals(listOf(point(2, 3.0)), CrossRate.compose(numerator, denominator))
        assertEquals(
            listOf(point(1, 2.0), point(2, 3.0)),
            CrossRate.compose(listOf(candle(0, close = 6.0), candle(2, close = 9.0)), listOf(candle(1, close = 3.0))),
        )
        assertEquals(emptyList(), CrossRate.compose(emptyList(), denominator))
    }

    @Test
    fun `the composed series feeds the window like any asset`() {
        val gold = (0..16).map { candle(it, close = 100.0 + it) }
        val silver = (0..16).map { candle(it, close = 2.0) }

        val window = priceWindow(CrossRate.compose(gold, silver), Interval.FIFTEEN_MINUTES, now = minute(16))

        assertEquals(PriceWindow(p0 = 50.0, p1 = 58.0, hi = 58.0, lo = 50.0), window)
    }

    @Test
    fun `cross market is open only when both sides are open`() {
        assertTrue(CrossRate.marketOpen(numeratorOpen = true, denominatorOpen = true))
        assertFalse(CrossRate.marketOpen(numeratorOpen = true, denominatorOpen = false))
        assertFalse(CrossRate.marketOpen(numeratorOpen = false, denominatorOpen = true))
    }

    @Test
    fun `cross pair takes the numerator category and rejects the same asset on both sides`() {
        val gold = AssetRef(id = "xau_gram", category = Category.METAL)
        val bitcoin = AssetRef(id = "btcusdt", category = Category.CRYPTO)

        assertEquals(Category.CRYPTO, CrossPair(numerator = bitcoin, denominator = gold).category)
        assertEquals(Category.METAL, CrossPair(numerator = gold, denominator = bitcoin).category)
        assertFailsWith<IllegalArgumentException> { CrossPair(numerator = gold, denominator = gold) }
    }
}
