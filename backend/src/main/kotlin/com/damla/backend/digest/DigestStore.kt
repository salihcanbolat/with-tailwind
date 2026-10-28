package com.damla.backend.digest

import com.damla.backend.store.Database
import com.damla.engine.State
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * A drop change of one engine group, kept for the evening summary: the drop turned from [from] to [to] at
 * [at] while the rings showed [upperRing] and [lowerRing].
 */
data class Moment(
    val group: String,
    val at: Instant,
    val from: State,
    val to: State,
    val upperRing: State,
    val lowerRing: State,
)

/** The day's drop changes and which devices already got their summary, so a restart sends none twice. */
interface DigestStore {
    suspend fun record(moments: List<Moment>)

    /** Moments of [groups] in [from, to), oldest first. */
    suspend fun moments(
        groups: Collection<String>,
        from: Instant,
        to: Instant,
    ): List<Moment>

    /** Notes that [device] got the summary of [day]; false when it already had. */
    suspend fun markSent(
        device: String,
        day: LocalDate,
        at: Instant,
    ): Boolean

    suspend fun deleteBefore(before: Instant)
}

class InMemoryDigestStore : DigestStore {
    private val mutex = Mutex()
    private val moments = ArrayList<Moment>()
    private val sent = HashMap<Pair<String, LocalDate>, Instant>()

    override suspend fun record(moments: List<Moment>) = mutex.withLock { this.moments += moments }

    override suspend fun moments(
        groups: Collection<String>,
        from: Instant,
        to: Instant,
    ): List<Moment> =
        mutex.withLock {
            val wanted = groups.toSet()
            moments.filter { it.group in wanted && it.at >= from && it.at < to }.sortedBy { it.at }
        }

    override suspend fun markSent(
        device: String,
        day: LocalDate,
        at: Instant,
    ): Boolean = mutex.withLock { sent.putIfAbsent(device to day, at) == null }

    override suspend fun deleteBefore(before: Instant) =
        mutex.withLock {
            moments.removeAll { it.at < before }
            sent.values.removeAll { it < before }
            Unit
        }
}

class PostgresDigestStore(
    private val database: Database,
) : DigestStore {
    override suspend fun record(moments: List<Moment>) {
        if (moments.isEmpty()) return
        database.transaction { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO drop_moments (group_key, at, from_state, to_state, upper_ring, lower_ring) VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    for (moment in moments) {
                        statement.setString(1, moment.group)
                        statement.setTimestamp(2, Timestamp.from(moment.at.toJavaInstant()))
                        statement.setString(3, moment.from.name)
                        statement.setString(4, moment.to.name)
                        statement.setString(5, moment.upperRing.name)
                        statement.setString(6, moment.lowerRing.name)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
        }
    }

    override suspend fun moments(
        groups: Collection<String>,
        from: Instant,
        to: Instant,
    ): List<Moment> {
        if (groups.isEmpty()) return emptyList()
        return database.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT group_key, at, from_state, to_state, upper_ring, lower_ring FROM drop_moments
                    WHERE group_key = ANY (?) AND at >= ? AND at < ? ORDER BY at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setArray(1, connection.createArrayOf("text", groups.toTypedArray()))
                    statement.setTimestamp(2, Timestamp.from(from.toJavaInstant()))
                    statement.setTimestamp(3, Timestamp.from(to.toJavaInstant()))
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    Moment(
                                        group = rows.getString(1),
                                        at = rows.getTimestamp(2).toInstant().toKotlinInstant(),
                                        from = State.valueOf(rows.getString(3)),
                                        to = State.valueOf(rows.getString(4)),
                                        upperRing = State.valueOf(rows.getString(5)),
                                        lowerRing = State.valueOf(rows.getString(6)),
                                    ),
                                )
                            }
                        }
                    }
                }
        }
    }

    override suspend fun markSent(
        device: String,
        day: LocalDate,
        at: Instant,
    ): Boolean =
        database.transaction { connection ->
            connection
                .prepareStatement("INSERT INTO digests_sent (device_id, day, sent_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .use { statement ->
                    statement.setString(1, device)
                    statement.setDate(2, Date.valueOf(day))
                    statement.setTimestamp(3, Timestamp.from(at.toJavaInstant()))
                    statement.executeUpdate() == 1
                }
        }

    override suspend fun deleteBefore(before: Instant) {
        database.transaction { connection ->
            val at = Timestamp.from(before.toJavaInstant())
            connection.prepareStatement("DELETE FROM drop_moments WHERE at < ?").use {
                it.setTimestamp(1, at)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM digests_sent WHERE sent_at < ?").use {
                it.setTimestamp(1, at)
                it.executeUpdate()
            }
        }
    }
}
