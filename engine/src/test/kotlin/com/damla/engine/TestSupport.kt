package com.damla.engine

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val T0: Instant = Instant.parse("2026-10-27T10:00:00Z")

internal fun at(seconds: Int): Instant = T0 + seconds.seconds

internal fun minute(index: Int): Instant = T0 + index.minutes

internal val FX = Thresholds(stable = 0.10, wave = 0.30)

/**
 * A window whose base price is 100, so net and range equal the absolute price moves.
 * [range] must be at least |[net]|.
 */
internal fun window(
    net: Double,
    range: Double = kotlin.math.abs(net),
): PriceWindow {
    val p1 = 100.0 + net
    val lo = minOf(100.0, p1)
    return PriceWindow(p0 = 100.0, p1 = p1, hi = lo + range, lo = lo)
}

internal fun mirror(state: State): State =
    when (state) {
        State.UP -> State.DOWN
        State.DOWN -> State.UP
        else -> state
    }
