package com.damla.backend

import com.damla.backend.catalog.Target
import com.damla.backend.devices.AssetSettings
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.devices.DeviceSettings
import com.damla.backend.devices.InMemoryDeviceStore
import com.damla.backend.push.FcmSender
import com.damla.backend.push.PushDispatcher
import com.damla.backend.push.PushMessage
import com.damla.backend.push.PushSender
import com.damla.backend.push.SendResult
import com.damla.backend.state.GroupKey
import com.damla.engine.DropStateChanged
import com.damla.engine.MarketClosed
import com.damla.engine.MarketOpened
import com.damla.engine.Ring
import com.damla.engine.RingStateChanged
import com.damla.engine.State
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PushTest {
    private val usd = GroupKey.default(Target.parse("usdtry")!!)

    @Test
    fun `events become the data payloads of docs 03`() {
        val at = WEDNESDAY_NOON
        assertEquals(
            mapOf(
                "type" to "state_change",
                "asset" to "usdtry",
                "part" to "drop",
                "from" to "FLAT",
                "to" to "UP",
                "at" to "2026-10-28T12:00:00Z",
            ),
            PushDispatcher.payload("usdtry", DropStateChanged("usdtry", State.FLAT, State.UP, at)),
        )
        assertEquals(
            "upperRing",
            PushDispatcher.payload("usdtry", RingStateChanged("usdtry", Ring.UPPER, State.FLAT, State.UP, at))["part"],
        )
        assertEquals(
            "lowerRing",
            PushDispatcher.payload("usdtry", RingStateChanged("usdtry", Ring.LOWER, State.FLAT, State.UP, at))["part"],
        )
        assertEquals(mapOf("type" to "refresh"), PushDispatcher.payload("usdtry", MarketClosed("usdtry", at)))
        assertEquals(mapOf("type" to "refresh"), PushDispatcher.payload("usdtry", MarketOpened("usdtry", at)))
    }

    @Test
    fun `only followers with a token get a message, with normal priority`() =
        runTest {
            val registry = DeviceRegistry(InMemoryDeviceStore())
            registry.put("follower-1", DeviceSettings("token-1", listOf(AssetSettings("usdtry"))), WEDNESDAY_NOON)
            registry.put("tokenless-1", DeviceSettings(null, listOf(AssetSettings("usdtry"))), WEDNESDAY_NOON)
            registry.put("other-hour", DeviceSettings("token-2", listOf(AssetSettings("usdtry", drop = "1h"))), WEDNESDAY_NOON)
            val sender = RecordingSender()

            PushDispatcher(sender, registry).dispatch(
                usd,
                listOf(MarketOpened("usdtry", WEDNESDAY_NOON), MarketClosed("usdtry", WEDNESDAY_NOON)),
            )

            assertEquals(listOf(PushMessage("token-1", mapOf("type" to "refresh"), PushMessage.Priority.NORMAL, 3_600)), sender.sent)
        }

    @Test
    fun `an unregistered token is forgotten`() =
        runTest {
            val store = InMemoryDeviceStore()
            val registry = DeviceRegistry(store)
            registry.put("follower-1", DeviceSettings("token-1", listOf(AssetSettings("usdtry"))), WEDNESDAY_NOON)

            PushDispatcher(RecordingSender(SendResult.Unregistered), registry).dispatch(usd, listOf(MarketClosed("usdtry", WEDNESDAY_NOON)))

            assertNull(registry.get("follower-1")?.token)
            assertNull(store.loadAll().getValue("follower-1").fcmToken)
        }

    @Test
    fun `a failing send does not stop the other devices`() =
        runTest {
            val registry = DeviceRegistry(InMemoryDeviceStore())
            registry.put("device-0001", DeviceSettings("token-1", listOf(AssetSettings("usdtry"))), WEDNESDAY_NOON)
            registry.put("device-0002", DeviceSettings("token-2", listOf(AssetSettings("usdtry"))), WEDNESDAY_NOON)
            val delivered = mutableListOf<String>()
            val sender =
                PushSender { message ->
                    if (message.token == "token-1") error("network down")
                    delivered += message.token
                    SendResult.Sent
                }

            PushDispatcher(sender, registry).dispatch(usd, listOf(MarketClosed("usdtry", WEDNESDAY_NOON)))

            assertEquals(listOf("token-2"), delivered)
        }

    @Test
    fun `fcm requests follow the HTTP v1 format`() =
        runTest {
            var body = ""
            var authorization: String? = null
            var url = ""
            val engine =
                MockEngine { request ->
                    url = request.url.toString()
                    authorization = request.headers[HttpHeaders.Authorization]
                    body = String(request.body.toByteArray())
                    respond("""{"name":"projects/damla-test/messages/1"}""", HttpStatusCode.OK)
                }
            val sender = FcmSender(HttpClient(engine), projectId = "damla-test", accessToken = { "access" })

            val result = sender.send(PushMessage("device-token", mapOf("type" to "refresh"), PushMessage.Priority.NORMAL, 3_600))

            assertEquals(SendResult.Sent, result)
            assertEquals("https://fcm.googleapis.com/v1/projects/damla-test/messages:send", url)
            assertEquals("Bearer access", authorization)
            assertEquals(
                Json.parseToJsonElement(
                    """{"message":{"token":"device-token","data":{"type":"refresh"},"android":{"priority":"normal","ttl":"3600s"}}}""",
                ),
                Json.parseToJsonElement(body),
            )
        }

    @Test
    fun `fcm errors are classified`() =
        runTest {
            val unregistered =
                """{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
                   "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}"""
            var reply = unregistered
            val engine =
                MockEngine {
                    respond(reply, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val sender = FcmSender(HttpClient(engine), projectId = "p", accessToken = { "a" })
            val message = PushMessage("t", emptyMap(), PushMessage.Priority.HIGH, 60)

            assertEquals(SendResult.Unregistered, sender.send(message))
            reply = """{"error":{"code":404,"status":"NOT_FOUND"}}"""
            assertIs<SendResult.Failed>(sender.send(message))
            assertNull(FcmSender.errorCode("not json"))
            val android =
                FcmSender
                    .body(message)
                    .getValue("message")
                    .jsonObject
                    .getValue("android")
                    .jsonObject
            assertEquals("high", android.getValue("priority").jsonPrimitive.content)
        }
}
