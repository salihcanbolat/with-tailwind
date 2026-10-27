package com.damla.backend.push

import org.slf4j.LoggerFactory

/** An FCM data message (docs/03). FCM requires every data value to be a string. */
data class PushMessage(
    val token: String,
    val data: Map<String, String>,
    val priority: Priority,
    val ttlSeconds: Long,
) {
    /**
     * NORMAL for widget and ring updates; HIGH only for messages that show a notification, so that FCM
     * keeps honouring high priority (it is deprioritised when it does not lead to visible notifications).
     */
    enum class Priority { NORMAL, HIGH }
}

sealed interface SendResult {
    data object Sent : SendResult

    /** The token is no longer valid; the device must register again. */
    data object Unregistered : SendResult

    data class Failed(
        val reason: String,
    ) : SendResult
}

fun interface PushSender {
    suspend fun send(message: PushMessage): SendResult
}

/** Used when no Firebase project is configured: logs what would have been sent. */
class LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(message: PushMessage): SendResult {
        log.info("Push (not sent, FCM is not configured): {}", message.data)
        return SendResult.Sent
    }
}
