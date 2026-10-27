package com.damla.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MeasurementTest {
    @Test
    fun `net is signed and range is relative to the base price`() {
        val up = assertIs<Measurement.Valid>(measure(PriceWindow(p0 = 100.0, p1 = 101.0, hi = 102.0, lo = 99.5)))
        assertEquals(1.0, up.net, absoluteTolerance = 1e-12)
        assertEquals(2.5, up.range, absoluteTolerance = 1e-12)

        val down = assertIs<Measurement.Valid>(measure(PriceWindow(p0 = 200.0, p1 = 198.0, hi = 200.0, lo = 197.0)))
        assertEquals(-1.0, down.net, absoluteTolerance = 1e-12)
        assertEquals(1.5, down.range, absoluteTolerance = 1e-12)
    }

    @Test
    fun `missing window is insufficient`() {
        assertEquals(Measurement.Insufficient, measure(null))
    }

    @Test
    fun `zero or negative prices are insufficient`() {
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = 0.0, p1 = 1.0, hi = 1.0, lo = 0.5)))
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = -1.0, p1 = 1.0, hi = 1.0, lo = 0.5)))
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = 1.0, p1 = 0.0, hi = 1.0, lo = 0.5)))
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = 1.0, p1 = 1.0, hi = 1.0, lo = 0.0)))
    }

    @Test
    fun `non finite prices are insufficient`() {
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = 1.0, p1 = Double.NaN, hi = 1.0, lo = 1.0)))
        assertEquals(
            Measurement.Insufficient,
            measure(PriceWindow(p0 = 1.0, p1 = 1.0, hi = Double.POSITIVE_INFINITY, lo = 1.0)),
        )
    }

    @Test
    fun `high below low is insufficient`() {
        assertEquals(Measurement.Insufficient, measure(PriceWindow(p0 = 1.0, p1 = 1.0, hi = 0.9, lo = 1.0)))
    }
}
