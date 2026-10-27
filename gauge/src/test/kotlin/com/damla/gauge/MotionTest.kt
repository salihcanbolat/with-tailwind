package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    private val up = GaugeVisual(Shape.UP, upperRing = Shape.UP, lowerRing = Shape.DOWN)

    @Test
    fun `a visual at rest needs no frames`() {
        val motion = GaugeMotion(up)

        val frame = motion.frame(0.0)
        assertEquals(GaugeColors.UP, frame.bodyColor)
        assertEquals(0.0, frame.haloAlpha)
        assertEquals(1.0, frame.haloScale)
        assertEquals(0.0, frame.upperRingDashOffset)
        assertEquals(Geometry.HIGHLIGHT_ALPHA, frame.glintAlpha)
        assertEquals(0.0, motion.settledAt())
    }

    @Test
    fun `the same visual again changes nothing`() {
        val motion = GaugeMotion(up)

        motion.update(up, 100.0)

        assertEquals(0.0, motion.settledAt())
    }

    @Test
    fun `a shape change settles when its longest effect ends`() {
        val motion = GaugeMotion(up)

        motion.update(up.copy(shape = Shape.DOWN), 1_000.0)

        assertEquals(1_000.0 + MotionSpec.HALO, motion.settledAt())
        assertEquals(GaugeColors.DOWN, motion.frame(5_000.0).bodyColor)
        assertEquals(180.0, motion.frame(5_000.0).dropRotation)
        assertEquals(1.7, motion.frame(5_000.0).haloScale, 1e-9)
        assertEquals(0.0, motion.frame(5_000.0).haloAlpha, 1e-9)
    }

    @Test
    fun `speed scales every duration and delay like the prototype`() {
        val slow = GaugeMotion(up, speed = 2.0)

        slow.update(up.copy(lowerRing = Shape.FLAT), 0.0)

        assertEquals(2.0 * (MotionSpec.LOWER_REDRAW_DELAY + MotionSpec.REDRAW), slow.settledAt())
        assertEquals(100.0, slow.frame(2.0 * MotionSpec.LOWER_REDRAW_DELAY).lowerRingDashOffset, 1e-9)
    }

    @Test
    fun `reduced motion jumps to the new state without effects`() {
        val motion = GaugeMotion(up, reduceMotion = true)

        motion.update(GaugeVisual(Shape.FLAT, Shape.DOWN, Shape.UP, pulseTurns = 1), 0.0)

        val frame = motion.frame(0.0)
        assertEquals(GaugeColors.FLAT, frame.bodyColor)
        assertEquals(0.0, frame.dropAlpha)
        assertEquals(1.0, frame.circleAlpha)
        assertEquals(1.0, frame.squashX)
        assertEquals(0.0, frame.haloAlpha)
        assertEquals(0.0, frame.upperRingDashOffset)
        assertEquals(Geometry.HIGHLIGHT_ALPHA, frame.glintAlpha)
        assertEquals(360.0, frame.lightRotation)
        assertEquals(0.0, motion.settledAt())
    }

    @Test
    fun `closing greys everything and keeps the logo white`() {
        val motion = GaugeMotion(up.copy(shape = Shape.FLAT))
        assertEquals(GaugeColors.LOGO_ON_FLAT, motion.frame(0.0).logoTextColor)

        motion.update(motion.visual.copy(closed = true), 0.0)

        val frame = motion.frame(1_000.0)
        assertEquals(
            listOf(GaugeColors.CLOSED, GaugeColors.CLOSED, GaugeColors.CLOSED),
            listOf(frame.bodyColor, frame.upperRingColor, frame.lowerRingColor),
        )
        assertEquals(GaugeColors.WHITE, frame.logoTextColor)
        assertEquals(0.0, frame.haloAlpha, "closing is not a shape change")
    }

    @Test
    fun `the glint plays once per new price and the light turns with it`() {
        val motion = GaugeMotion(up)

        motion.update(up.copy(pulseTurns = -1), 0.0)

        assertTrue(motion.frame(385.0).glintAlpha > 0.6)
        assertTrue(motion.frame(385.0).glintX > 0)
        assertEquals(Geometry.HIGHLIGHT_ALPHA, motion.frame(1_100.0).glintAlpha)
        assertEquals(-360.0, motion.frame(1_100.0).lightRotation)
        assertEquals(1_100.0, motion.settledAt())
    }

    @Test
    fun `frames before a change show the state before it`() {
        val reduced = GaugeMotion(up, reduceMotion = true)
        reduced.update(up.copy(shape = Shape.DOWN, upperRing = Shape.FLAT), 100.0)
        assertEquals(GaugeColors.UP, reduced.frame(50.0).bodyColor)
        assertEquals(0.0, reduced.frame(50.0).dropRotation)

        val pulsing = GaugeMotion(up)
        pulsing.update(up.copy(pulseTurns = 1), 100.0)
        assertEquals(Geometry.HIGHLIGHT_ALPHA, pulsing.frame(50.0).glintAlpha)
    }

    @Test
    fun `keyframe segments are eased separately`() {
        val linear = CubicBezier(0.0, 0.0, 1.0, 1.0)
        val offsets = doubleArrayOf(0.0, 0.5, 1.0)
        val values = doubleArrayOf(0.0, 10.0, 0.0)

        assertEquals(5.0, keyframes(offsets, values, 0.25, linear), 1e-6)
        assertEquals(10.0, keyframes(offsets, values, 0.5, linear), 1e-6)
        assertEquals(0.0, keyframes(offsets, values, 1.0, linear), 1e-6)
    }

    @Test
    fun `colours interpolate per channel`() {
        assertEquals(0xFF808080.toInt(), GaugeColors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5025))
        assertEquals(listOf(GaugeColors.UP, GaugeColors.DOWN, GaugeColors.FLAT, GaugeColors.WAVE), Shape.entries.map(GaugeColors::of))
    }
}
