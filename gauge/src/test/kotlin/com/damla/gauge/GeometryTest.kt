package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeometryTest {
    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-3,
    ) = assertEquals(expected, actual, tolerance)

    @Test
    fun `the drop's round part is a 240 degree clockwise arc around (0, 12)`() {
        val arc = PathData.parse(Geometry.DROP_PATH).filterIsInstance<PathCommand.ArcTo>().single()

        assertClose(0.0, arc.cx)
        assertClose(12.0045, arc.cy)
        assertClose(22.0, arc.rx)
        assertClose(-30.01, arc.startAngle, 0.01)
        assertClose(239.98, arc.sweepAngle, 0.05)
        val (x, y) = PathData.end(arc)
        assertClose(-19.05, x)
        assertClose(1.0, y)
    }

    @Test
    fun `rings are concentric arcs under the drop drawn left to right`() {
        val upper = PathData.parse(Geometry.UPPER_RING_PATH)[1] as PathCommand.ArcTo
        val lower = PathData.parse(Geometry.LOWER_RING_PATH)[1] as PathCommand.ArcTo

        assertClose(40.0, upper.rx)
        assertClose(0.0, upper.cy, 0.01)
        assertTrue(upper.sweepAngle < 0, "counter-clockwise through the bottom")
        assertClose(-69.8, upper.sweepAngle, 0.1)
        assertClose(48.0, lower.rx, 0.05)
        assertEquals(22.9 to 32.8, PathData.end(upper).let { (x, y) -> Math.round(x * 10) / 10.0 to Math.round(y * 10) / 10.0 })
    }

    @Test
    fun `the light arc turns around its own centre`() {
        val arc = PathData.parse(Geometry.LIGHT_ARC_PATH)[1] as PathCommand.ArcTo

        assertClose(0.0, arc.cx, 0.01)
        assertClose(0.0, arc.cy, 0.01)
        assertClose(-120.0, arc.startAngle, 0.05)
        assertClose(70.0, arc.sweepAngle, 0.05)
    }

    @Test
    fun `path commands round trip through svg path data`() {
        val path = PathData.parse("M1,2 L3,4 C5,6 7,8 9,10 A2 2 0 0 1 13,10 Z")

        assertEquals(
            listOf(PathCommand.MoveTo(1.0, 2.0), PathCommand.LineTo(3.0, 4.0), PathCommand.CubicTo(5.0, 6.0, 7.0, 8.0, 9.0, 10.0)),
            path.take(3),
        )
        assertEquals(PathCommand.Close, path.last())
        assertEquals("M1,2 L3,4 C5,6 7,8 9,10 A2 2 0 0 1 13,10 Z", SvgCanvas.d(path))
    }

    @Test
    fun `radii too small for the end point are scaled up`() {
        val arc = PathData.arc(0.0, 0.0, 1.0, 1.0, largeArc = false, sweep = true, x2 = 10.0, y2 = 0.0)

        assertClose(5.0, arc.rx)
        assertClose(5.0, arc.cx)
        assertClose(180.0, arc.sweepAngle)
        val small = PathData.arc(0.0, 0.0, 10.0, 10.0, largeArc = false, sweep = false, x2 = 10.0, y2 = 0.0)
        assertTrue(small.sweepAngle < 0)
        val large = PathData.arc(0.0, 0.0, 10.0, 10.0, largeArc = true, sweep = false, x2 = 10.0, y2 = 0.0)
        assertTrue(large.sweepAngle < -180)
    }

    @Test
    fun `unsupported path syntax is rejected`() {
        assertFailsWith<IllegalArgumentException> { PathData.parse("M0,0 A1 1 30 0 1 1,1") }
        assertFailsWith<IllegalStateException> { PathData.parse("M0,0 Q1,1 2,2") }
    }

    @Test
    fun `poses and badge centres per shape`() {
        assertEquals(Geometry.DropPose(-6.0, 180.0, 1.0), Geometry.dropPose(Shape.DOWN))
        assertEquals(Geometry.DropPose(6.0, 90.0, 1.0), Geometry.dropPose(Shape.WAVE))
        assertEquals(Geometry.DropPose(0.0, 0.0, 0.55), Geometry.dropPose(Shape.FLAT))
        assertEquals(listOf(0.0 to 12.0, 0.0 to -18.0, 0.0 to 6.0, -12.0 to 6.0), Shape.entries.map(Geometry::badgeCentre))
        assertClose(12.16, Geometry.DROP_SQUASH_PIVOT_Y)
        assertClose(6.08, Geometry.CIRCLE_PIVOT_Y)
    }

    @Test
    fun `pixel sizes follow the design's rounding`() {
        val large = GaugeLayout(120.0)
        assertEquals(145, large.heightPx)
        assertEquals(48, large.badgePx)
        assertEquals(false, large.smallBadge)
        assertClose(1.68, large.badgeRingPx)
        assertClose(1.737, large.highlightBlurPx)
        assertClose(27.6, large.logoFontPx("GBP"))
        assertClose(36.0, large.logoFontPx("Ξ"))

        val tiny = GaugeLayout(16.0)
        assertEquals(6, tiny.badgePx)
        assertEquals(true, tiny.smallBadge)
        assertClose(0.6, tiny.badgeRingPx)
        assertClose(0.4, GaugeLayout(20.0).highlightBlurPx)
        assertEquals(3, GaugeLayout(4.0).badgePx)
    }
}
