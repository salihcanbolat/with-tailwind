package com.damla.backend

import com.damla.backend.market.MarketCalendar
import com.damla.engine.Category
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MarketCalendarTest {
    private val calendar = MarketCalendar.bundled()

    private fun open(
        category: Category,
        utc: String,
    ) = calendar.isOpen(category, Instant.parse(utc))

    @Test
    fun `fx and metals trade from Sunday to Friday 17 00 New York time`() {
        assertTrue(open(Category.FX, "2026-10-28T12:00:00Z"))
        // Friday 30 October, daylight saving time: 17:00 New York is 21:00 UTC.
        assertTrue(open(Category.FX, "2026-10-30T20:59:00Z"))
        assertFalse(open(Category.FX, "2026-10-30T21:00:00Z"))
        assertFalse(open(Category.METAL, "2026-10-31T12:00:00Z"))
        // Sunday 1 November, standard time again: 17:00 New York is 22:00 UTC.
        assertFalse(open(Category.METAL, "2026-11-01T21:59:00Z"))
        assertTrue(open(Category.METAL, "2026-11-01T22:00:00Z"))
    }

    @Test
    fun `crypto never closes`() {
        assertTrue(open(Category.CRYPTO, "2026-10-31T12:00:00Z"))
    }

    @Test
    fun `closed dates and sessions that do not wrap the week`() {
        val custom =
            MarketCalendar.fromJson(
                """{"fx": {"zone": "Europe/Istanbul", "opens": "MONDAY 09:00", "closes": "FRIDAY 18:00", "closedDates": ["2026-10-29"]}}""",
            )
        assertTrue(custom.isOpen(Category.FX, Instant.parse("2026-10-28T06:00:00Z")))
        assertFalse(custom.isOpen(Category.FX, Instant.parse("2026-10-29T09:00:00Z")))
        assertFalse(custom.isOpen(Category.FX, Instant.parse("2026-10-26T05:59:00Z")))
        assertFalse(custom.isOpen(Category.FX, Instant.parse("2026-10-30T15:00:00Z")))
        assertTrue(custom.isOpen(Category.METAL, Instant.parse("2026-10-31T12:00:00Z")))
    }
}
