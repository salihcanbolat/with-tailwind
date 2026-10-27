package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EasingAndBlurTest {
    @Test
    fun `cubic bezier matches css reference values`() {
        // CSS `ease` at 50% progress, as browsers compute it.
        assertEquals(0.8024, Curves.ease.transform(0.5), 1e-3)
        assertEquals(0.0, Curves.ease.transform(-1.0))
        assertEquals(1.0, Curves.ease.transform(2.0))
        assertTrue((1..99).any { Curves.springShape.transform(it / 100.0) > 1.0 }, "the shape spring overshoots")
        assertEquals(0.5, CubicBezier(0.0, 0.0, 1.0, 1.0).transform(0.5), 1e-6)
    }

    @Test
    fun `curves where Newton's method struggles fall back to bisection`() {
        // x(t) = t^3 has no slope at t = 0, so Newton creeps and runs out of steps near 0; y(0.1) = 0.028.
        val slowStart = CubicBezier(0.0, 0.0, 0.0, 1.0)
        assertEquals(0.028, slowStart.transform(0.001), 1e-6)
        // x(t) = 1 - (1-t)^3 is flat at t = 1, and y(t) = t^3: x = 1 - 1e-7 gives t = 1 - 0.00464.
        val flatEnd = CubicBezier(1.0, 0.0, 1.0, 0.0)
        assertEquals(0.98614, flatEnd.transform(1 - 1e-7), 1e-4)
    }

    @Test
    fun `curves of the motion tokens`() {
        val all =
            listOf(Curves.squash, Curves.halo, Curves.easeStandard, Curves.pulse, Curves.easeExit, Curves.springOpen, Curves.springSoft)
        assertTrue(all.all { it.transform(1.0) == 1.0 && it.transform(0.0) == 0.0 })
        assertEquals(CubicBezier(0.25, 1.12, 0.5, 1.0), Curves.springOpen)
    }

    @Test
    fun `a blurred disc fades from full in the middle to nothing beyond three sigmas`() {
        val stops = BlurProfile.stops(30.0, 9.0)

        assertEquals(1.0, stops.first().alpha, 0.01)
        assertEquals(0.0, stops.last().alpha, 0.01)
        assertTrue(stops.zipWithNext().all { (a, b) -> b.alpha <= a.alpha + 1e-9 }, "monotonic")
        // At its edge a blurred disc is a little under half as strong, because the disc curves away.
        assertEquals(0.44, BlurProfile.intensity(1.0, 0.3), 0.01)
        assertEquals(57.0, BlurProfile.outerRadius(30.0, 9.0))
        assertTrue(BlurProfile.stops(30.0, 9.0) === stops, "cached")
    }

    @Test
    fun `scaled bessel function in both ranges`() {
        assertEquals(1.0, BlurProfile.besselI0Scaled(0.0), 1e-7)
        assertEquals(0.4657596, BlurProfile.besselI0Scaled(1.0), 1e-6)
        assertEquals(0.1835408, BlurProfile.besselI0Scaled(5.0), 1e-6)
    }
}
