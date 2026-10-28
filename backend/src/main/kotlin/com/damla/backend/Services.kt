package com.damla.backend

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.damla.backend.alarms.AlarmRunner
import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.catalog.Target
import com.damla.backend.config.AppConfig
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.devices.DeviceStore
import com.damla.backend.devices.InMemoryDeviceStore
import com.damla.backend.devices.Pairings
import com.damla.backend.devices.PostgresDeviceStore
import com.damla.backend.digest.ClaudeDigestWriter
import com.damla.backend.digest.DigestRunner
import com.damla.backend.digest.DigestStore
import com.damla.backend.digest.DigestWriter
import com.damla.backend.digest.InMemoryDigestStore
import com.damla.backend.digest.PostgresDigestStore
import com.damla.backend.market.MarketCalendar
import com.damla.backend.prices.MarketData
import com.damla.backend.prices.SourceSelector
import com.damla.backend.push.FcmSender
import com.damla.backend.push.LoggingPushSender
import com.damla.backend.push.PushDispatcher
import com.damla.backend.push.PushSender
import com.damla.backend.sources.BinanceSource
import com.damla.backend.sources.Ingestion
import com.damla.backend.sources.PriceSource
import com.damla.backend.sources.TestSource
import com.damla.backend.sources.TruncgilSource
import com.damla.backend.state.GroupKey
import com.damla.backend.state.StateRunner
import com.damla.backend.store.CandleStore
import com.damla.backend.store.Database
import com.damla.backend.store.InMemoryCandleStore
import com.damla.backend.store.InMemoryStateStore
import com.damla.backend.store.PostgresCandleStore
import com.damla.backend.store.PostgresStateStore
import com.damla.backend.store.StateStore
import com.damla.engine.GaugeEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Everything the server runs, wired together. Tests build it with in-memory stores and fake sources. */
class Services(
    val config: AppConfig,
    val clock: Clock,
    val candles: CandleStore,
    states: StateStore,
    deviceStore: DeviceStore,
    sources: List<PriceSource>,
    sender: PushSender,
    val calendar: MarketCalendar = MarketCalendar.bundled(),
    digestStore: DigestStore = InMemoryDigestStore(),
    digestWriter: DigestWriter? = null,
    private val closeables: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(Services::class.java)

    val market =
        MarketData(
            assets = AssetCatalog.assets.map { it.id },
            selector = SourceSelector(Ingestion.preferences(sources), sources.associate { it.name to it.staleAfter }),
            retention = config.memory,
        )
    val runner = StateRunner(GaugeEngine(), market, calendar, states)
    val devices = DeviceRegistry(deviceStore)
    val pairings = Pairings(clock)
    val dispatcher = PushDispatcher(sender, devices)
    val alarms = AlarmRunner(devices, runner, sender)
    val digests = DigestRunner(devices, digestStore, sender, digestWriter)
    val ingestion = Ingestion(sources, market, candles, clock, config.memory)

    /** Restores engine memory, backfills prices and starts the default and device groups. */
    suspend fun prepare() {
        runner.restore()
        ingestion.backfill()
        val defaults = AssetCatalog.assets.map { GroupKey.default(Target.Single(it)) }
        runner.register(defaults + devices.load(), clock.now())
    }

    /** One step of the loop: close finished minutes, evaluate every group, push the events, check alarms. */
    suspend fun tick(now: Instant) {
        ingestion.persist(market.closeDue(now))
        for ((key, events) in runner.evaluateAll(now)) {
            dispatcher.dispatch(key, events)
            digests.record(key, events, runner.states(key))
        }
        alarms.evaluateAll(now)
    }

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            try {
                prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Startup preparation failed; continuing with live data only", e)
            }
            ingestion.start(this)
            // Summaries wait on the writer, so they have a loop of their own.
            launch {
                while (isActive) {
                    try {
                        digests.sendDue(clock.now())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.error("Sending the evening summaries failed", e)
                    }
                    delay(DIGEST_EVERY)
                }
            }
            var lastSave = clock.now()
            var lastCleanup = Instant.DISTANT_PAST
            while (isActive) {
                val now = clock.now()
                try {
                    tick(now)
                    if (now - lastSave >= SAVE_EVERY) {
                        runner.saveAll(now)
                        lastSave = now
                    }
                    if (now - lastCleanup >= 1.days) {
                        candles.deleteBefore(now - config.retention)
                        digests.deleteBefore(now - 2.days)
                        lastCleanup = now
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Evaluation loop step failed", e)
                }
                delay(config.evaluateEvery)
            }
        }

    override fun close() = closeables.forEach { it.close() }

    companion object {
        private val SAVE_EVERY = 30.seconds
        private val DIGEST_EVERY = 30.seconds

        fun create(config: AppConfig): Services {
            val clock = Clock.System
            val client =
                HttpClient(CIO) {
                    install(WebSockets)
                    install(HttpTimeout) { requestTimeoutMillis = 30_000 }
                }
            val database = config.databaseUrl?.let { Database(it, config.databaseUser, config.databasePassword) }
            val sources =
                config.sources.map { name ->
                    when (name) {
                        BinanceSource.NAME -> BinanceSource(client)
                        TruncgilSource.NAME -> TruncgilSource(client)
                        TestSource.NAME -> TestSource(clock)
                        else -> error("Unknown price source '$name' in DAMLA_SOURCES")
                    }
                }
            val sender =
                if (config.fcmProjectId != null && config.fcmCredentialsFile != null) {
                    FcmSender(client, config.fcmProjectId, FcmSender.serviceAccountTokens(config.fcmCredentialsFile))
                } else {
                    LoggingPushSender()
                }
            return Services(
                config = config,
                clock = clock,
                candles = database?.let(::PostgresCandleStore) ?: InMemoryCandleStore(),
                states = database?.let(::PostgresStateStore) ?: InMemoryStateStore(),
                deviceStore = database?.let(::PostgresDeviceStore) ?: InMemoryDeviceStore(),
                sources = sources,
                sender = sender,
                digestStore = database?.let(::PostgresDigestStore) ?: InMemoryDigestStore(),
                digestWriter =
                    config.anthropicApiKey
                        ?.takeIf { config.digestAi }
                        ?.let { ClaudeDigestWriter(AnthropicOkHttpClient.builder().apiKey(it).build()) },
                closeables = listOfNotNull(client, database),
            )
        }
    }
}
