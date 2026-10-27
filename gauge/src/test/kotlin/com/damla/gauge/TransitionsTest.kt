package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals

class TransitionsTest {
    private val linear = CubicBezier(0.0, 0.0, 1.0, 1.0)

    @Test
    fun `turning back takes only as long as the way back`() {
        val transition = NumberTransition(0.0)
        transition.to(1.0, atMs = 0.0, duration = 100.0, curve = linear)

        transition.to(0.0, atMs = 50.0, duration = 100.0, curve = linear)

        assertEquals(100.0, transition.end)
        assertEquals(0.25, transition.value(75.0), 1e-9)
    }

    @Test
    fun `heading somewhere new takes the full duration`() {
        val transition = NumberTransition(0.0)
        transition.to(1.0, atMs = 0.0, duration = 100.0, curve = linear)

        transition.to(2.0, atMs = 50.0, duration = 100.0, curve = linear)

        assertEquals(150.0, transition.end)
    }

    @Test
    fun `turning back during the delay cancels the transition`() {
        val transition = NumberTransition(0.0)
        transition.to(1.0, atMs = 0.0, duration = 100.0, delay = 100.0)

        transition.to(0.0, atMs = 50.0, duration = 100.0)

        assertEquals(50.0, transition.end)
        assertEquals(0.0, transition.value(60.0))
    }

    @Test
    fun `the same target changes nothing`() {
        val transition = ColorTransition(GaugeColors.UP)
        transition.to(GaugeColors.DOWN, atMs = 0.0, duration = 100.0)

        transition.to(GaugeColors.DOWN, atMs = 50.0, duration = 300.0)

        assertEquals(100.0, transition.end)
        assertEquals(GaugeColors.DOWN, transition.value(100.0))
    }

    @Test
    fun `colours fade with premultiplied alpha`() {
        val linearFade = ColorTransition(0xFFFFFFFF.toInt())
        linearFade.to(0x00000000, atMs = 0.0, duration = 100.0, curve = linear)
        val glass = ColorTransition(0xFFFFFFFF.toInt())
        glass.to(0x80000000.toInt(), atMs = 0.0, duration = 100.0, curve = linear)

        // Fading to transparent keeps the colour; towards half-transparent black it darkens by weight.
        assertEquals(0x80FFFFFF.toInt(), linearFade.value(50.0))
        assertEquals(0xC0AAAAAA.toInt(), glass.value(50.0))
    }
}
