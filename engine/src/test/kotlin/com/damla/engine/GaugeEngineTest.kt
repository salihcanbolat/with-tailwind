package com.damla.engine

import com.damla.engine.State.CLOSED
import com.damla.engine.State.DOWN
import com.damla.engine.State.FLAT
import com.damla.engine.State.UP
import com.damla.engine.State.WAVE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration

class GaugeEngineTest {
    private val engine = GaugeEngine()
    private val asset =
        AssetConfig(
            asset = "usdtry",
            drop = PartConfig(Interval.FIFTEEN_MINUTES, FX),
            upperRing = PartConfig(Interval.FOUR_HOURS, FX),
            lowerRing = PartConfig(Interval.ONE_DAY, FX),
        )
    private val flat = window(net = 0.0)
    private val up = window(net = 0.12)
    private val down = window(net = -0.12)

    private fun input(
        second: Int,
        drop: PriceWindow? = flat,
        upper: PriceWindow? = flat,
        lower: PriceWindow? = flat,
        open: Boolean = true,
    ) = AssetInput(now = at(second), marketOpen = open, drop = drop, upperRing = upper, lowerRing = lower)

    /** Feeds the inputs in order, carrying the memory along, and returns every evaluation. */
    private fun run(
        vararg inputs: AssetInput,
        engine: GaugeEngine = this.engine,
    ): List<Evaluation> {
        var memory: AssetMemory? = null
        return inputs.map { input -> engine.evaluate(asset, memory, input).also { memory = it.memory } }
    }

    private fun List<Evaluation>.dropStates() = map { assertNotNull(it.states).drop.state }

    private fun dropChanged(
        from: State,
        to: State,
        second: Int,
    ) = DropStateChanged(asset = "usdtry", from = from, to = to, at = at(second))

    @Test
    fun `first evaluation sets every part immediately without events`() {
        val result = engine.evaluate(asset, memory = null, input(0, drop = up, upper = window(-0.05, 0.35), lower = down))

        val states = assertNotNull(result.states)
        assertEquals(UP, states.drop.state)
        assertEquals(UP, states.drop.shapeState)
        assertEquals(0.12, states.drop.net, absoluteTolerance = 1e-9)
        assertEquals(0.12, states.drop.range, absoluteTolerance = 1e-9)
        assertEquals(Interval.FIFTEEN_MINUTES, states.drop.interval)
        assertEquals(at(0), states.drop.since)
        assertEquals(WAVE, states.upperRing.state)
        assertEquals(Interval.FOUR_HOURS, states.upperRing.interval)
        assertEquals(DOWN, states.lowerRing.state)
        assertEquals(Interval.ONE_DAY, states.lowerRing.interval)
        assertEquals(emptyList(), result.events)
    }

    @Test
    fun `states stay unknown until every part has data`() {
        val (first, second) = run(input(0, lower = null), input(10))
        val noUpperRing = engine.evaluate(asset, memory = null, input(0, upper = null))

        assertNull(first.states)
        assertNotNull(first.memory.drop)
        assertNull(first.memory.lowerRing)
        assertNull(noUpperRing.states)
        assertNotNull(second.states)
        assertEquals(emptyList(), first.events + second.events)
    }

    @Test
    fun `drop change is confirmed only after holding for 60 seconds`() {
        val results = run(input(0), input(10, drop = up), input(69, drop = up), input(70, drop = up))

        assertEquals(listOf(FLAT, FLAT, FLAT, UP), results.dropStates())
        assertEquals(emptyList(), results.take(3).flatMap { it.events })
        assertEquals(listOf(dropChanged(FLAT, UP, 70)), results[3].events)
        assertEquals(at(10), assertNotNull(results[3].states).drop.since)
    }

    @Test
    fun `falling prices are held the same way`() {
        val results = run(input(0), input(10, drop = down), input(70, drop = down))

        assertEquals(listOf(FLAT, FLAT, DOWN), results.dropStates())
        assertEquals(listOf(dropChanged(FLAT, DOWN, 70)), results.flatMap { it.events })
    }

