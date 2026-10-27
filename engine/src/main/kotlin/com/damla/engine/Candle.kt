package com.damla.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One-minute OHLC candle covering [start] up to (not including) [start] + [LENGTH]. */
public data class Candle(
    val start: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    public companion object {
        public val LENGTH: Duration = 1.minutes
    }
}

/**
 * Builds the window of [interval] ending at [now] from candles sorted by start time.
 *
 * - p0 is the close of the last candle that ended at or before the window start.
 * - p1 is the close of the last candle that started at or before [now].
 * - hi and lo span p0 and every candle overlapping the window.
 *
 * Returns null when no candle ended before the window start, so the history is too short.
 */
public fun priceWindow(
    candles: List<Candle>,
    interval: Interval,
    now: Instant,
): PriceWindow? {
    val windowStart = now - interval.duration
    var base: Double? = null
    var latest = 0.0
    var hi = Double.NEGATIVE_INFINITY
    var lo = Double.POSITIVE_INFINITY
    for (candle in candles) {
        if (candle.start > now) break
        if (candle.start + Candle.LENGTH <= windowStart) {
            base = candle.close
        } else {
            hi = maxOf(hi, candle.high)
            lo = minOf(lo, candle.low)
        }
        latest = candle.close
    }
    val p0 = base ?: return null
    return PriceWindow(p0 = p0, p1 = latest, hi = maxOf(hi, p0), lo = minOf(lo, p0))
}
