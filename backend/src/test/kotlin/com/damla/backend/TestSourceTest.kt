package com.damla.backend

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.sources.TestSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TestSourceTest {
    private val usd = AssetCatalog["usdtry"]!!
    private val btc = AssetCatalog["btcusdt"]!!

    @Test
    fun `prices are a pure function of time around 100`() {
        val at = WEDNESDAY_NOON

        assertEquals(TestSource.price(usd, at), TestSource.price(usd, at))
        assertTrue(TestSource.price(usd, at) != TestSource.price(btc, at))
        val samples = (0 until 7 * 24 * 60).map { TestSource.price(usd, at + it.minutes) }
        assertTrue(samples.all { it in 80.0..120.0 }, "synthetic FX prices stay near 100")
    }

    @Test
    fun `history consists of closed minute candles that match the live price`() =
        runTest {
            val source = TestSource()
            val to = WEDNESDAY_NOON + 30.seconds

            val candles = source.history(usd, WEDNESDAY_NOON - 10.minutes + 5.seconds, to)

            assertEquals(9, candles.size)
            assertEquals(WEDNESDAY_NOON - 9.minutes, candles.first().start)
            assertEquals(WEDNESDAY_NOON - 1.minutes, candles.last().start)
            val last = candles.last()
            assertEquals(TestSource.price(usd, last.start), last.open)
            assertTrue(last.low <= last.close && last.close <= last.high)
        }

    @Test
    fun `swings are large enough to reach every state`() {
        // Over a week the 15-minute move should cross the FX and crypto stable thresholds (0.10 and 0.50 %).
        for ((asset, threshold) in listOf(usd to 0.10, btc to 0.50)) {
            val moves =
                (0 until 7 * 24 * 4).map { step ->
                    val start = WEDNESDAY_NOON + (step * 15).minutes
                    val p0 = TestSource.price(asset, start)
                    abs(TestSource.price(asset, start + 15.minutes) - p0) / p0 * 100
                }
            assertTrue(moves.any { it >= threshold }, "${asset.id} reaches its stable threshold")
            assertTrue(moves.any { it < threshold / 2 }, "${asset.id} also stays flat at times")
        }
    }

    @Test
    fun `live ticks carry the test source name`() =
        runTest {
            val clock = MutableClock(WEDNESDAY_NOON)

            val tick = TestSource(clock).updates(listOf(usd)).first() as PriceUpdate.Tick

            assertEquals(PriceUpdate.Tick("usdtry", "test", TestSource.price(usd, WEDNESDAY_NOON), WEDNESDAY_NOON), tick)
            assertTrue(TestSource().supports(btc))
        }
}