    @Test
    fun `returning to the shown state cancels the pending change`() {
        val results =
            run(
                input(0),
                input(10, drop = up),
                input(40),
                input(50, drop = up),
                input(100, drop = up),
                input(110, drop = up),
            )

        assertEquals(listOf(FLAT, FLAT, FLAT, FLAT, FLAT, UP), results.dropStates())
        assertEquals(listOf(dropChanged(FLAT, UP, 110)), results.flatMap { it.events })
    }

    @Test
    fun `a different candidate restarts the hold`() {
        val wave = window(net = 0.05, range = 0.35)
        val results = run(input(0), input(10, drop = up), input(40, drop = wave), input(80, drop = wave), input(100, drop = wave))

        assertEquals(listOf(FLAT, FLAT, FLAT, FLAT, WAVE), results.dropStates())
        assertEquals(listOf(dropChanged(FLAT, WAVE, 100)), results.flatMap { it.events })
        assertEquals(at(40), assertNotNull(results[4].states).drop.since)
    }

    @Test
    fun `hysteresis follows the pending candidate`() {
        val slightlyUp = window(net = 0.09)
        val results = run(input(0), input(10, drop = up), input(30, drop = slightlyUp), input(70, drop = slightlyUp))

        assertEquals(listOf(FLAT, FLAT, FLAT, UP), results.dropStates())
        assertEquals(listOf(dropChanged(FLAT, UP, 70)), results.flatMap { it.events })
    }

    @Test
    fun `rings hold for five minutes and report which ring changed`() {
        val ringDown = window(net = -0.2)
        val results =
            run(
                input(0),
                input(10, upper = up, lower = ringDown),
                input(309, upper = up, lower = ringDown),
                input(310, upper = up, lower = ringDown),
            )

        assertEquals(FLAT, assertNotNull(results[2].states).upperRing.state)
        assertEquals(emptyList(), results.take(3).flatMap { it.events })
        assertEquals(
            listOf(
                RingStateChanged(asset = "usdtry", ring = Ring.UPPER, from = FLAT, to = UP, at = at(310)),
                RingStateChanged(asset = "usdtry", ring = Ring.LOWER, from = FLAT, to = DOWN, at = at(310)),
            ),
            results[3].events,
        )
    }

    @Test
    fun `hold times are configurable`() {
        val immediate = GaugeEngine(EngineConfig(dropHold = Duration.ZERO, ringHold = Duration.ZERO))

        val results = run(input(0), input(1, drop = up, upper = down), engine = immediate)

        assertEquals(
            listOf(
                dropChanged(FLAT, UP, 1),
                RingStateChanged(asset = "usdtry", ring = Ring.UPPER, from = FLAT, to = DOWN, at = at(1)),
            ),
            results[1].events,
        )
        assertEquals(at(1), assertNotNull(results[1].states).drop.since)
    }

    @Test
    fun `values refresh while the state is unchanged`() {
        val results = run(input(0, drop = up), input(10, drop = window(net = 0.2)))

        val drop = assertNotNull(results[1].states).drop
        assertEquals(UP, drop.state)
        assertEquals(0.2, drop.net, absoluteTolerance = 1e-9)
        assertEquals(at(0), drop.since)
    }

    @Test
    fun `values refresh while a change is pending`() {
        val results = run(input(0), input(10, drop = up))

        val drop = assertNotNull(results[1].states).drop
        assertEquals(FLAT, drop.state)
        assertEquals(0.12, drop.net, absoluteTolerance = 1e-9)
    }

    @Test
    fun `missing or invalid data keeps the last state`() {
        val invalid = PriceWindow(p0 = 0.0, p1 = 1.0, hi = 1.0, lo = 1.0)

        val results = run(input(0, drop = up), input(10, drop = null), input(20, drop = invalid))

        assertEquals(results[0].memory, results[1].memory)
        assertEquals(results[0].memory, results[2].memory)
        assertEquals(emptyList(), results.flatMap { it.events })
    }

