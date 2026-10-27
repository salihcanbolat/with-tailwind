package com.damla.backend.market

import com.damla.engine.Category
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Trading sessions per category (docs/02: "Takvim backend'de tutulur"). Crypto is always open.
 *
 * Each other category has one weekly session from [Session.opens] to [Session.closes] in its time zone,
 * minus [Session.closedDates]. Loaded from `market-calendar.json`; the bundled values must be checked
 * against how the price sources actually behave (docs/kararlar.md).
 */
class MarketCalendar(
    private val sessions: Map<Category, Session>,
) {
    fun isOpen(
        category: Category,
        at: Instant,
    ): Boolean {
        val session = sessions[category] ?: return true
        val local = at.toJavaInstant().atZone(session.zone)
        if (local.toLocalDate() in session.closedDates) return false
        val minute = minuteOfWeek(local.dayOfWeek, local.toLocalTime())
        val opens = session.opensMinute
        val closes = session.closesMinute
        return if (opens <= closes) minute in opens until closes else minute >= opens || minute < closes
    }

    data class Session(
        val zone: ZoneId,
        val opensMinute: Int,
        val closesMinute: Int,
        val closedDates: Set<LocalDate>,
    )

    companion object {
        fun fromJson(text: String): MarketCalendar {
            val file = Json.decodeFromString<Map<String, SessionJson>>(text)
            val sessions =
                file.entries.associate { (key, value) ->
                    Category.valueOf(key.uppercase()) to
                        Session(
                            zone = ZoneId.of(value.zone),
                            opensMinute = parseWeekTime(value.opens),
                            closesMinute = parseWeekTime(value.closes),
                            closedDates = value.closedDates.map(LocalDate::parse).toSet(),
                        )
                }
            return MarketCalendar(sessions)
        }

        fun bundled(): MarketCalendar {
            val text =
                checkNotNull(MarketCalendar::class.java.getResource("/market-calendar.json")) {
                    "market-calendar.json is missing from the resources"
                }.readText()
            return fromJson(text)
        }

        private fun parseWeekTime(text: String): Int {
            val (day, time) = text.trim().split(' ')
            return minuteOfWeek(DayOfWeek.valueOf(day.uppercase()), LocalTime.parse(time))
        }

        private fun minuteOfWeek(
            day: DayOfWeek,
            time: LocalTime,
        ): Int = (day.value - 1) * 24 * 60 + time.hour * 60 + time.minute
    }

    @Serializable
    private data class SessionJson(
        val zone: String,
        val opens: String,
        val closes: String,
        val closedDates: List<String> = emptyList(),
    )
}
