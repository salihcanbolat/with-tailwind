package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticTest {
    @Test
    fun `patterns are the ones of docs 05`() {
        assertContentEquals(longArrayOf(0, 25, 70, 35), Haptic.TURNED_UP.timings)
        assertContentEquals(intArrayOf(0, 120, 0, 255), Haptic.TURNED_UP.amplitudes)
        assertContentEquals(longArrayOf(0, 35, 70, 25), Haptic.TURNED_DOWN.timings)
        assertContentEquals(intArrayOf(0, 255, 0, 120), Haptic.TURNED_DOWN.amplitudes)
        assertContentEquals(longArrayOf(0, 260), Haptic.CALMED.timings)
        assertContentEquals(intArrayOf(0, 80), Haptic.CALMED.amplitudes)
        assertContentEquals(longArrayOf(0, 30, 70, 30, 70, 30), Haptic.TARGET_REACHED.timings)
        assertContentEquals(intArrayOf(0, 200, 0, 200, 0, 200), Haptic.TARGET_REACHED.amplitudes)
        assertContentEquals(longArrayOf(0, 20), Haptic.MARKET_CLOSED.timings)
        assertContentEquals(intArrayOf(0, 60), Haptic.MARKET_CLOSED.amplitudes)
    }

    @Test
    fun `every pattern pairs each timing with an amplitude and starts with a pause`() {
        for (haptic in Haptic.entries) {
            assertEquals(haptic.timings.size, haptic.amplitudes.size, haptic.name)
            assertEquals(0, haptic.amplitudes[0], haptic.name)
            assertEquals(0L, haptic.fallback[0], haptic.name)
            // Without amplitude control a pattern alternates pause and vibration, so its length must be even.
            assertEquals(0, haptic.fallback.size % 2, haptic.name)
        }
    }

    @Test
    fun `without amplitude control rising and falling differ by length`() {
        val up = Haptic.TURNED_UP.fallback
        val down = Haptic.TURNED_DOWN.fallback
        assertTrue(up[1] < up[3], "rising: short then long")
        assertTrue(down[1] > down[3], "falling: long then short")
    }

    @Test
    fun `a turn feels like its new shape`() {
        assertEquals(Haptic.TURNED_UP, Haptic.turnedTo(Shape.UP))
        assertEquals(Haptic.TURNED_DOWN, Haptic.turnedTo(Shape.DOWN))
        assertEquals(Haptic.CALMED, Haptic.turnedTo(Shape.FLAT))
        assertEquals(Haptic.CALMED, Haptic.turnedTo(Shape.WAVE))
    }
}
