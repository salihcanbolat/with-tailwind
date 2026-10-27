package com.damla.engine

/** Absorbs floating point error, so that e.g. (100.1 - 100) / 100 * 100 still reaches 0.10. */
private const val EPSILON = 1e-9

/**
 * Decision order of docs/02 for an open market, with hysteresis.
 *
 * Entering a state needs the full threshold. A part already in UP, DOWN or WAVE ([previous]) keeps it
 * until its measure falls below [hysteresis] times the threshold. UP and DOWN take priority over WAVE.
 */
internal fun classifyOpen(
    net: Double,
    range: Double,
    thresholds: Thresholds,
    previous: State?,
    hysteresis: Double,
): State {
    val s = thresholds.stable
    val d = thresholds.wave
    return when {
        net >= s - EPSILON -> State.UP
        net <= -s + EPSILON -> State.DOWN
        previous == State.UP && net >= hysteresis * s - EPSILON -> State.UP
        previous == State.DOWN && net <= -hysteresis * s + EPSILON -> State.DOWN
        range >= d - EPSILON -> State.WAVE
        previous == State.WAVE && range >= hysteresis * d - EPSILON -> State.WAVE
        else -> State.FLAT
    }
}
