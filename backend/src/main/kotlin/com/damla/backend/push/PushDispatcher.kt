package com.damla.backend.push

import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.state.GroupKey
import com.damla.engine.DropStateChanged
import com.damla.engine.EngineEvent
import com.damla.engine.MarketClosed
import com.damla.engine.MarketOpened
import com.damla.engine.Ring
import com.damla.engine.RingStateChanged
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Turns engine events into FCM data messages for every device following the group (docs/03).
 *
 * State changes carry `state_change`; market openings and closings, which change every part at once,
 * carry `refresh`. All of them only update widgets and views, so they go with normal priority and are
 * dropped after an hour (the app syncs on its own as well).
 */
class PushDispatcher(
    private val sender: PushSender,
    private val devices: DeviceRegistry,
) {
    private val log = LoggerFactory.getLogger(PushDispatcher::class.java)

    suspend fun dispatch(
        key: GroupKey,
        events: List<EngineEvent>,
    ) {
        val payloads = events.map { payload(key.target.id, it) }.distinct()
        for (device in devices.followers(key)) {
            val token = device.token ?: continue
            for (data in payloads) {
                when (val result = trySend(PushMessage(token, data, PushMessage.Priority.NORMAL, TTL_SECONDS))) {
                    SendResult.Sent -> {
                        Unit
                    }

                    SendResult.Unregistered -> {
                        log.info("Device {} has an unregistered token; forgetting it", device.id)
                        devices.forgetToken(device.id)
                        break
                    }

                    is SendResult.Failed -> {
                        log.warn("Push to device {} failed: {}", device.id, result.reason)
                    }
                }
            }
        }
    }

    private suspend fun trySend(message: PushMessage): SendResult =
        try {
            sender.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed(e.toString())
        }

    companion object {
        private const val TTL_SECONDS = 3_600L

        fun payload(
            target: String,
            event: EngineEvent,
        ): Map<String, String> =
            when (event) {
                is DropStateChanged -> {
                    stateChange(target, "drop", event.from.name, event.to.name, event.at.toString())
                }

                is RingStateChanged -> {
                    val part = if (event.ring == Ring.UPPER) "upperRing" else "lowerRing"
                    stateChange(target, part, event.from.name, event.to.name, event.at.toString())
                }

                // Every part changes at once, so the device refreshes; "market" lets it feel the close (docs/05).
                is MarketClosed -> {
                    mapOf("type" to "refresh", "asset" to target, "market" to "closed")
                }

                is MarketOpened -> {
                    mapOf("type" to "refresh", "asset" to target, "market" to "open")
                }
            }

        private fun stateChange(
            target: String,
            part: String,
            from: String,
            to: String,
            at: String,
        ) = mapOf("type" to "state_change", "asset" to target, "part" to part, "from" to from, "to" to to, "at" to at)
    }
}
