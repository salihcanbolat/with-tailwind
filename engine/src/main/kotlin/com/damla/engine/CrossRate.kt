package com.damla.engine

import kotlin.time.Instant

public data class AssetRef(
    val id: String,
    val category: Category,
)

/** A user-defined numerator ÷ denominator rate (docs/01). */
public data class CrossPair(
    val numerator: AssetRef,
    val denominator: AssetRef,
) {
    init {
        require(numerator.id != denominator.id) { "The same asset cannot be on both sides: ${numerator.id}" }
    }

    /** Cross rates use the numerator's category, and so its default thresholds (docs/02). */
    public val category: Category get() = numerator.category
}

public object CrossRate {
    public fun marketOpen(
        numeratorOpen: Boolean,
        denominatorOpen: Boolean,
    ): Boolean = numeratorOpen && denominatorOpen

    /**
     * Composes the cross rate at the one-minute level (docs/03).
     *
     * Each output candle is the ratio of the two closes in that minute, with open, high and low equal to
     * it, so a window's extremes come from the ratio of closes. A side with no candle in a minute carries
     * its last close forward. Minutes before both sides have a price are skipped. Inputs must be sorted
     * by start time.
     */
    public fun compose(
        numerator: List<Candle>,
        denominator: List<Candle>,
    ): List<Candle> {
        val result = ArrayList<Candle>()
        var i = 0
        var j = 0
        var numeratorClose: Double? = null
        var denominatorClose: Double? = null
        while (i < numerator.size || j < denominator.size) {
            val nextNumerator = if (i < numerator.size) numerator[i].start else Instant.DISTANT_FUTURE
            val nextDenominator = if (j < denominator.size) denominator[j].start else Instant.DISTANT_FUTURE
            val minute = minOf(nextNumerator, nextDenominator)
            if (nextNumerator == minute) numeratorClose = numerator[i++].close
            if (nextDenominator == minute) denominatorClose = denominator[j++].close
            val top = numeratorClose ?: continue
            val bottom = denominatorClose ?: continue
            val ratio = top / bottom
            result += Candle(start = minute, open = ratio, high = ratio, low = ratio, close = ratio)
        }
        return result
    }
}
