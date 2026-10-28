package com.damla.backend.digest

import com.damla.api.DigestMoment
import com.damla.backend.catalog.Target
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.push.PushMessage
import com.damla.backend.push.PushSender
import com.damla.backend.push.SendResult
import com.damla.backend.state.GroupKey
import com.damla.engine.AssetStates
import com.damla.engine.DropStateChanged
import com.damla.engine.EngineEvent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * The evening summary, "Günün özeti" (docs/09): one message a day saying how many of the device's drops
 * changed shape since the previous summary, with those moments so the phone can replay them.
 *
 * Every drop change of every group is recorded with the rings' states at that moment. From the device's
 * chosen time (in its own time zone) until [LATE_LIMIT] later, the summary of the past 24 hours is sent once.
 * A day without changes sends nothing. The sentence comes from [writer] when there is one; the phone writes
 * its own otherwise.
 */
class DigestRunner(
    private val devices: DeviceRegistry,
    private val store: DigestStore,
    private val sender: PushSender,
    private val writer: DigestWriter?,
) {
    private val log = LoggerFactory.getLogger(DigestRunner::class.java)

    /** Keeps the drop changes among [events] of [key], with the rings of its [states] after them. */
    suspend fun record(
        key: GroupKey,
        events: List<EngineEvent>,
        states: AssetStates?,
    ) {
        val changes = events.filterIsInstance<DropStateChanged>()
        if (changes.isEmpty() || states == null) return
        store.record(
            changes.map { Moment(key.id, it.at, it.from, it.to, states.upperRing.shapeState, states.lowerRing.shapeState) },
        )
    }

    /** Sends every summary that is due at [now]. */
    suspend fun sendDue(now: Instant) {
        for (device in devices.withDigest()) {
            val settings = device.settings.digest ?: continue
            val zone = ZoneId.of(settings.zone)
            val local = now.toJavaInstant().atZone(zone)
            val due = local.toLocalDate().atTime(LocalTime.parse(settings.at)).atZone(zone)
            if (local < due || local >= due.plus(LATE_LIMIT)) continue
            val token = device.token ?: continue
            // Marked before sending: a summary may be lost, but never comes twice.
            if (!store.markSent(device.id, local.toLocalDate(), now)) continue
            val targets = device.groups.values.associateBy { it.id }
            val moments =
                store.moments(targets.keys, due.minusDays(1).toInstant().toKotlinInstant(), due.toInstant().toKotlinInstant())
            if (moments.isEmpty()) continue
            val names = device.settings.assets.associate { it.id to it.name }
            val lines =
                moments.map { moment ->
                    val target = targets.getValue(moment.group).target
                    DayLine(clock(moment.at, zone), names[target.id] ?: name(target), moment.from, moment.to)
                }
            val data = payload(local, moments, targets, writer?.write(lines.takeLast(MAX_MOMENTS)))
            when (val result = trySend(PushMessage(token, data, PushMessage.Priority.HIGH, TTL_SECONDS))) {
                SendResult.Sent -> Unit
                SendResult.Unregistered -> devices.forgetToken(device.id)
                is SendResult.Failed -> log.warn("Summary to device {} failed: {}", device.id, result.reason)
            }
        }
    }

    suspend fun deleteBefore(before: Instant) = store.deleteBefore(before)

    private suspend fun trySend(message: PushMessage): SendResult =
        try {
            sender.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed(e.toString())
        }

    companion object {
        /** A summary that could not go out within two hours of its time is skipped: by then it is old news. */
        val LATE_LIMIT: java.time.Duration = java.time.Duration.ofHours(2)

        /** FCM data messages hold 4 KB, so the replay keeps the latest moments. */
        const val MAX_MOMENTS = 16
        private const val TTL_SECONDS = 2 * 3_600L
        private val json = Json
        private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * `type=digest`: the [day] (the phone's date), `count` of targets whose drop changed, the latest
         * moments as JSON ([DigestMoment]) and the `text` from the writer, empty when there is none.
         */
        fun payload(
            local: ZonedDateTime,
            moments: List<Moment>,
            targets: Map<String, GroupKey>,
            text: String?,
        ): Map<String, String> {
            val replay =
                moments.takeLast(MAX_MOMENTS).map {
                    DigestMoment(
                        asset = targets.getValue(it.group).target.id,
                        at = it.at.toString(),
                        from = it.from.name,
                        to = it.to.name,
                        upperRing = it.upperRing.name,
                        lowerRing = it.lowerRing.name,
                    )
                }
            return mapOf(
                "type" to "digest",
                "day" to local.toLocalDate().toString(),
                "count" to
                    moments
                        .map { it.group }
                        .distinct()
                        .size
                        .toString(),
                "moments" to json.encodeToString(replay),
                "text" to text.orEmpty(),
            )
        }

        private fun clock(
            at: Instant,
            zone: ZoneId,
        ): String = at.toJavaInstant().atZone(zone).format(HOUR_MINUTE)

        /** "Gram altın", or "Altın/Gümüş" for a cross rate without a name of the user's. */
        private fun name(target: Target): String =
            when (target) {
                is Target.Single -> target.asset.name
                is Target.Cross -> "${target.numerator.short}/${target.denominator.short}"
            }
    }
}
