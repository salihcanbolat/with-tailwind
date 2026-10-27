package com.damla.app.alarms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.damla.alarms.alarmHaptic
import com.damla.app.MainActivity
import com.damla.app.R
import com.damla.core.DamlaServices
import com.damla.core.FiredAlarm
import com.damla.core.Haptics
import com.damla.core.QuietHours
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Shows the backend's `alarm` messages (docs/03, docs/07). The backend only says which alarm fired and
 * which conditions held; the sentence is written here from the alarm saved on the device, in Turkish.
 */
object AlarmNotifications {
    private const val CHANNEL = "alarms"
    private const val CHANNEL_SILENT = "alarms_silent"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val description = context.getString(R.string.channel_alarms_description)
        // The channels do not vibrate: the alarm plays its own rhythm (docs/05) through [Haptics].
        val loud =
            NotificationChannel(CHANNEL, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    this.description = description
                    enableVibration(false)
                }
        val silent =
            NotificationChannel(CHANNEL_SILENT, context.getString(R.string.channel_alarms_silent), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    this.description = description
                    setSound(null, null)
                    enableVibration(false)
                }
        manager.createNotificationChannels(listOf(loud, silent))
    }

    /** Handles a received message: shows it now, or in the morning when the alarm waits out the night. */
    suspend fun receive(
        context: Context,
        data: Map<String, String>,
    ) {
        val alarmId = data["alarmId"] ?: return
        val alarm =
            DamlaServices.repository.store.alarms
                .first()
                .firstOrNull { it.id == alarmId } ?: return
        val now = LocalDateTime.now()
        if (alarm.waitQuietHours && QuietHours.isQuiet(now.toLocalTime())) {
            val request =
                OneTimeWorkRequestBuilder<MorningWorker>()
                    .setInitialDelay(QuietHours.untilMorning(now).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putAll(data).build())
                    .build()
            // One waiting notification per alarm: a later firing in the same night replaces the earlier one.
            WorkManager.getInstance(context).enqueueUniqueWork("alarm-$alarmId", ExistingWorkPolicy.REPLACE, request)
            return
        }
        show(context, data)
    }

    /** Writes the sentence, keeps it in the alarm history and posts the notification. */
    suspend fun show(
        context: Context,
        data: Map<String, String>,
    ) {
        val store = DamlaServices.repository.store
        val alarm = store.alarms.first().firstOrNull { it.id == data["alarmId"] } ?: return
        val asset =
            store.snapshot
                .first()
                ?.assets
                ?.firstOrNull { it.id == alarm.assetId }
        val name =
            asset?.name
                ?: store.watchlist
                    .first()
                    .firstOrNull { it.id == alarm.assetId }
                    ?.name
                ?: alarm.assetId
        val met = data["met"].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        val at = data["at"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val text = alarmText(context).fired(alarm, name, alarmUnit(asset), met)
        store.addFiredAlarm(FiredAlarm(alarm.id, alarm.assetId, text, at.toString()))

        if (!allowed(context)) return
        val open =
            PendingIntent.getActivity(
                context,
                alarm.id.hashCode(),
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_ALARMS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, if (alarm.sound) CHANNEL else CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(name)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setWhen(at.toEpochMilli())
                .setShowWhen(true)
                .setPriority(if (alarm.sound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setSilent(!alarm.sound)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(alarm.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // The permission was taken back between the check and the post; the history still has it.
            return
        }
        Haptics.play(context, alarmHaptic(alarm, met))
    }

    private fun allowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

/** Shows an alarm that waited for the morning. */
class MorningWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = inputData.keyValueMap.mapValues { it.value.toString() }
        AlarmNotifications.show(applicationContext, data)
        return Result.success()
    }
}
