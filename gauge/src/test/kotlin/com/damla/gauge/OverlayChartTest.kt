package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayChartTest {
    @Test
    fun `prices fill 6 to 94 percent of the height, highest at the top`() {
        val prices = List(24) { if (it == 5) 20.0 else 10.0 + it % 3 }

        val ys = OverlayChart.points(prices)

        assertEquals(24, ys.size)
        assertEquals(0.94 * 54, ys.first(), 1e-9)
        assertEquals(0.06 * 54, ys[5], 1e-9)
        assertEquals((0.94 - 0.88 * 0.2) * 54, ys[23], 1e-9)
    }

    @Test
    fun `missing or unchanged prices draw a flat line`() {
        assertEquals(OverlayChart.flat, OverlayChart.points(emptyList()))
        assertEquals(OverlayChart.flat, OverlayChart.points(listOf(5.0)))
        assertEquals(OverlayChart.flat, OverlayChart.points(listOf(5.0, 5.0)))
    }

    @Test
    fun `the line is the prototype's curve through every point`() {
        val ys = List(24) { if (it % 2 == 0) 10.0 else 20.0 }

        val segments = OverlayChart.segments(ys)

        assertEquals(23, segments.size)
        // First segment: p0 is p1 itself; x step 260 / 23.
        assertEquals(OverlayChart.Segment(1.9, 11.7, 7.5, 20.0, 11.3, 20.0), segments.first())
        assertEquals(OverlayChart.Segment(252.5, 10.0, 258.1, 18.3, 260.0, 20.0), segments.last())
    }

    @Test
    fun `the scrub dot follows straight lines and picks the nearest point`() {
        val ys = OverlayChart.points(listOf(1.0, 2.0)).let { listOf(0.0, 23.0) + it.drop(2) }

        assertEquals(11.5, OverlayChart.yAt(ys, 130.0 / 23), 1e-9)
        assertEquals(ys.last(), OverlayChart.yAt(ys, 400.0), 1e-9)
        assertEquals(0, OverlayChart.indexAt(-5.0, 7))
        assertEquals(3, OverlayChart.indexAt(130.0, 7))
        assertEquals(6, OverlayChart.indexAt(260.0, 7))
    }
}
