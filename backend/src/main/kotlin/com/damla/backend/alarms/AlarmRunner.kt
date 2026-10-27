package com.damla.backend.alarms

import com.damla.alarms.AlarmEvaluator
import com.damla.alarms.AlarmMemory
import com.damla.api.Alarm
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.push.PushMessage
import com.damla.backend.push.PushSender
import com.damla.backend.push.SendResult
import com.damla.backend.state.StateRunner
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Instant

/**
 * Evaluates every device's alarms (docs/07) after each engine step and tells the device when one rings. The
 * device writes the notification from its own copy of the alarm, so the message carries only what happened.
 *
 * Alarm memory lives in memory: after a restart an alarm whose conditions still hold rings once more.
 */
class AlarmRunner(
    private val devices: DeviceRegistry,
    private val runner: StateRunner,
    private val sender: PushSender,
) {
    private val log = LoggerFactory.getLogger(AlarmRunner::class.java)
    private val memories = HashMap<String, AlarmMemory>()

    suspend fun evaluateAll(now: Instant) {
        val seen = HashSet<String>()
        for (device in devices.withAlarms()) {
            for (alarm in device.alarms) {
                val key = device.groups[alarm.assetId] ?: continue
                val id = device.id + "/" + alarm.id
                seen += id
                val result = AlarmEvaluator.evaluate(alarm, memories[id], runner.alarmInput(key, now))
                memories[id] = result.memory
                if (result.fired) send(device, alarm, result.met, now)
            }
        }
        memories.keys.retainAll(seen)
    }

    private suspend fun send(
        device: DeviceRegistry.Device,
        alarm: Alarm,
        met: List<Int>,
        now: Instant,
    ) {
        val token = device.token ?: return
        val message = PushMessage(token, payload(alarm, met, now), PushMessage.Priority.HIGH, TTL_SECONDS)
        when (val result = trySend(message)) {
            SendResult.Sent -> {
                Unit
            }

            SendResult.Unregistered -> {
                devices.forgetToken(device.id)
            }

            is SendResult.Failed -> {
                log.warn("Alarm push to device {} failed: {}", device.id, result.reason)
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
        /** An alarm still matters for a while; after six hours it is no news any more. */
        private const val TTL_SECONDS = 6 * 3_600L

        /** docs/03's `alarm` message; [met] lists the conditions that held, by index. */
        fun payload(
            alarm: Alarm,
            met: List<Int>,
            at: Instant,
        ): Map<String, String> =
            mapOf(
                "type" to "alarm",
                "alarmId" to alarm.id,
                "asset" to alarm.assetId,
                "met" to met.joinToString(","),
                "at" to at.toString(),
            )
    }
}
