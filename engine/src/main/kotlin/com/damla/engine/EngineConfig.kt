package com.damla.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tunable constants of docs/02.
 *
 * @property hysteresis share of a threshold the measure must fall below before a part leaves UP, DOWN or WAVE.
 * @property dropHold how long a new drop state must persist before it is shown and reported.
 * @property ringHold the same for both rings.
 */
public data class EngineConfig(
    val hysteresis: Double = 0.8,
    val dropHold: Duration = 60.seconds,
    val ringHold: Duration = 5.minutes,
) {
    init {
        require(hysteresis > 0.0 && hysteresis <= 1.0) { "Hysteresis must be in (0, 1], was $hysteresis" }
        require(!dropHold.isNegative()) { "Drop hold must not be negative, was $dropHold" }
        require(!ringHold.isNegative()) { "Ring hold must not be negative, was $ringHold" }
    }
}
