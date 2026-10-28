package com.damla.backend.prices

import com.damla.engine.Candle
import com.damla.engine.Interval
import com.damla.engine.PriceWindow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Instant

/** Latest prices and in-memory candles of every listed asset, fed by the active source per asset. */
class MarketData(
    assets: Collection<String>,
    private val selector: SourceSelector,
    retention: Duration,
) {
    private val log = LoggerFactory.getLogger(MarketData::class.java)
    private val mutex = Mutex()
    private val books = assets.associateWith { CandleBook(retention) }

    /** Applies an update if it comes from the asset's active source and returns candles to persist. */
    suspend fun accept(
        update: PriceUpdate,
        now: Instant,
    ): List<Pair<String, Candle>> =
        mutex.withLock {
            val book = books[update.asset] ?: return@withLock emptyList()
            val before = selector.active(update.asset)
            val active = selector.seen(update.asset, update.source, now)
            if (active != before && before != null) {
                log.warn("Source for {} switched from {} to {}", update.asset, before, active)
            }
            if (active != update.source) return@withLock emptyList()
            book.apply(update, now).map { update.asset to it }
        }

    suspend fun load(
        asset: String,
        candles: List<Candle>,
    ) = mutex.withLock { books[asset]?.load(candles) }

    /** Closes finished minutes, trims old candles and returns the closed candles to persist. */
    suspend fun closeDue(now: Instant): List<Pair<String, Candle>> =
        mutex.withLock {
            books.flatMap { (asset, book) ->
                book.trim(now)
                book.closeIfDue(now).map { asset to it }
            }
        }

    suspend fun window(
        asset: String,
        interval: Interval,
        now: Instant,
    ): PriceWindow? = mutex.withLock { books[asset]?.window(interval, now) }

    suspend fun candlesSince(
        asset: String,
        from: Instant,
    ): List<Candle> = mutex.withLock { books[asset]?.candlesSince(from).orEmpty() }

    suspend fun firstCandle(asset: String): Instant? = mutex.withLock { books[asset]?.firstStart }

    suspend fun quote(asset: String): Quote? =
        mutex.withLock {
            val book = books[asset] ?: return@withLock null
            val price = book.lastPrice ?: return@withLock null
            val updatedAt = book.lastUpdate ?: return@withLock null
            Quote(price, updatedAt, selector.active(asset))
        }

    data class Quote(
        val price: Double,
        val updatedAt: Instant,
        val source: String?,
    )
}
