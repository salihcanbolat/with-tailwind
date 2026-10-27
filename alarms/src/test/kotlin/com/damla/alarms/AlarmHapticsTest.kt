package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.gauge.Haptic
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmHapticsTest {
    private fun alarm(vararg conditions: Condition) = Alarm("a", "usdtry", conditions.toList(), createdAt = "2026-10-27T10:00:00Z")

    private val price = Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 42.0)
    private val percent = Condition(ConditionType.PERCENT, op = AlarmOp.DOWN, value = 2.0, interval = "1d")
    private val wave = Condition(ConditionType.WAVE, interval = "1h")

    @Test
    fun `state alarms feel like the turn they waited for`() {
        assertEquals(Haptic.TURNED_UP, alarmHaptic(alarm(Condition(ConditionType.STATE, AlarmPart.DROP, "UP")), listOf(0)))
        assertEquals(Haptic.TURNED_DOWN, alarmHaptic(alarm(Condition(ConditionType.STATE, AlarmPart.UPPER_RING, "DOWN")), listOf(0)))
        assertEquals(Haptic.CALMED, alarmHaptic(alarm(Condition(ConditionType.STATE, target = "FLAT")), listOf(0)))
        assertEquals(Haptic.CALMED, alarmHaptic(alarm(Condition(ConditionType.STATE, target = "WAVE")), listOf(0)))
    }

    @Test
    fun `a state alarm without a known target falls back to a reached target`() {
        assertEquals(Haptic.TARGET_REACHED, alarmHaptic(alarm(Condition(ConditionType.STATE, AlarmPart.DROP, null)), listOf(0)))
    }

    @Test
    fun `price and percent alarms feel like a reached target, wave alarms like calm`() {
        assertEquals(Haptic.TARGET_REACHED, alarmHaptic(alarm(price), listOf(0)))
        assertEquals(Haptic.TARGET_REACHED, alarmHaptic(alarm(percent), listOf(0)))
        assertEquals(Haptic.CALMED, alarmHaptic(alarm(wave), listOf(0)))
    }

    @Test
    fun `the first condition that held decides`() {
        val mixed = alarm(price, Condition(ConditionType.STATE, AlarmPart.DROP, "DOWN"), wave)
        assertEquals(Haptic.TURNED_DOWN, alarmHaptic(mixed, listOf(1, 2)))
        assertEquals(Haptic.CALMED, alarmHaptic(mixed, listOf(5, 2)))
    }

    @Test
    fun `without a usable index the first condition decides`() {
        val mixed = alarm(wave, price)
        assertEquals(Haptic.CALMED, alarmHaptic(mixed, emptyList()))
        assertEquals(Haptic.CALMED, alarmHaptic(mixed, listOf(7)))
    }
}
