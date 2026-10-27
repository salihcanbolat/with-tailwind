package com.damla.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ConfigTest {
    @Test
    fun `category defaults match docs 02`() {
        assertEquals(Thresholds(stable = 0.10, wave = 0.30), Thresholds.defaultFor(Category.FX))
        assertEquals(Thresholds(stable = 0.15, wave = 0.45), Thresholds.defaultFor(Category.METAL))
        assertEquals(Thresholds(stable = 0.50, wave = 1.50), Thresholds.defaultFor(Category.CRYPTO))
    }

    @Test
    fun `wave threshold must be greater than stable threshold`() {
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = 0.3, wave = 0.3) }
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = 0.3, wave = 0.2) }
    }

    @Test
    fun `thresholds must be positive and finite`() {
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = 0.0, wave = 0.3) }
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = -0.1, wave = 0.3) }
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = Double.NaN, wave = 0.3) }
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = 0.1, wave = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Thresholds(stable = 0.1, wave = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `interval presets`() {
        assertEquals(15.minutes, Interval.FIFTEEN_MINUTES.duration)
        assertEquals(60.minutes, Interval.ONE_HOUR.duration)
        assertEquals(240.minutes, Interval.FOUR_HOURS.duration)
        assertEquals(1_440.minutes, Interval.ONE_DAY.duration)
        assertEquals(10_080.minutes, Interval.ONE_WEEK.duration)
        assertEquals(43_200.minutes, Interval.ONE_MONTH.duration)
        assertEquals(37.minutes, Interval(37).duration)
    }

    @Test
    fun `interval must be positive`() {
        assertFailsWith<IllegalArgumentException> { Interval(0) }
        assertFailsWith<IllegalArgumentException> { Interval(-15) }
    }

    @Test
    fun `engine config defaults match docs 02`() {
        val config = EngineConfig()
        assertEquals(0.8, config.hysteresis)
        assertEquals(60.seconds, config.dropHold)
        assertEquals(5.minutes, config.ringHold)
    }

    @Test
    fun `engine config is validated`() {
        EngineConfig(hysteresis = 1.0, dropHold = Duration.ZERO, ringHold = Duration.ZERO)
        assertFailsWith<IllegalArgumentException> { EngineConfig(hysteresis = 0.0) }
        assertFailsWith<IllegalArgumentException> { EngineConfig(hysteresis = 1.01) }
        assertFailsWith<IllegalArgumentException> { EngineConfig(hysteresis = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { EngineConfig(dropHold = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { EngineConfig(ringHold = (-1).seconds) }
    }
}
