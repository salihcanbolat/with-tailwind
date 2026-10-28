package com.damla.app.digest

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
import com.damla.app.MainActivity
import com.damla.app.R
import com.damla.core.DamlaServices
import com.damla.core.QuietHours
import com.damla.core.StoredDigest
import com.damla.core.sentence
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Shows the backend's `digest` message, "Günün özeti" (docs/09): one evening notification saying how many
 * drops changed shape today, with Claude's sentence or the phone's own. A tap opens the day's replay.
 */
object DigestNotifications {
    private const val CHANNEL = "digest"
    private const val NOTIFICATION_ID = 0x0D16E57

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_digest_description)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Keeps the summary for the replay screen and shows it, unless the user turned it off or it is night. */
    suspend fun receive(
        context: Context,
        data: Map<String, String>,
    ) {
        val digest = StoredDigest.fromMessage(data) ?: return
        val store = DamlaServices.repository.store
        store.setDigest(digest)
        if (!store.settings.first().digest || QuietHours.isQuiet(LocalTime.now()) || !allowed(context)) return
        val title = context.getString(R.string.digest_count, digest.count)
        val text = digest.text.ifEmpty { digest.sentence(context, names(context)) }
        val open =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_DIGEST, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        try {
            // One a day: a new summary replaces yesterday's if it is still there.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was taken back between the check and the post; the replay still has it.
        }
    }

    /** A target's name as the app shows it: the snapshot's, the user's own for a cross rate, or its id. */
    suspend fun names(context: Context): (String) -> String {
        val store = DamlaServices.repository.store
        val snapshot =
            store.snapshot
                .first()
                ?.assets
                .orEmpty()
                .associate { it.id to it.name }
        val own = store.watchlist.first().associate { it.id to it.name }
        return { id -> own[id] ?: snapshot[id] ?: id }
    }

    private fun allowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
