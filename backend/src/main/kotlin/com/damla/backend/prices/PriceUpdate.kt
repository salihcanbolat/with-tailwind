package com.damla.backend.prices

import com.damla.engine.Candle
import kotlin.time.Instant

/** A price observation from one source for one listed asset. */
sealed interface PriceUpdate {
    val asset: String
    val source: String

    /** A single price at [time], for sources that only report the latest price. */
    data class Tick(
        override val asset: String,
        override val source: String,
        val price: Double,
        val time: Instant,
    ) : PriceUpdate

    /** The current state of a one-minute candle, for sources that build candles themselves. */
    data class CandleState(
        override val asset: String,
        override val source: String,
        val candle: Candle,
        val closed: Boolean,
    ) : PriceUpdate
}
