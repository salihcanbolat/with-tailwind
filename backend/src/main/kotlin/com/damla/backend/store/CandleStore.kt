package com.damla.backend.store

import com.damla.engine.Candle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.Timestamp
import java.util.TreeMap
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/** Long-term storage of one-minute candles. */
interface CandleStore {
    /** Inserts or replaces candles by asset and start time. */
    suspend fun save(
        asset: String,
        candles: List<Candle>,
    )

    /** Candles starting in [from, to), oldest first. */
    suspend fun load(
        asset: String,
        from: Instant,
        to: Instant,
    ): List<Candle>

    /**
     * Splits [from, to) into [buckets] equal slices and returns, for each slice with data, its end time
     * and the close of its last candle. Used for history charts.
     */
    suspend fun bucketCloses(
        asset: String,
        from: Instant,
        to: Instant,
        buckets: Int,
    ): List<Pair<Instant, Double>>

    /** Deletes candles that started before [before]. */
    suspend fun deleteBefore(before: Instant)
}

/** For tests and for running without a database. */
class InMemoryCandleStore : CandleStore {
    private val mutex = Mutex()
    private val data = HashMap<String, TreeMap<Instant, Candle>>()

    override suspend fun save(
        asset: String,
        candles: List<Candle>,
    ) = mutex.withLock {
        val series = data.getOrPut(asset) { TreeMap() }
        candles.forEach { series[it.start] = it }
    }

    override suspend fun load(
        asset: String,
        from: Instant,
        to: Instant,
    ): List<Candle> =
        mutex.withLock {
            data[asset]
                ?.subMap(from, to)
                ?.values
                ?.toList()
                .orEmpty()
        }

    override suspend fun bucketCloses(
        asset: String,
        from: Instant,
        to: Instant,
        buckets: Int,
    ): List<Pair<Instant, Double>> {
        val stepMillis = (to - from).inWholeMilliseconds / buckets
        return load(asset, from, to)
            .groupBy { minOf((it.start - from).inWholeMilliseconds / stepMillis, buckets - 1L) }
            .toSortedMap()
            .map { (bucket, candles) ->
                Instant.fromEpochMilliseconds(from.toEpochMilliseconds() + stepMillis * (bucket + 1)) to candles.last().close
            }
    }

    override suspend fun deleteBefore(before: Instant) =
        mutex.withLock {
            data.values.forEach { it.headMap(before).clear() }
        }
}

class PostgresCandleStore(
    private val database: Database,
) : CandleStore {
    override suspend fun save(
        asset: String,
        candles: List<Candle>,
    ) {
        if (candles.isEmpty()) return
        database.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO candles (asset, start, open, high, low, close) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (asset, start) DO UPDATE
                    SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close
                    """.trimIndent(),
                ).use { statement ->
                    for (candle in candles) {
                        statement.setString(1, asset)
                        statement.setTimestamp(2, Timestamp.from(candle.start.toJavaInstant()))
                        statement.setDouble(3, candle.open)
                        statement.setDouble(4, candle.high)
                        statement.setDouble(5, candle.low)
                        statement.setDouble(6, candle.close)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
        }
    }

    override suspend fun load(
        asset: String,
        from: Instant,
        to: Instant,
    ): List<Candle> =
        database.transaction { connection ->
            connection
                .prepareStatement(
                    "SELECT start, open, high, low, close FROM candles WHERE asset = ? AND start >= ? AND start < ? ORDER BY start",
                ).use { statement ->
                    statement.setString(1, asset)
                    statement.setTimestamp(2, Timestamp.from(from.toJavaInstant()))
                    statement.setTimestamp(3, Timestamp.from(to.toJavaInstant()))
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    Candle(
                                        start = rows.getTimestamp(1).toInstant().toKotlinInstant(),
                                        open = rows.getDouble(2),
                                        high = rows.getDouble(3),
                                        low = rows.getDouble(4),
                                        close = rows.getDouble(5),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    override suspend fun bucketCloses(
        asset: String,
        from: Instant,
        to: Instant,
        buckets: Int,
    ): List<Pair<Instant, Double>> {
        val stepMillis = (to - from).inWholeMilliseconds / buckets
        return database.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT DISTINCT ON (bucket) bucket, close FROM (
                        SELECT least(floor((extract(epoch FROM start) * 1000 - ?) / ?)::bigint, ?) AS bucket, start, close
                        FROM candles WHERE asset = ? AND start >= ? AND start < ?
                    ) slices
                    ORDER BY bucket, start DESC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, from.toEpochMilliseconds())
                    statement.setLong(2, stepMillis)
                    statement.setLong(3, buckets - 1L)
                    statement.setString(4, asset)
                    statement.setTimestamp(5, Timestamp.from(from.toJavaInstant()))
                    statement.setTimestamp(6, Timestamp.from(to.toJavaInstant()))
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val end = from.toEpochMilliseconds() + stepMillis * (rows.getLong(1) + 1)
                                add(Instant.fromEpochMilliseconds(end) to rows.getDouble(2))
                            }
                        }
                    }
                }
        }
    }

    override suspend fun deleteBefore(before: Instant) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM candles WHERE start < ?").use { statement ->
                statement.setTimestamp(1, Timestamp.from(before.toJavaInstant()))
                statement.executeUpdate()
            }
        }
    }
}
