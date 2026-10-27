package com.damla.wear

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
import com.damla.core.DamlaServices
import com.damla.core.Formats
import com.damla.core.Haptics
import com.damla.core.WearContract
import com.damla.core.WearEvent
import com.damla.gauge.Haptic
import com.damla.gauge.Shape
import kotlinx.coroutines.flow.first

/**
 * The watch's alert (docs/08, design/Saat.dc.html): a standard notification that buzzes in the event's
 * rhythm (docs/05); tapping it opens the alert screen (docs/kararlar.md, "Platform kararları").
 */
object WatchAlerts {
    private const val CHANNEL = "alerts"
    const val EXTRA_EVENT = "com.damla.wear.EVENT"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_description)
                // The event plays its own rhythm.
                enableVibration(false)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun show(
        context: Context,
        event: WearEvent,
    ) {
        val name = name(event.asset)
        val message = message(context, event, name)
        Haptics.play(context, haptic(event))
        if (!allowed(context)) return
        val open =
            PendingIntent.getActivity(
                context,
                event.asset.hashCode(),
                Intent(context, AlertActivity::class.java)
                    .putExtra(EXTRA_EVENT, WearContract.json.encodeToString(WearEvent.serializer(), event))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(icon(shape(event)))
                .setContentTitle(name)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(event.asset.hashCode(), notification)
        } catch (_: SecurityException) {
            // The permission was taken back in between.
        }
    }

    /** The asset's name as the user reads it: its own name in the watch list, else the catalogue's. */
    suspend fun name(id: String): String {
        val store = DamlaServices.repository.store
        return store.watchlist
            .first()
            .firstOrNull { it.id == id }
            ?.name
            ?: store.snapshot
                .first()
                ?.assets
                ?.firstOrNull { it.id == id }
                ?.name
            ?: id
    }

    /** "Yükselişe döndü" for a turn; for an alarm the phone's sentence without the leading asset name. */
    fun message(
        context: Context,
        event: WearEvent,
        name: String,
    ): String {
        val shape = shape(event)
        if (event.kind == WearEvent.Kind.STATE && shape != null) {
            return context.getString(
                when (shape) {
                    Shape.UP -> R.string.alert_up
                    Shape.DOWN -> R.string.alert_down
                    Shape.FLAT -> R.string.alert_flat
                    Shape.WAVE -> R.string.alert_wave
                },
            )
        }
        val text =
            event.text
                .orEmpty()
                .removePrefix(name)
                .trim()
        return text.take(1).uppercase(Formats.TURKISH) + text.drop(1)
    }

    fun shape(event: WearEvent): Shape? = Shape.entries.firstOrNull { it.name == event.shape }

    private fun haptic(event: WearEvent): Haptic {
        val shape = shape(event)
        return when {
            event.kind == WearEvent.Kind.STATE && shape != null -> Haptic.turnedTo(shape)
            else -> Haptic.entries.firstOrNull { it.name == event.haptic } ?: Haptic.TARGET_REACHED
        }
    }

    fun icon(shape: Shape?): Int =
        when (shape) {
            Shape.UP, null -> R.drawable.ic_status_up
            Shape.DOWN -> R.drawable.ic_status_down
            Shape.FLAT -> R.drawable.ic_status_flat
            Shape.WAVE -> R.drawable.ic_status_wave
        }

    private fun allowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
