package com.damla.backend.catalog

import com.damla.engine.Interval

/**
 * Wire format of intervals: `15m`, `1h`, `4h`, `1d`, `1w`, `1mo`, or any whole number of minutes such as
 * `37m`, up to one month (the longest choice in docs/01, and what the server keeps in memory).
 */
object IntervalCodec {
    private const val HOUR = 60
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private val pattern = Regex("""^([1-9][0-9]{0,5})(mo|m|h|d|w)$""")

    fun format(interval: Interval): String {
        val minutes = interval.minutes
        return when {
            minutes == Interval.ONE_MONTH.minutes -> "1mo"
            minutes % WEEK == 0 -> "${minutes / WEEK}w"
            minutes % DAY == 0 -> "${minutes / DAY}d"
            minutes % HOUR == 0 -> "${minutes / HOUR}h"
            else -> "${minutes}m"
        }
    }

    fun parse(text: String): Interval? {
        val match = pattern.matchEntire(text) ?: return null
        val count = match.groupValues[1].toLong()
        val unit =
            when (match.groupValues[2]) {
                "m" -> 1
                "h" -> HOUR
                "d" -> DAY
                "w" -> WEEK
                else -> Interval.ONE_MONTH.minutes
            }
        val minutes = count * unit
        return if (minutes <= Interval.ONE_MONTH.minutes) Interval(minutes.toInt()) else null
    }
}
