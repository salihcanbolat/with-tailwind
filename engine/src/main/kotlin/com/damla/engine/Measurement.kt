package com.damla.engine

/**
 * Prices of one part's look-back window (docs/02).
 *
 * @property p0 last price at or before the window start.
 * @property p1 latest price.
 * @property hi highest price in the window.
 * @property lo lowest price in the window.
 */
public data class PriceWindow(
    val p0: Double,
    val p1: Double,
    val hi: Double,
    val lo: Double,
)

public sealed interface Measurement {
    /** [net] is signed and [range] unsigned, both in percent of p0. */
    public data class Valid(
        val net: Double,
        val range: Double,
    ) : Measurement

    /** No window, or one that cannot be measured (non-positive or non-finite prices, hi below lo). */
    public data object Insufficient : Measurement
}

public fun measure(window: PriceWindow?): Measurement {
    if (window == null) return Measurement.Insufficient
    val (p0, p1, hi, lo) = window
    val prices = listOf(p0, p1, hi, lo)
    if (prices.any { !it.isFinite() || it <= 0.0 } || hi < lo) return Measurement.Insufficient
    return Measurement.Valid(
        net = (p1 - p0) / p0 * 100,
        range = (hi - lo) / p0 * 100,
    )
}
