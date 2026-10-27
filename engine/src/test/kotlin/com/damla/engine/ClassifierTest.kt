package com.damla.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassifierTest {
    private fun classify(
        net: Double,
        range: Double,
        previous: State?,
    ): State = classifyOpen(net, range, FX, previous, hysteresis = 0.8)

    @Test
    fun `mandatory table from docs 02`() {
        assertEquals(State.UP, classify(net = 0.12, range = 0.2, previous = State.FLAT))
        assertEquals(State.UP, classify(net = 0.09, range = 0.2, previous = State.UP))
        assertEquals(State.FLAT, classify(net = 0.07, range = 0.2, previous = State.UP))
        assertEquals(State.WAVE, classify(net = -0.05, range = 0.35, previous = State.FLAT))
    }

    @Test
    fun `entering up or down needs the full stable threshold`() {
        assertEquals(State.UP, classify(net = 0.10, range = 0.10, previous = State.FLAT))
        assertEquals(State.FLAT, classify(net = 0.0999, range = 0.10, previous = State.FLAT))
        assertEquals(State.DOWN, classify(net = -0.10, range = 0.10, previous = State.FLAT))
        assertEquals(State.FLAT, classify(net = -0.0999, range = 0.10, previous = State.FLAT))
    }

    @Test
    fun `a value a rounding error below the threshold still counts as reaching it`() {
        assertEquals(State.UP, classify(net = 0.10 - 1e-12, range = 0.10, previous = State.FLAT))
        assertEquals(State.DOWN, classify(net = -0.10 + 1e-12, range = 0.10, previous = State.FLAT))
        assertEquals(State.WAVE, classify(net = 0.0, range = 0.30 - 1e-12, previous = State.FLAT))
    }

    @Test
    fun `leaving up or down needs the measure to drop below h times s`() {
        assertEquals(State.UP, classify(net = 0.08, range = 0.10, previous = State.UP))
        assertEquals(State.FLAT, classify(net = 0.0799, range = 0.10, previous = State.UP))
        assertEquals(State.DOWN, classify(net = -0.08, range = 0.10, previous = State.DOWN))
        assertEquals(State.FLAT, classify(net = -0.0799, range = 0.10, previous = State.DOWN))
    }

    @Test
    fun `entering wave needs the full wave threshold and leaving it needs h times d`() {
        assertEquals(State.WAVE, classify(net = 0.0, range = 0.30, previous = State.FLAT))
        assertEquals(State.FLAT, classify(net = 0.0, range = 0.2999, previous = State.FLAT))
        assertEquals(State.WAVE, classify(net = 0.0, range = 0.24, previous = State.WAVE))
        assertEquals(State.FLAT, classify(net = 0.0, range = 0.2399, previous = State.WAVE))
    }

    @Test
    fun `hysteresis only holds the state the part was already in`() {
        assertEquals(State.FLAT, classify(net = 0.09, range = 0.10, previous = State.DOWN))
        assertEquals(State.FLAT, classify(net = -0.09, range = 0.10, previous = State.UP))
        assertEquals(State.FLAT, classify(net = 0.0, range = 0.25, previous = State.UP))
        assertEquals(State.FLAT, classify(net = 0.09, range = 0.10, previous = null))
    }

    @Test
    fun `up and down take priority over wave`() {
        assertEquals(State.UP, classify(net = 0.10, range = 0.50, previous = State.WAVE))
        assertEquals(State.DOWN, classify(net = -0.10, range = 0.50, previous = State.WAVE))
        assertEquals(State.UP, classify(net = 0.09, range = 0.50, previous = State.UP))
        assertEquals(State.WAVE, classify(net = 0.07, range = 0.35, previous = State.UP))
    }

    @Test
    fun `direction can flip directly`() {
        assertEquals(State.DOWN, classify(net = -0.10, range = 0.10, previous = State.UP))
        assertEquals(State.UP, classify(net = 0.10, range = 0.10, previous = State.DOWN))
    }

    @Test
    fun `mirroring the move mirrors the state`() {
        val previousStates = listOf(null, State.UP, State.DOWN, State.FLAT, State.WAVE)
        for (netStep in -50..50) {
            for (rangeStep in 0..60) {
                for (previous in previousStates) {
                    val net = netStep / 100.0
                    val range = rangeStep / 100.0
                    val mirroredPrevious = previous?.let(::mirror)
                    assertEquals(
                        mirror(classify(net, range, previous)),
                        classify(-net, range, mirroredPrevious),
                        "net=$net range=$range previous=$previous",
                    )
                }
            }
        }
    }
}
