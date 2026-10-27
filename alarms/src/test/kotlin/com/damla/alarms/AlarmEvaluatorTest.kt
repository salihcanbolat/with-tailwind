package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.Frequency
import com.damla.api.Joiner
import com.damla.api.Validity
import com.damla.engine.PriceWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AlarmEvaluatorTest {
    private val start = Instant.parse("2026-10-27T10:00:00Z")

    private fun alarm(
        vararg conditions: Condition,
        joiner: Joiner = Joiner.AND,
        frequency: Frequency = Frequency.EVERY_TIME,
        validity: Validity = Validity.FOREVER,
        waitMarketClosed: Boolean = true,
        enabled: Boolean = true,
    ) = Alarm(
        "a",
        "xau_gram",
        conditions.toList(),
        joiner,
        frequency,
        validity,
        waitMarketClosed = waitMarketClosed,
        enabled = enabled,
        createdAt = start.toString(),
    )

    private fun input(
        minutes: Int = 0,
        price: Double? = 100.0,
        open: Boolean = true,
        states: Map<AlarmPart, String> = mapOf(AlarmPart.DROP to "FLAT"),
        window: PriceWindow? = null,
        stable: Double = 0.1,
    ) = AlarmInput(
        now = start + minutes.minutes,
        marketOpen = open,
        price = price,
        states = states,
        windows = window?.let { mapOf("1h" to it) } ?: emptyMap(),
        stable = stable,
    )

    /** Feeds the inputs in order and returns whether each evaluation rang. */
    private fun run(
        alarm: Alarm,
        vararg inputs: AlarmInput,
    ): List<Boolean> {
        var memory: AlarmMemory? = null
        return inputs.map { input -> AlarmEvaluator.evaluate(alarm, memory, input).also { memory = it.memory }.fired }
    }

    private val above = Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 105.0)

    @Test
    fun `rings when the conditions become true, not while they stay true`() {
        assertEquals(
            listOf(false, true, false, false, true),
            run(alarm(above), input(0, 100.0), input(1, 106.0), input(2, 107.0), input(3, 104.0), input(4, 106.0)),
        )
    }

    @Test
    fun `a condition true from the start rings at once`() {
        val result = AlarmEvaluator.evaluate(alarm(above), null, input(price = 110.0))

        assertTrue(result.fired)
        assertEquals(listOf(0), result.met)
    }

    @Test
    fun `once rings a single time`() {
        assertEquals(
            listOf(true, false, false),
            run(alarm(above, frequency = Frequency.ONCE), input(0, 106.0), input(1, 100.0), input(2, 106.0)),
        )
    }

    @Test
    fun `hourly rings at most once an hour and catches up if still true`() {
        assertEquals(
            listOf(true, false, false, true, false),
            run(
                alarm(above, frequency = Frequency.HOURLY),
                input(0, 106.0),
                input(10, 100.0),
                input(20, 106.0),
                input(61, 106.0),
                input(62, 106.0),
            ),
        )
    }

    @Test
    fun `below and crossing`() {
        val below = Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 95.0)
        val cross = Condition(ConditionType.PRICE, op = AlarmOp.BOTH, value = 100.0)

        assertEquals(listOf(false, true), run(alarm(below), input(0, 96.0), input(1, 94.0)))
        assertEquals(listOf(true), run(alarm(above.copy(op = null)), input(0, 106.0)))
        assertEquals(
            listOf(false, false, true, false, true),
            run(alarm(cross), input(0, 98.0), input(1, 99.0), input(2, 101.0), input(3, 102.0), input(4, 97.0)),
        )
        assertEquals(listOf(false, false), run(alarm(cross), input(0, null), input(1, 101.0)))
    }

    @Test
    fun `state conditions watch the confirmed state of a part`() {
        val upperUp = Condition(ConditionType.STATE, AlarmPart.UPPER_RING, "UP")
        val dropWave = Condition(ConditionType.STATE, target = "WAVE")

        assertEquals(
            listOf(false, true),
            run(
                alarm(upperUp),
                input(0, states = mapOf(AlarmPart.UPPER_RING to "FLAT")),
                input(1, states = mapOf(AlarmPart.UPPER_RING to "UP")),
            ),
        )
        assertEquals(listOf(true), run(alarm(dropWave), input(states = mapOf(AlarmPart.DROP to "WAVE"))))
        // A stray value on a state condition changes nothing.
        assertEquals(listOf(true), run(alarm(dropWave.copy(value = 1.0)), input(states = mapOf(AlarmPart.DROP to "WAVE"))))
    }

    @Test
    fun `percent conditions measure the change over their period`() {
        fun percent(op: AlarmOp?) = Condition(ConditionType.PERCENT, op = op, value = 2.0, interval = "1h")
        val rose = PriceWindow(p0 = 100.0, p1 = 103.0, hi = 103.0, lo = 100.0)
        val fell = PriceWindow(p0 = 100.0, p1 = 97.0, hi = 100.0, lo = 97.0)
        val still = PriceWindow(p0 = 100.0, p1 = 101.0, hi = 101.0, lo = 100.0)

        assertEquals(listOf(true, false), listOf(rose, fell).map { run(alarm(percent(null)), input(window = it)).single() })
        assertEquals(listOf(false, true), listOf(rose, fell).map { run(alarm(percent(AlarmOp.DOWN)), input(window = it)).single() })
        assertEquals(
            listOf(true, true, false),
            listOf(rose, fell, still).map { run(alarm(percent(AlarmOp.BOTH)), input(window = it)).single() },
        )
        assertEquals(listOf(false), run(alarm(percent(AlarmOp.UP)), input(window = null)))
    }

    @Test
    fun `a wave swings by more than its value and ends near the start`() {
        val wave = Condition(ConditionType.WAVE, value = 3.0, interval = "1h")
        val swing = PriceWindow(p0 = 100.0, p1 = 100.05, hi = 102.0, lo = 98.0)
        val move = PriceWindow(p0 = 100.0, p1 = 101.0, hi = 102.0, lo = 98.0)
        val calm = PriceWindow(p0 = 100.0, p1 = 100.0, hi = 101.0, lo = 99.0)

        assertEquals(listOf(true, false, false), listOf(swing, move, calm).map { run(alarm(wave), input(window = it)).single() })
        assertEquals(listOf(false), run(alarm(wave), input(window = null)))
    }

    @Test
    fun `and needs all conditions, or any`() {
        val up = Condition(ConditionType.STATE, target = "UP")
        val both = alarm(above, up)
        val either = alarm(above, up, joiner = Joiner.OR)
        val upState = mapOf(AlarmPart.DROP to "UP")

        assertEquals(listOf(false, true), run(both, input(0, 106.0), input(1, 106.0, states = upState)))
        val result = AlarmEvaluator.evaluate(either, null, input(price = 100.0, states = upState))
        assertTrue(result.fired)
        assertEquals(listOf(1), result.met)
        assertEquals(listOf(false), run(alarm(), input()))
    }

    @Test
    fun `missing values never hold`() {
        assertEquals(listOf(false), run(alarm(Condition(ConditionType.PRICE, op = AlarmOp.UP)), input(price = 200.0)))
    }

    @Test
    fun `a closed market holds the alarm unless it is told not to wait`() {
        assertEquals(listOf(false, true), run(alarm(above), input(0, 106.0, open = false), input(1, 106.0)))
        assertEquals(listOf(true), run(alarm(above, waitMarketClosed = false), input(0, 106.0, open = false)))
    }

    @Test
    fun `validity ends the alarm for good`() {
        val day = alarm(above, validity = Validity.DAY)
        val week = alarm(above, validity = Validity.WEEK)

        assertEquals(listOf(false, false), run(day, input(0, 100.0), input(25 * 60, 106.0)))
        assertEquals(listOf(true), run(week, input(6 * 24 * 60, 106.0)))
        val expired = AlarmEvaluator.evaluate(week, null, input(8 * 24 * 60, 106.0))
        assertTrue(expired.memory.done)
        assertFalse(AlarmEvaluator.evaluate(week, expired.memory, input(0, 106.0)).fired)
    }

    @Test
    fun `a switched off alarm stays quiet`() {
        assertEquals(listOf(false), run(alarm(above, enabled = false), input(price = 106.0)))
    }

    @Test
    fun `the last known price is kept when a price is missing`() {
        val first = AlarmEvaluator.evaluate(alarm(above), null, input(price = 100.0))
        val gap = AlarmEvaluator.evaluate(alarm(above), first.memory, input(1, price = null))

        assertEquals(100.0, gap.memory.lastPrice)
        assertEquals(1.hours, AlarmEvaluator.HOUR)
        assertEquals(start + 1.days, start + 24.hours)
    }
}
