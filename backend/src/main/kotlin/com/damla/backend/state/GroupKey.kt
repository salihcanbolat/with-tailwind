package com.damla.backend.state

import com.damla.backend.catalog.IntervalCodec
import com.damla.backend.catalog.Target
import com.damla.engine.AssetConfig
import com.damla.engine.Interval
import com.damla.engine.PartConfig
import com.damla.engine.Thresholds

/**
 * One engine run shared by everyone with the same settings for a target (docs/03: grouped by
 * `asset|interval|s|d`). The three parts are evaluated together, so all three intervals are in the key.
 */
data class GroupKey(
    val target: Target,
    val drop: Interval,
    val upperRing: Interval,
    val lowerRing: Interval,
    val thresholds: Thresholds,
) {
    val id: String =
        listOf(
            target.id,
            IntervalCodec.format(drop),
            IntervalCodec.format(upperRing),
            IntervalCodec.format(lowerRing),
            thresholds.stable.toString(),
            thresholds.wave.toString(),
        ).joinToString("|")

    val config: AssetConfig
        get() =
            AssetConfig(
                asset = target.id,
                drop = PartConfig(drop, thresholds),
                upperRing = PartConfig(upperRing, thresholds),
                lowerRing = PartConfig(lowerRing, thresholds),
            )

    /** The longest look-back of the three parts. */
    val longest: Interval get() = listOf(drop, upperRing, lowerRing).maxBy { it.minutes }

    companion object {
        /** Intervals used when a device does not choose its own (docs/01 examples). */
        val defaultDrop: Interval = Interval.FIFTEEN_MINUTES
        val defaultUpperRing: Interval = Interval.FOUR_HOURS
        val defaultLowerRing: Interval = Interval.ONE_DAY

        fun default(target: Target): GroupKey =
            GroupKey(target, defaultDrop, defaultUpperRing, defaultLowerRing, Thresholds.defaultFor(target.category))
    }
}
