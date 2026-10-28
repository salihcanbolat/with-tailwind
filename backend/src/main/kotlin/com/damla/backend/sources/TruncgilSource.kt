package com.damla.backend.sources

import com.damla.backend.catalog.AssetInfo
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Trunçgil Finans (docs/03): free-market Turkish lira prices of currencies, gold and silver from
 * `finans.truncgil.com/v4/today.json`, a file the service rewrites once a minute. As in the samples of
 * 2026-10-28 (`samples/truncgil-1` branch), `Update_Date` is Istanbul time and each entry has numeric
 * `Buying` and `Selling` prices.
 *
 * The file is polled every [pollEvery] with its ETag, so an unchanged file costs a 304. Each new file becomes
 * one tick per asset at its `Update_Date`, at the selling price ("satış", the price Turkish apps quote). A
 * file that stops changing yields nothing, so the selector moves to a backup after [staleAfter]. Missing or
 * zero prices are skipped (the ounce is 0 in every sample). The service keeps no history.
 */
class TruncgilSource(
    private val client: HttpClient,
    private val url: String = "https://finans.truncgil.com/v4/today.json",
    private val pollEvery: Duration = 20.seconds,
) : PriceSource {
    private val log = LoggerFactory.getLogger(TruncgilSource::class.java)

    override val name: String = NAME

    /** Three missed updates of a file written once a minute. */
    override val staleAfter: Duration = 3.minutes

    override fun updates(assets: List<AssetInfo>): Flow<PriceUpdate> =
        flow {
            val byKey = assets.filter(::supports).associateBy { it.symbols.getValue(NAME) }
            if (byKey.isEmpty()) return@flow
            var etag: String? = null
            var latest: Instant? = null
            while (true) {
                try {
                    val response = client.get(url) { etag?.let { header(HttpHeaders.IfNoneMatch, it) } }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            etag = response.headers[HttpHeaders.ETag]
                            val file = parse(response.bodyAsText(), byKey)
                            if (latest == null || file.updated > latest) {
                                latest = file.updated
                                file.ticks.forEach { emit(it) }
                            }
                        }

                        HttpStatusCode.NotModified -> {
                            Unit
                        }

                        else -> {
                            log.warn("Trunçgil answered {}", response.status)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Trunçgil request failed", e)
                }
                delay(pollEvery)
            }
        }

    override suspend fun history(
        asset: AssetInfo,
        from: Instant,
        to: Instant,
    ): List<Candle> = emptyList()

    /** One version of the file: when it was written and the prices of the assets asked for. */
    data class File(
        val updated: Instant,
        val ticks: List<PriceUpdate.Tick>,
    )

    companion object {
        const val NAME = "truncgil"
        private val ISTANBUL = ZoneId.of("Europe/Istanbul")
        private val UPDATE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val json = Json

        /** Parses `today.json`; [byKey] maps its keys ("USD", "GRA", ...) to the assets wanted. */
        fun parse(
            text: String,
            byKey: Map<String, AssetInfo>,
        ): File {
            val root = json.parseToJsonElement(text).jsonObject
            val local = LocalDateTime.parse(root.getValue("Update_Date").jsonPrimitive.content, UPDATE_DATE)
            val updated = Instant.fromEpochSeconds(local.atZone(ISTANBUL).toEpochSecond())
            val ticks =
                byKey.mapNotNull { (key, asset) ->
                    val price = (root[key] as? JsonObject)?.get("Selling")?.jsonPrimitive?.doubleOrNull
                    price?.takeIf { it > 0 && it.isFinite() }?.let { PriceUpdate.Tick(asset.id, NAME, it, updated) }
                }
            return File(updated, ticks)
        }
    }
}