    @Test
    fun `closing the market greys every part, keeps the shape and only reports the closure`() {
        val results = run(input(0, drop = up, lower = window(net = -0.2)), input(10, open = false, drop = down))

        val closed = assertNotNull(results[1].states)
        assertEquals(listOf(CLOSED, CLOSED, CLOSED), listOf(closed.drop, closed.upperRing, closed.lowerRing).map { it.state })
        assertEquals(listOf(UP, FLAT, DOWN), listOf(closed.drop, closed.upperRing, closed.lowerRing).map { it.shapeState })
        assertEquals(at(10), closed.drop.since)
        assertEquals(listOf(MarketClosed(asset = "usdtry", at = at(10))), results[1].events)
    }

    @Test
    fun `while closed the shape and since stay put`() {
        val results =
            run(
                input(0, drop = up),
                input(10, open = false),
                input(20, open = false, drop = down),
                input(30, open = false, drop = null, upper = null, lower = null),
            )

        for (result in results.drop(2)) {
            val drop = assertNotNull(result.states).drop
            assertEquals(CLOSED, drop.state)
            assertEquals(UP, drop.shapeState)
            assertEquals(at(10), drop.since)
            assertEquals(emptyList(), result.events)
        }
    }

    @Test
    fun `reopening with the same shape only reports the opening`() {
        val results = run(input(0, drop = up), input(10, open = false), input(20, drop = up))

        val drop = assertNotNull(results[2].states).drop
        assertEquals(UP, drop.state)
        assertEquals(at(20), drop.since)
        assertEquals(listOf(MarketOpened(asset = "usdtry", at = at(20))), results[2].events)
    }

    @Test
    fun `reopening into a different state reports it at once`() {
        val results = run(input(0, drop = up), input(10, open = false), input(20, drop = down))

        assertEquals(DOWN, assertNotNull(results[2].states).drop.state)
        assertEquals(
            listOf(MarketOpened(asset = "usdtry", at = at(20)), dropChanged(UP, DOWN, 20)),
            results[2].events,
        )
    }

    @Test
    fun `reopening applies hysteresis against the shape kept while closed`() {
        val results = run(input(0, drop = up), input(10, open = false), input(20, drop = window(net = 0.09)))

        assertEquals(UP, assertNotNull(results[2].states).drop.state)
        assertEquals(listOf(MarketOpened(asset = "usdtry", at = at(20))), results[2].events)
    }

    @Test
    fun `reopening without data restores the kept shape`() {
        val results = run(input(0, drop = up), input(10, open = false), input(20, drop = null))

        val drop = assertNotNull(results[2].states).drop
        assertEquals(UP, drop.state)
        assertEquals(at(20), drop.since)
        assertEquals(listOf(MarketOpened(asset = "usdtry", at = at(20))), results[2].events)
    }

    @Test
    fun `a change pending at the close is dropped`() {
        val results = run(input(0), input(10, drop = up), input(30, open = false, drop = up), input(40, drop = up))

        assertEquals(FLAT, assertNotNull(results[2].states).drop.shapeState)
        assertEquals(
            listOf(MarketOpened(asset = "usdtry", at = at(40)), dropChanged(FLAT, UP, 40)),
            results[3].events,
        )
    }

    @Test
    fun `mandatory closed row - closed market is CLOSED from the first evaluation`() {
        val still = engine.evaluate(asset, memory = null, input(0, open = false))
        val moved = engine.evaluate(asset, memory = null, input(0, open = false, drop = up))

        val drop = assertNotNull(still.states).drop
        assertEquals(CLOSED, drop.state)
        assertEquals(FLAT, drop.shapeState)
        assertEquals(UP, assertNotNull(moved.states).drop.shapeState)
        assertEquals(emptyList(), still.events + moved.events)
    }

    @Test
    fun `closed market without any data has no state yet`() {
        val result = engine.evaluate(asset, memory = null, input(0, open = false, drop = null, upper = null, lower = null))

        assertNull(result.states)
        assertNull(result.memory.drop)
        assertEquals(emptyList(), result.events)
    }

    @Test
    fun `price exactly at the stable threshold counts despite floating point error`() {
        // (100.1 - 100) / 100 * 100 evaluates to 0.09999999999999432.
        val exactlyStable = PriceWindow(p0 = 100.0, p1 = 100.1, hi = 100.1, lo = 100.0)

        val result = engine.evaluate(asset, memory = null, input(0, drop = exactlyStable))

        assertEquals(UP, assertNotNull(result.states).drop.state)
    }
}
