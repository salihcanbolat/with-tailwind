package com.damla.backend.store

import com.damla.backend.state.MemoryCodec
import com.damla.engine.AssetMemory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.Timestamp
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Engine memory per group key, so a restart neither forgets pending changes nor invents new ones. */
interface StateStore {
    suspend fun loadAll(): Map<String, AssetMemory>

    suspend fun save(
        memories: Map<String, AssetMemory>,
        at: Instant,
    )
}

class InMemoryStateStore : StateStore {
    private val mutex = Mutex()
    private val data = HashMap<String, AssetMemory>()

    override suspend fun loadAll(): Map<String, AssetMemory> = mutex.withLock { data.toMap() }

    override suspend fun save(
        memories: Map<String, AssetMemory>,
        at: Instant,
    ) = mutex.withLock { data.putAll(memories) }
}

class PostgresStateStore(
    private val database: Database,
) : StateStore {
    override suspend fun loadAll(): Map<String, AssetMemory> =
        database.transaction { connection ->
            connection.prepareStatement("SELECT key, memory::text FROM group_states").use { statement ->
                statement.executeQuery().use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), MemoryCodec.decode(rows.getString(2))) }
                }
            }
        }

    override suspend fun save(
        memories: Map<String, AssetMemory>,
        at: Instant,
    ) {
        if (memories.isEmpty()) return
        database.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO group_states (key, memory, updated_at) VALUES (?, ?::jsonb, ?)
                    ON CONFLICT (key) DO UPDATE SET memory = EXCLUDED.memory, updated_at = EXCLUDED.updated_at
                    """.trimIndent(),
                ).use { statement ->
                    for ((key, memory) in memories) {
                        statement.setString(1, key)
                        statement.setString(2, MemoryCodec.encode(memory))
                        statement.setTimestamp(3, Timestamp.from(at.toJavaInstant()))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
        }
    }
}
