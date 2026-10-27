package com.damla.backend.sources

import com.damla.backend.catalog.AssetInfo
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Binance spot market data (docs/03), through the market-data-only endpoints
 * `data-api.binance.vision` and `data-stream.binance.vision`.
 *
 * Live prices come from the `<symbol>@kline_1m` streams on one combined connection. Binance closes a
 * connection after 24 hours, so it is renewed every 23 hours, and immediately on a `serverShutdown`
 * event or any error (with backoff). History comes from `GET /api/v3/klines`.
 */
class BinanceSource(
    private val client: HttpClient,
    private val restBase: String = "https://data-api.binance.vision",
    private val streamBase: String = "wss://data-stream.binance.vision",
) : PriceSource {
    private val log = LoggerFactory.getLogger(BinanceSource::class.java)

    override val name: String = NAME
    override val staleAfter: Duration = 30.seconds

    override fun updates(assets: List<AssetInfo>): Flow<PriceUpdate> =
        channelFlow {
            val bySymbol = assets.filter(::supports).associateBy { it.symbols.getValue(NAME) }
            if (bySymbol.isEmpty()) return@channelFlow
            val streams = bySymbol.keys.joinToString("/") { "${it.lowercase()}@kline_1m" }
            var backoff = 1.seconds
            while (true) {
                try {
                    withTimeoutOrNull(CONNECTION_LIFETIME) {
                        client.webSocket("$streamBase/stream?streams=$streams") {
                            backoff = 1.seconds
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                when (val message = parseStreamMessage(frame.readText(), bySymbol)) {
                                    is StreamMessage.Kline -> send(message.update)
                                    StreamMessage.Shutdown -> return@webSocket
                                    StreamMessage.Ignored -> Unit
                                }
                            }
                        }
                    }
                    // Renewal, a shutdown notice or a clean close: reconnect after a short pause so a
                    // server that keeps closing cannot push us past the connection limit.
                    delay(1.seconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Binance stream failed, reconnecting in {}", backoff, e)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(1.minutes)
                }
            }
        }

    override suspend fun history(
        asset: AssetInfo,
        from: Instant,
        to: Instant,
    ): List<Candle> {
        val symbol = asset.symbols[NAME] ?: return emptyList()
        val result = ArrayList<Candle>()
        var next = from
        while (next < to) {
            val response = getKlines(symbol, next, to)
            val page = parseKlines(response.bodyAsText())
            val closed = page.filter { it.start >= next && it.start + Candle.LENGTH <= to }
            result += closed
            if (page.size < PAGE_SIZE || closed.isEmpty()) break
            next = closed.last().start + Candle.LENGTH
        }
        return result
    }

    private suspend fun getKlines(
        symbol: String,
        from: Instant,
        to: Instant,
    ): HttpResponse {
        repeat(MAX_ATTEMPTS) {
            val response =
                client.get("$restBase/api/v3/klines") {
                    parameter("symbol", symbol)
                    parameter("interval", "1m")
                    parameter("startTime", from.toEpochMilliseconds())
                    parameter("endTime", to.toEpochMilliseconds() - 1)
                    parameter("limit", PAGE_SIZE)
                }
            when (response.status) {
                HttpStatusCode.OK -> {
                    return response
                }

                // 429 asks us to back off; ignoring it leads to an IP ban (418). Both carry Retry-After.
                HttpStatusCode.TooManyRequests, IM_A_TEAPOT -> {
                    val wait = response.headers["Retry-After"]?.toLongOrNull()?.seconds ?: 1.minutes
                    log.warn("Binance asked to back off for {}", wait)
                    delay(wait)
                }

                else -> {
                    error("Binance klines failed with ${response.status}: ${response.bodyAsText()}")
                }
            }
        }
        error("Binance klines kept rate limiting after $MAX_ATTEMPTS attempts")
    }

    sealed interface StreamMessage {
        data class Kline(
            val update: PriceUpdate.CandleState,
        ) : StreamMessage

        data object Shutdown : StreamMessage

        data object Ignored : StreamMessage
    }

    companion object {
        const val NAME = "binance"
        private const val PAGE_SIZE = 1000
        private const val MAX_ATTEMPTS = 3
        private val CONNECTION_LIFETIME = 23.hours
        private val IM_A_TEAPOT = HttpStatusCode(418, "I'm a teapot")
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses one combined-stream message: `{"stream": ..., "data": <kline event | serverShutdown>}`. */
        fun parseStreamMessage(
            text: String,
            bySymbol: Map<String, AssetInfo>,
        ): StreamMessage {
            val data = json.parseToJsonElement(text).jsonObject["data"] as? JsonObject ?: return StreamMessage.Ignored
            return when (data["e"]?.jsonPrimitive?.content) {
                "serverShutdown" -> {
                    StreamMessage.Shutdown
                }

                "kline" -> {
                    val k = data.getValue("k").jsonObject
                    val asset = bySymbol[k.getValue("s").jsonPrimitive.content] ?: return StreamMessage.Ignored
                    val candle =
                        Candle(
                            start = Instant.fromEpochMilliseconds(k.getValue("t").jsonPrimitive.long),
                            open = k.decimal("o"),
                            high = k.decimal("h"),
                            low = k.decimal("l"),
                            close = k.decimal("c"),
                        )
                    StreamMessage.Kline(PriceUpdate.CandleState(asset.id, NAME, candle, closed = k.getValue("x").jsonPrimitive.boolean))
                }

                else -> {
                    StreamMessage.Ignored
                }
            }
        }

        /** Parses a `GET /api/v3/klines` response: arrays of open time, open, high, low, close, ... */
        fun parseKlines(text: String): List<Candle> =
            json.parseToJsonElement(text).jsonArray.map { element ->
                val row = element as JsonArray
                Candle(
                    start = Instant.fromEpochMilliseconds(row[0].jsonPrimitive.long),
                    open = row[1].jsonPrimitive.content.toDouble(),
                    high = row[2].jsonPrimitive.content.toDouble(),
                    low = row[3].jsonPrimitive.content.toDouble(),
                    close = row[4].jsonPrimitive.content.toDouble(),
                )
            }

        private fun JsonObject.decimal(key: String): Double = getValue(key).jsonPrimitive.content.toDouble()
    }
}
