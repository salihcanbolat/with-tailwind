package com.damla.backend.prices

import com.damla.engine.Candle
import com.damla.engine.Interval
import com.damla.engine.PriceWindow
import com.damla.engine.priceWindow
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One-minute candles of one asset held in memory: closed candles for the longest window in use plus the
 * candle of the current minute. Not thread-safe; [MarketData] guards it.
 */
class CandleBook(
    private val retention: Duration,
) {
    private val closed = ArrayList<Candle>()
    private var current: Candle? = null

    var lastPrice: Double? = null
        private set
    var lastUpdate: Instant? = null
        private set

    /** Adds history, e.g. from the database or a backfill. Candles must be closed; duplicates are replaced. */
    fun load(candles: List<Candle>) {
        val merged = (closed + candles).associateBy { it.start }.values.sortedBy { it.start }
        closed.clear()
        closed.addAll(merged)
        val last = closed.lastOrNull() ?: return
        val lastEnd = last.start + Candle.LENGTH
        // Live data received meanwhile is newer than any history.
        if (lastUpdate.let { it == null || lastEnd >= it }) touch(last.close, lastEnd)
    }

    /**
     * Applies an update received at [receivedAt] and returns the candles it closed or corrected, which
     * the caller persists.
     */
    fun apply(
        update: PriceUpdate,
        receivedAt: Instant,
    ): List<Candle> {
        val result =
            when (update) {
                is PriceUpdate.Tick -> applyTick(update.price, update.time)
                is PriceUpdate.CandleState -> applyCandle(update.candle, update.closed)
            }
        return result.also { if (it.applied) touch(it.price, receivedAt) }.finished
    }

    /** Closes the current candle once its minute is over and returns it. */
    fun closeIfDue(now: Instant): List<Candle> {
        val open = current ?: return emptyList()
        if (open.start + Candle.LENGTH > now) return emptyList()
        current = null
        return listOf(appendClosed(open))
    }

    /** Start of the oldest candle kept, or of the open one when none has closed yet. */
    val firstStart: Instant? get() = closed.firstOrNull()?.start ?: current?.start

    /** Closed candles plus the current one, from the last candle that ends at or before [from]. */
    fun candlesSince(from: Instant): List<Candle> {
        val firstEndingAfter = closed.partitionPoint { it.start + Candle.LENGTH <= from }
        val start = (firstEndingAfter - 1).coerceAtLeast(0)
        val tail = closed.subList(start, closed.size)
        return current?.let { tail + it } ?: tail.toList()
    }

    fun window(
        interval: Interval,
        now: Instant,
    ): PriceWindow? = priceWindow(candlesSince(now - interval.duration), interval, now)

    /** Drops closed candles that no window can reach any more. */
    fun trim(now: Instant) {
        val keepFrom = closed.partitionPoint { it.start < now - retention }
        if (keepFrom > 0) closed.subList(0, keepFrom).clear()
    }

    private fun applyTick(
        price: Double,
        time: Instant,
    ): Outcome {
        val minute = minuteOf(time)
        val open = current
        return when {
            open == null || minute > open.start -> {
                val finished = listOfNotNull(open?.let(::appendClosed))
                current = Candle(minute, price, price, price, price)
                Outcome(applied = true, price, finished)
            }

            minute == open.start -> {
                current = open.copy(high = maxOf(open.high, price), low = minOf(open.low, price), close = price)
                Outcome(applied = true, price, emptyList())
            }

            // A tick for a minute that is already closed comes too late to change anything.
            else -> {
                Outcome(applied = false, price, emptyList())
            }
        }
    }

    private fun applyCandle(
        candle: Candle,
        isClosed: Boolean,
    ): Outcome {
        val open = current
        val finished = ArrayList<Candle>()
        when {
            open == null || candle.start >= open.start -> {
                if (open != null && candle.start > open.start) finished += appendClosed(open)
                current = candle
            }

            // A correction of an already closed minute replaces it.
            else -> {
                finished += appendClosed(candle)
                return Outcome(applied = false, candle.close, finished)
            }
        }
        if (isClosed) {
            current = null
            finished += appendClosed(candle)
        }
        return Outcome(applied = true, candle.close, finished)
    }

    private fun appendClosed(candle: Candle): Candle {
        val index = closed.binarySearchBy(candle.start) { it.start }
        if (index >= 0) closed[index] = candle else closed.add(-index - 1, candle)
        return candle
    }

    private fun touch(
        price: Double,
        at: Instant,
    ) {
        lastPrice = price
        lastUpdate = at
    }

    private class Outcome(
        val applied: Boolean,
        val price: Double,
        val finished: List<Candle>,
    )

    private fun minuteOf(time: Instant): Instant = Instant.fromEpochSeconds(time.epochSeconds - Math.floorMod(time.epochSeconds, 60L))

    private inline fun <T> List<T>.partitionPoint(predicate: (T) -> Boolean): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (predicate(this[mid])) low = mid + 1 else high = mid
        }
        return low
    }
}
