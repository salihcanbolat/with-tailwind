package com.damla.backend

import com.damla.api.AssetSettings
import com.damla.api.DeviceSettings
import com.damla.backend.devices.PostgresDeviceStore
import com.damla.backend.digest.Moment
import com.damla.backend.digest.PostgresDigestStore
import com.damla.backend.state.MemoryCodec
import com.damla.backend.store.Database
import com.damla.backend.store.PostgresCandleStore
import com.damla.backend.store.PostgresStateStore
import com.damla.engine.AssetMemory
import com.damla.engine.Candle
import com.damla.engine.State
import kotlinx.coroutines.test.runTest
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Runs against `DAMLA_TEST_DATABASE_URL` when set (e.g. a local PostgreSQL), otherwise against a
 * Testcontainers PostgreSQL, which needs Docker (as on CI).
 */
class PostgresStoresTest {
    private lateinit var database: Database

    @BeforeTest
    fun connect() {
        database = TestDatabase.open()
        runTest {
            database.transaction {
                it.createStatement().use { s ->
                    s.execute("TRUNCATE candles, group_states, devices, drop_moments, digests_sent")
                }
            }
        }
    }

    @AfterTest
    fun disconnect() = database.close()

    @Test
    fun `candles are upserted, loaded, bucketed and expired`() =
        runTest {
            val store = PostgresCandleStore(database)
            val start = WEDNESDAY_NOON
            val candles = (0 until 120).map { Candle(start + it.minutes, 1.0, 2.0, 0.5, 100.0 + it) }
            store.save("usdtry", candles)
            store.save("usdtry", listOf(candles[0].copy(close = 99.0)))
            store.save("eurtry", listOf(candles[0]))

            val loaded = store.load("usdtry", start, start + 2.hours)
            assertEquals(120, loaded.size)
            assertEquals(99.0, loaded.first().close)
            assertEquals(candles.last(), loaded.last())

            val buckets = store.bucketCloses("usdtry", start, start + 2.hours, 4)
            assertEquals(listOf(start + 30.minutes, start + 60.minutes, start + 90.minutes, start + 120.minutes), buckets.map { it.first })
            assertEquals(listOf(129.0, 159.0, 189.0, 219.0), buckets.map { it.second })

            store.deleteBefore(start + 60.minutes)
            assertEquals(60, store.load("usdtry", start, start + 2.hours).size)
            assertEquals(emptyList(), store.load("eurtry", start, start + 2.hours))
        }

    @Test
    fun `engine memory is stored per group`() =
        runTest {
            val store = PostgresStateStore(database)
            val memory = MemoryCodec.decode(MemoryCodec.encode(AssetMemory(true, null, null, null)))

            store.save(mapOf("usdtry|15m|4h|1d|0.1|0.3" to memory), WEDNESDAY_NOON)
            store.save(mapOf("usdtry|15m|4h|1d|0.1|0.3" to memory.copy(marketOpen = false)), WEDNESDAY_NOON)

            assertEquals(mapOf("usdtry|15m|4h|1d|0.1|0.3" to memory.copy(marketOpen = false)), store.loadAll())
        }

    @Test
    fun `devices keep their settings and tokens can be cleared`() =
        runTest {
            val store = PostgresDeviceStore(database)
            val settings = DeviceSettings("token-1", listOf(AssetSettings("usdtry", drop = "1h")))

            store.save("device-0001", settings, WEDNESDAY_NOON)
            assertEquals(mapOf("device-0001" to settings), store.loadAll())

            store.clearToken("device-0001")
            assertNull(store.loadAll().getValue("device-0001").fcmToken)
        }

    @Test
    fun `summary moments are kept by group and time, and each day is sent once`() =
        runTest {
            val store = PostgresDigestStore(database)
            val usd = Moment("usdtry|15m|4h|1d|0.1|0.3", WEDNESDAY_NOON, State.FLAT, State.UP, State.UP, State.FLAT)
            val gold = usd.copy(group = "xau_gram|15m|4h|1d|0.15|0.4", at = WEDNESDAY_NOON + 1.hours, to = State.WAVE)
            store.record(listOf(gold, usd, usd.copy(at = WEDNESDAY_NOON - 2.hours)))

            assertEquals(
                listOf(usd, gold),
                store.moments(listOf(usd.group, gold.group), WEDNESDAY_NOON - 1.hours, WEDNESDAY_NOON + 2.hours),
            )
            assertEquals(listOf(gold), store.moments(listOf(gold.group), WEDNESDAY_NOON - 3.hours, WEDNESDAY_NOON + 2.hours))
            assertEquals(emptyList(), store.moments(emptyList(), WEDNESDAY_NOON - 3.hours, WEDNESDAY_NOON + 2.hours))

            val day = LocalDate.of(2026, 10, 28)
            assertEquals(true, store.markSent("device-0001", day, WEDNESDAY_NOON))
            assertEquals(false, store.markSent("device-0001", day, WEDNESDAY_NOON + 1.minutes))
            assertEquals(true, store.markSent("device-0001", day.plusDays(1), WEDNESDAY_NOON + 24.hours))

            store.deleteBefore(WEDNESDAY_NOON + 30.minutes)
            assertEquals(listOf(gold), store.moments(listOf(usd.group, gold.group), WEDNESDAY_NOON - 3.hours, WEDNESDAY_NOON + 2.hours))
            assertEquals(true, store.markSent("device-0001", day, WEDNESDAY_NOON + 2.hours))
        }
}

private object TestDatabase {
    private val container: PostgreSQLContainer? by lazy {
        if (System.getenv("DAMLA_TEST_DATABASE_URL") != null) null else PostgreSQLContainer("postgres:17-alpine").also { it.start() }
    }

    fun open(): Database {
        val url = System.getenv("DAMLA_TEST_DATABASE_URL")
        if (url != null) return Database(url, user = null, password = null)
        val running = checkNotNull(container)
        return Database(running.jdbcUrl, running.username, running.password)
    }
}
