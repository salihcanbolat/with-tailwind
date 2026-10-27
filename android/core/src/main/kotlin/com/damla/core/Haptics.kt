package com.damla.core

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.damla.gauge.Haptic
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Plays the haptic language of docs/05 (`VibrationEffect.createWaveform`). Nothing plays when "Titreşim" is
 * off, at night, or while Do Not Disturb holds notifications back. The events come from pushes while the
 * app is in the background, so they vibrate as notifications: Android lets background apps vibrate only
 * with a notification, ringtone or alarm usage.
 */
object Haptics {
    /** Several pushes can land together (one per asset); one rhythm is enough. */
    private const val GAP_MILLIS = 1_500L
    private var lastAt = 0L

    suspend fun play(
        context: Context,
        haptic: Haptic,
    ) {
        if (!DamlaServices.repository.store.settings
                .first()
                .vibration
        ) {
            return
        }
        if (QuietHours.isQuiet(LocalTime.now()) || doNotDisturb(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < GAP_MILLIS) return
        lastAt = now
        vibrate(context, haptic)
    }

    private fun doNotDisturb(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun vibrate(
        context: Context,
        haptic: Haptic,
    ) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val effect =
            if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(haptic.timings, haptic.amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(haptic.fallback, -1)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
