package com.damla.core

import com.damla.api.AssetsResponse
import com.damla.api.DeviceSettings
import com.damla.api.HistoryResponse
import com.damla.api.PairingCode
import com.damla.api.PriceEvent
import com.damla.api.SnapshotResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json

/** The backend's v1 API (docs/03). */
class DamlaApi(
    private val baseUrl: String,
    private val client: HttpClient = defaultClient(),
) {
    suspend fun snapshot(
        ids: List<String>,
        deviceId: String?,
    ): SnapshotResponse =
        client
            .get("$baseUrl/v1/snapshot") {
                parameter("assets", ids.joinToString(","))
                deviceId?.let { parameter("device", it) }
            }.body()

    suspend fun history(
        id: String,
        range: String,
        points: Int = HISTORY_POINTS,
    ): HistoryResponse =
        client
            .get("$baseUrl/v1/history") {
                parameter("asset", id)
                parameter("range", range)
                parameter("points", points)
            }.body()

    suspend fun assets(): AssetsResponse = client.get("$baseUrl/v1/assets").body()

    suspend fun putDevice(
        id: String,
        settings: DeviceSettings,
    ) {
        client.put("$baseUrl/v1/devices/$id") {
            contentType(ContentType.Application.Json)
            setBody(settings)
        }
    }

    /** A pairing code for a computer (docs/08), valid ten minutes. */
    suspend fun pairing(deviceId: String): PairingCode = client.post("$baseUrl/v1/devices/$deviceId/pairing").body()

    /** Live price events while collected; the connection closes when collection stops. */
    fun stream(ids: List<String>): Flow<PriceEvent> =
        channelFlow {
            client.sse("$baseUrl/v1/stream", { parameter("assets", ids.joinToString(",")) }) {
                incoming.collect { event ->
                    val data = event.data
                    if (event.event == "price" && data != null) send(json.decodeFromString<PriceEvent>(data))
                }
            }
        }

    companion object {
        /** The prototype's chart always has 24 points, so ranges morph into each other. */
        const val HISTORY_POINTS = 24
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): HttpClient =
            HttpClient(OkHttp) {
                expectSuccess = true
                install(ContentNegotiation) { json(json) }
                install(SSE)
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 30_000
                }
            }
    }
}
