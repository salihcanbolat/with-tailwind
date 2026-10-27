package com.damla.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Length of a part's look-back window, in whole minutes. The user picks one per part (docs/01). */
public data class Interval(
    val minutes: Int,
) {
    init {
        require(minutes > 0) { "Interval must be positive, was $minutes minutes" }
    }

    public val duration: Duration get() = minutes.minutes

    public companion object {
        public val FIFTEEN_MINUTES: Interval = Interval(15)
        public val ONE_HOUR: Interval = Interval(60)
        public val FOUR_HOURS: Interval = Interval(240)
        public val ONE_DAY: Interval = Interval(1_440)
        public val ONE_WEEK: Interval = Interval(10_080)

        /** "1 ay" is a rolling 30-day window. */
        public val ONE_MONTH: Interval = Interval(43_200)
    }
}
