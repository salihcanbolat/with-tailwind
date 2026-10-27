package com.damla.backend.devices

import com.damla.api.DeviceSettings
import com.damla.backend.store.Database
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.sql.Timestamp
import kotlin.time.Instant
import kotlin.time.toJavaInstant

interface DeviceStore {
    suspend fun loadAll(): Map<String, DeviceSettings>

    suspend fun save(
        id: String,
        settings: DeviceSettings,
        at: Instant,
    )

    suspend fun clearToken(id: String)
}

class InMemoryDeviceStore : DeviceStore {
    private val mutex = Mutex()
    private val data = HashMap<String, DeviceSettings>()

    override suspend fun loadAll(): Map<String, DeviceSettings> = mutex.withLock { data.toMap() }

    override suspend fun save(
        id: String,
        settings: DeviceSettings,
        at: Instant,
    ) = mutex.withLock { data[id] = settings }

    override suspend fun clearToken(id: String) =
        mutex.withLock {
            data.computeIfPresent(id) { _, settings -> settings.copy(fcmToken = null) }
            Unit
        }
}

class PostgresDeviceStore(
    private val database: Database,
) : DeviceStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadAll(): Map<String, DeviceSettings> =
        database.transaction { connection ->
            connection.prepareStatement("SELECT id, fcm_token, config::text FROM devices").use { statement ->
                statement.executeQuery().use { rows ->
                    buildMap {
                        while (rows.next()) {
                            val settings = json.decodeFromString<DeviceSettings>(rows.getString(3))
                            put(rows.getString(1), settings.copy(fcmToken = rows.getString(2)))
                        }
                    }
                }
            }
        }

    override suspend fun save(
        id: String,
        settings: DeviceSettings,
        at: Instant,
    ) {
        database.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO devices (id, fcm_token, config, updated_at) VALUES (?, ?, ?::jsonb, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET fcm_token = EXCLUDED.fcm_token, config = EXCLUDED.config, updated_at = EXCLUDED.updated_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, settings.fcmToken)
                    statement.setString(3, json.encodeToString(settings.copy(fcmToken = null)))
                    statement.setTimestamp(4, Timestamp.from(at.toJavaInstant()))
                    statement.executeUpdate()
                }
        }
    }

    override suspend fun clearToken(id: String) {
        database.transaction { connection ->
            connection.prepareStatement("UPDATE devices SET fcm_token = NULL WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeUpdate()
            }
        }
    }
}
