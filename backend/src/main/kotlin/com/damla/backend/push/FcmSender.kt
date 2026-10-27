package com.damla.backend.push

import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Sends through the FCM HTTP v1 API: `POST https://fcm.googleapis.com/v1/projects/{project}/messages:send`
 * with `{"message": {...}}`. The request shape and error format follow the official Firebase Admin SDK
 * for Java (`FirebaseMessagingClientImpl`, `AndroidConfig`, `MessagingServiceErrorResponse`).
 */
class FcmSender(
    private val client: HttpClient,
    private val projectId: String,
    private val accessToken: suspend () -> String,
    private val baseUrl: String = "https://fcm.googleapis.com",
) : PushSender {
    override suspend fun send(message: PushMessage): SendResult {
        val response =
            client.post("$baseUrl/v1/projects/$projectId/messages:send") {
                bearerAuth(accessToken())
                contentType(ContentType.Application.Json)
                setBody(body(message).toString())
            }
        if (response.status.isSuccess()) return SendResult.Sent
        val text = response.bodyAsText()
        return if (errorCode(text) == "UNREGISTERED") SendResult.Unregistered else SendResult.Failed("${response.status}: $text")
    }

    companion object {
        private const val FCM_ERROR_TYPE = "type.googleapis.com/google.firebase.fcm.v1.FcmError"

        fun body(message: PushMessage): JsonObject =
            buildJsonObject {
                putJsonObject("message") {
                    put("token", message.token)
                    putJsonObject("data") { message.data.forEach { (key, value) -> put(key, value) } }
                    putJsonObject("android") {
                        put("priority", message.priority.name.lowercase())
                        put("ttl", "${message.ttlSeconds}s")
                    }
                }
            }

        /** The FCM error code from `error.details[].errorCode` of an error response, if any. */
        fun errorCode(text: String): String? {
            val error = runCatching { Json.parseToJsonElement(text).jsonObject["error"] as? JsonObject }.getOrNull()
            val details = error?.get("details") as? JsonArray ?: return null
            return details
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it["@type"]?.jsonPrimitive?.content == FCM_ERROR_TYPE }
                ?.get("errorCode")
                ?.jsonPrimitive
                ?.content
        }

        /** Access tokens from a service account key file, refreshed when they expire. */
        fun serviceAccountTokens(keyFile: String): suspend () -> String {
            val credentials =
                File(keyFile).inputStream().use {
                    GoogleCredentials.fromStream(it).createScoped("https://www.googleapis.com/auth/cloud-platform")
                }
            return {
                withContext(Dispatchers.IO) {
                    credentials.refreshIfExpired()
                    checkNotNull(credentials.accessToken) { "No FCM access token after refresh" }.tokenValue
                }
            }
        }
    }
}
