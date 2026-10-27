package com.damla.backend.sources

import com.damla.backend.catalog.AssetInfo
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import com.damla.engine.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Synthetic prices for local development and tests. NOT market data.
 *
 * Every asset starts around 100 and moves along a fixed sum of waves (minutes to weeks) plus a little
 * noise, so the drop and both rings go through every state. The price is a pure function of time,
 * which keeps history and live ticks continuous across restarts. Snapshots show the source as `test`.
 */
class TestSource(
    private val clock: Clock = Clock.System,
    private val tickEvery: Duration = 1.seconds,
) : PriceSource {
    override val name: String = NAME
    override val staleAfter: Duration = 10.seconds

    override fun supports(asset: AssetInfo): Boolean = true

    override fun updates(assets: List<AssetInfo>): Flow<PriceUpdate> =
        flow {
            while (true) {
                val now = clock.now()
                for (asset in assets) emit(PriceUpdate.Tick(asset.id, NAME, price(asset, now), now))
                delay(tickEvery)
            }
        }

    override suspend fun history(
        asset: AssetInfo,
        from: Instant,
        to: Instant,
    ): List<Candle> {
        val first = from.epochSeconds.ceilTo(60)
        return (first until to.epochSeconds - 59 step 60).map { start ->
            val samples = (0 until 60 step SAMPLE_SECONDS).map { price(asset, Instant.fromEpochSeconds(start + it)) }
            Candle(Instant.fromEpochSeconds(start), samples.first(), samples.max(), samples.min(), samples.last())
        }
    }

    companion object {
        const val NAME = "test"
        private const val SAMPLE_SECONDS = 10
        private const val BASE_PRICE = 100.0
        private val periodsMinutes = doubleArrayOf(7.0, 23.0, 126.0, 540.0, 2_448.0, 8_640.0, 27_360.0)

        /** Relative size of the shortest waves; larger categories swing more, like their thresholds. */
        private fun scale(category: Category) =
            when (category) {
                Category.FX -> 0.0007
                Category.METAL -> 0.001
                Category.CRYPTO -> 0.0035
            }

        fun price(
            asset: AssetInfo,
            at: Instant,
        ): Double {
            val seed = asset.id.hashCode()
            val minutes = at.epochSeconds / 60.0
            val base = scale(asset.category)
            var exponent = 0.0
            for ((index, period) in periodsMinutes.withIndex()) {
                // Longer waves are larger, but grow slower than a random walk so that daily and weekly
                // moves stay in a plausible range.
                val amplitude = base * (period / 15.0).pow(0.4)
                val phase = ((seed * 31 + index * 17) % 360) / 360.0 * 2 * PI
                exponent += amplitude * sin(2 * PI * minutes / period + phase)
            }
            exponent += base / 4 * noise(seed, at.epochSeconds)
            return BASE_PRICE * exp(exponent)
        }

        /** Deterministic noise in [-1, 1] for one second. */
        private fun noise(
            seed: Int,
            second: Long,
        ): Double {
            var x = second * 6364136223846793005L + seed
            x = x xor (x ushr 33)
            x *= -0xae502812aa7333L
            x = x xor (x ushr 33)
            return (x ushr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
        }

        private fun Long.ceilTo(step: Long): Long = Math.floorDiv(this + step - 1, step) * step
    }
}
