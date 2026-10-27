package com.damla.backend

import com.damla.backend.devices.Pairings
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PairingsTest {
    private val clock = MutableClock(WEDNESDAY_NOON)

    @Test
    fun `a code is six easy characters and works once`() =
        runTest {
            val pairings = Pairings(clock, Random(1))
            val (code, expiresAt) = pairings.create("device-0001")

            assertEquals(Pairings.LENGTH, code.length)
            assertTrue(code.all { it in Pairings.ALPHABET })
            assertEquals(WEDNESDAY_NOON + 10.minutes, expiresAt)
            assertEquals("device-0001", pairings.claim(code.lowercase().chunked(3).joinToString(" ")))
            assertNull(pairings.claim(code))
        }

    @Test
    fun `codes expire`() =
        runTest {
            val pairings = Pairings(clock, Random(2))
            val (code, _) = pairings.create("device-0001")
            clock.now = WEDNESDAY_NOON + 10.minutes

            assertNull(pairings.claim(code))
        }

    @Test
    fun `a new code replaces the device's earlier one and skips codes in use`() =
        runTest {
            // The same seed twice makes the second device draw the first device's code before a free one.
            val pairings = Pairings(clock, SeededTwice())
            val (first, _) = pairings.create("device-0001")
            val (other, _) = pairings.create("device-0002")
            val (second, _) = pairings.create("device-0001")

            assertNotEquals(first, other)
            assertNull(pairings.claim(first))
            assertEquals("device-0002", pairings.claim(other))
            assertEquals("device-0001", pairings.claim(second))
        }

    @Test
    fun `unknown codes find nothing`() =
        runTest {
            assertNull(Pairings(clock).claim("ABC-DEF"))
        }

    /** Repeats the first code's draws once, then continues like [Random] with a fixed seed. */
    private class SeededTwice : Random() {
        private val first = Random(7)
        private val drawn = ArrayList<Int>()
        private var replay = -1
        private val rest = Random(8)

        override fun nextBits(bitCount: Int): Int {
            if (drawn.size < Pairings.LENGTH) return first.nextBits(bitCount).also { drawn += it }
            replay++
            return if (replay < Pairings.LENGTH) drawn[replay] else rest.nextBits(bitCount)
        }
    }
}
