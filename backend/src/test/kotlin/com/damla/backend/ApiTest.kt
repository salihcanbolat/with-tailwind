package com.damla.backend

import com.damla.backend.api.AssetsResponse
import com.damla.backend.api.ErrorResponse
import com.damla.backend.api.HistoryResponse
import com.damla.backend.api.SnapshotResponse
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val clock = MutableClock(WEDNESDAY_NOON)
    private val sender = RecordingSender()
    private val services = testServices(clock, sender)

    private fun api(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { damla(services) }
            block(client)
        }

    private suspend inline fun <reified T> HttpResponse.decode(): T = json.decodeFromString(bodyAsText())

    private suspend fun seed(
        asset: String,
        price: Double,
    ) = services.market.load(asset, flatCandles(WEDNESDAY_NOON, count = 2 * 24 * 60, price = price))

    @Test
    fun `health check`() =
        api { client ->
            assertEquals("ok", client.get("/health").bodyAsText())
        }

    @Test
    fun `the catalog lists assets and cross sides`() =
        api { client ->
            val body = client.get("/v1/assets").decode<AssetsResponse>()

            assertEquals(9, body.assets.size)
            assertEquals("fx", body.assets.first { it.id == "usdtry" }.kind)
            assertEquals("Türk lirası", body.crossSides.single { it.id == "try" }.name)
        }

    @Test
    fun `snapshot reports price and the three parts`() =
        api { client ->
            seed("usdtry", 100.0)

            val asset =
                client
                    .get("/v1/snapshot?assets=usdtry")
                    .decode<SnapshotResponse>()
                    .assets
                    .single()

            assertEquals("usdtry", asset.id)
            assertEquals("fx", asset.kind)
            assertEquals("USD/TRY", asset.name)
            assertEquals("usd", asset.badge)
            assertEquals(100.0, asset.price)
            assertEquals("TRY", asset.currency)
            assertEquals(WEDNESDAY_NOON.toString(), asset.updatedAt)
            assertTrue(asset.marketOpen)
            assertEquals(listOf("15m", "4h", "1d"), listOfNotNull(asset.drop, asset.upperRing, asset.lowerRing).map { it.interval })
            assertEquals("FLAT", asset.drop?.state)
        }

    @Test
    fun `snapshot of an asset without data has no price or states`() =
        api { client ->
            val asset =
                client
                    .get("/v1/snapshot?assets=eurtry")
                    .decode<SnapshotResponse>()
                    .assets
                    .single()

            assertNull(asset.price)
            assertNull(asset.updatedAt)
            assertNull(asset.drop)
        }

    @Test
    fun `snapshot of a cross rate`() =
        api { client ->
            seed("xau_gram", 100.0)
            seed("xag_gram", 50.0)

            val asset =
                client
                    .get("/v1/snapshot?assets=xau_gram/xag_gram")
                    .decode<SnapshotResponse>()
                    .assets
                    .single()

            assertEquals("cross", asset.kind)
            assertEquals("Altın/Gümüş", asset.name)
            assertEquals("au", asset.badge)
            assertEquals(2.0, asset.price)
            assertNull(asset.currency)
            assertEquals("FLAT", asset.drop?.state)
        }

    @Test
    fun `snapshot rejects unknown assets`() =
        api { client ->
            val response = client.get("/v1/snapshot?assets=usdtry,nope")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ErrorResponse(listOf("unknown asset or invalid cross rate: nope")), response.decode<ErrorResponse>())
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/snapshot").status)
        }

    @Test
    fun `device settings shape the snapshot`() =
        api { client ->
            seed("usdtry", 100.0)
            val put =
                client.put("/v1/devices/device-0001") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"fcmToken":"token-1","assets":[{"id":"usdtry","drop":"1h"}],"alarms":[]}""")
                }
            assertEquals(HttpStatusCode.NoContent, put.status)

            val asset =
                client
                    .get("/v1/snapshot?assets=usdtry&device=device-0001")
                    .decode<SnapshotResponse>()
                    .assets
                    .single()

            assertEquals("1h", asset.drop?.interval)
            assertEquals(
                "15m",
                client
                    .get("/v1/snapshot?assets=usdtry&device=unknown-device")
                    .decode<SnapshotResponse>()
                    .assets
                    .single()
                    .drop
                    ?.interval,
            )
        }

    @Test
    fun `invalid device requests are rejected with reasons`() =
        api { client ->
            suspend fun put(
                id: String,
                body: String,
            ) = client.put("/v1/devices/$id") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            val invalidSettings = put("device-0001", """{"assets":[{"id":"usdtry","drop":"2x"}]}""")
            assertEquals(HttpStatusCode.BadRequest, invalidSettings.status)
            assertEquals(listOf("usdtry.drop: invalid interval '2x'"), invalidSettings.decode<ErrorResponse>().errors)
            assertEquals(HttpStatusCode.BadRequest, put("bad", "{}").status)
            assertEquals(HttpStatusCode.BadRequest, put("device-0001", "not json").status)
        }

    @Test
    fun `a state change reaches the device`() =
        api { client ->
            seed("usdtry", 100.0)
            client.put("/v1/devices/device-0001") {
                contentType(ContentType.Application.Json)
                setBody("""{"fcmToken":"token-1","assets":[{"id":"usdtry"}]}""")
            }
            services.market.accept(PriceUpdate.Tick("usdtry", "test", 100.2, WEDNESDAY_NOON), WEDNESDAY_NOON)

            services.tick(WEDNESDAY_NOON + 1.seconds)
            services.tick(WEDNESDAY_NOON + 61.seconds)

            val message = sender.sent.single()
            assertEquals("token-1", message.token)
            assertEquals("state_change", message.data["type"])
            assertEquals("drop", message.data["part"])
            assertEquals("UP", message.data["to"])
        }

    @Test
    fun `history returns one closing price per slice`() =
        api { client ->
            val start = WEDNESDAY_NOON - 24.hours
            val hourly = (0 until 24).map { hour -> Candle(start + hour.hours + 30.minutes, 1.0, 1.0, 1.0, 100.0 + hour) }
            services.candles.save("usdtry", hourly)
            services.candles.save("xag_gram", hourly.map { it.copy(close = 2.0) })

            val single = client.get("/v1/history?asset=usdtry&range=1d").decode<HistoryResponse>()
            val cross = client.get("/v1/history?asset=usdtry/xag_gram&range=1d&points=24").decode<HistoryResponse>()

            assertEquals(24, single.points.size)
            assertEquals((start + 1.hours).toString(), single.points.first().t)
            assertEquals(100.0, single.points.first().p)
            assertEquals("TRY", single.currency)
            assertEquals(123.0 / 2, cross.points.last().p)
            assertNull(cross.currency)
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/history?asset=usdtry&range=2d").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/history?asset=usdtry&points=1").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/history").status)
            assertNotNull(
                client
                    .get("/v1/history?asset=xag_gram/try")
                    .decode<HistoryResponse>()
                    .points
                    .lastOrNull(),
            )
        }
}
