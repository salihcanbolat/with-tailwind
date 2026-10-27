package com.damla.backend

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.sources.BinanceSource
import com.damla.backend.sources.BinanceSource.StreamMessage
import com.damla.engine.Candle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

class BinanceSourceTest {
    private val btc = AssetCatalog["btcusdt"]!!
    private val bySymbol = mapOf("BTCUSDT" to btc)

    /** The kline event example of the official docs (web-socket-streams.md), with our symbol. */
    private fun klineMessage(
        start: Long = 1672515780000,
        close: String = "0.0020",
        closed: Boolean = false,
    ) = """
        {"stream":"btcusdt@kline_1m","data":{"e":"kline","E":1672515782136,"s":"BTCUSDT","k":{
          "t":$start,"T":${start + 59_999},"s":"BTCUSDT","i":"1m","f":100,"L":200,
          "o":"0.0010","c":"$close","h":"0.0025","l":"0.0015","v":"1000","n":100,"x":$closed,
          "q":"1.0000","V":"500","Q":"0.500","B":"123456"}}}
        """.trimIndent()

    @Test
    fun `kline events become candle updates`() {
        val message = BinanceSource.parseStreamMessage(klineMessage(), bySymbol)

        val expected =
            PriceUpdate.CandleState(
                asset = "btcusdt",
                source = "binance",
                candle = Candle(Instant.fromEpochMilliseconds(1672515780000), 0.0010, 0.0025, 0.0015, 0.0020),
                closed = false,
            )
        assertEquals(StreamMessage.Kline(expected), message)
    }

    @Test
    fun `server shutdown, unknown symbols and other events are recognised`() {
        assertEquals(
            StreamMessage.Shutdown,
            BinanceSource.parseStreamMessage("""{"stream":"!serverShutdown","data":{"e":"serverShutdown","E":1770123456789}}""", bySymbol),
        )
        assertEquals(StreamMessage.Ignored, BinanceSource.parseStreamMessage(klineMessage(), emptyMap()))
        assertEquals(StreamMessage.Ignored, BinanceSource.parseStreamMessage("""{"stream":"x","data":{"e":"trade"}}""", bySymbol))
        assertEquals(StreamMessage.Ignored, BinanceSource.parseStreamMessage("""{"result":null,"id":1}""", bySymbol))
    }

    @Test
    fun `klines responses are parsed`() {
        // Example response of GET /api/v3/klines in the official docs (rest-api.md).
        val text =
            """[[1499040000000,"0.01634790","0.80000000","0.01575800","0.01577100","148976.11427815",
               1499644799999,"2434.19055334",308,"1756.87402397","28.46694368","0"]]"""

        assertEquals(
            listOf(Candle(Instant.fromEpochMilliseconds(1499040000000), 0.0163479, 0.8, 0.015758, 0.015771)),
            BinanceSource.parseKlines(text),
        )
    }

    @Test
    fun `history pages through klines, keeps closed candles only and backs off when asked`() =
        runTest {
            val from = Instant.fromEpochMilliseconds(1_700_000_000_000 - 1_700_000_000_000 % 60_000)
            val requests = mutableListOf<Url>()
            var rateLimited = false
            val engine =
                MockEngine { request ->
                    requests += request.url
                    if (!rateLimited) {
                        rateLimited = true
                        return@MockEngine respond("", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "5"))
                    }
                    val start = request.url.parameters["startTime"]!!.toLong()
                    val count = if (start == from.toEpochMilliseconds()) 1000 else 3
                    val rows =
                        (0 until count).joinToString(",") { i ->
                            val t = start + i * 60_000L
                            """[$t,"1","2","0.5","1.5","0",${t + 59_999},"0",1,"0","0","0"]"""
                        }
                    respond("[$rows]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val source = BinanceSource(HttpClient(engine), restBase = "https://binance.test")

            val candles = source.history(btc, from, from + 1002.minutes)

            assertEquals(1002, candles.size)
            assertEquals(from, candles.first().start)
            assertEquals(from + 1001.minutes, candles.last().start)
            assertEquals(3, requests.size)
            assertEquals(listOf("BTCUSDT", "1m", "1000"), listOf("symbol", "interval", "limit").map { requests[1].parameters[it] })
        }

    @Test
    fun `the live stream reconnects after a server shutdown notice`() =
        testApplication {
            var connections = 0
            install(ServerWebSockets)
            routing {
                webSocket("/stream") {
                    connections++
                    assertEquals("btcusdt@kline_1m", call.request.queryParameters["streams"])
                    send(Frame.Text(klineMessage(close = "1.0$connections")))
                    send(Frame.Text("""{"stream":"!serverShutdown","data":{"e":"serverShutdown","E":1}}"""))
                }
            }
            val client = createClient { install(ClientWebSockets) }
            val source = BinanceSource(client, streamBase = "ws://localhost")

            val updates = source.updates(listOf(btc)).take(2).toList()

            assertEquals(listOf(1.01, 1.02), updates.map { (it as PriceUpdate.CandleState).candle.close })
            assertEquals(2, connections)
        }
}
