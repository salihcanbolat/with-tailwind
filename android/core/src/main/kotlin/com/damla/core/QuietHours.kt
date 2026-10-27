package com.damla.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The night (docs/05, design/AlarmKur.dc.html "Gece bildirimleri sabaha kalır"): alarms that wait for it are
 * shown in the morning, and state changes do not vibrate. Provisional hours (docs/kararlar.md).
 */
object QuietHours {
    val FROM: LocalTime = LocalTime.of(23, 0)
    val UNTIL: LocalTime = LocalTime.of(8, 0)

    fun isQuiet(time: LocalTime): Boolean = time >= FROM || time < UNTIL

    fun untilMorning(now: LocalDateTime): Duration {
        val today = now.toLocalDate().atTime(UNTIL)
        val morning = if (now < today) today else today.plusDays(1)
        return Duration.between(now, morning)
    }
}
