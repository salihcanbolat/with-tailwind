package com.damla.gauge

import kotlin.math.roundToInt

/**
 * A CSS transition of one number: when the target changes, it eases from wherever it is at that moment
 * towards the new target, after an optional delay. Before the delay ends it holds its start value; a zero
 * duration jumps once the delay is over. Turning back to where a running transition came from takes only as
 * long as the way back is (css-transitions-1 "reversing"). Delays are never negative.
 */
public class NumberTransition(
    initial: Double,
) {
    private val transition = Transition(initial) { from, to, p -> from + (to - from) * p }

    public val end: Double get() = transition.end

    public fun value(atMs: Double): Double = transition.value(atMs)

    /** Starts a transition to [value] unless that is already the target. */
    public fun to(
        value: Double,
        atMs: Double,
        duration: Double,
        delay: Double = 0.0,
        curve: CubicBezier = Curves.ease,
    ): Unit = transition.to(value, atMs, duration, delay, curve)
}

/** A CSS colour transition, interpolated per channel in sRGB with premultiplied alpha. */
public class ColorTransition(
    initial: Int,
) {
    private val transition = Transition(initial, ::lerpPremultiplied)

    public val end: Double get() = transition.end

    public fun value(atMs: Double): Int = transition.value(atMs)

    public fun to(
        value: Int,
        atMs: Double,
        duration: Double,
        delay: Double = 0.0,
        curve: CubicBezier = Curves.ease,
    ): Unit = transition.to(value, atMs, duration, delay, curve)
}

/** CSS interpolates colours with premultiplied alpha, so a fading colour keeps its hue. */
internal fun lerpPremultiplied(
    from: Int,
    to: Int,
    fraction: Double,
): Int {
    val fromAlpha = (from ushr 24) / 255.0
    val toAlpha = (to ushr 24) / 255.0
    if (fromAlpha == 1.0 && toAlpha == 1.0) return GaugeColors.lerp(from, to, fraction)
    val alpha = fromAlpha + (toAlpha - fromAlpha) * fraction
    if (alpha <= 0.0) return 0

    fun channel(shift: Int): Int {
        val a = ((from ushr shift) and 0xFF) * fromAlpha
        val b = ((to ushr shift) and 0xFF) * toAlpha
        return ((a + (b - a) * fraction) / alpha).roundToInt().coerceIn(0, 255) shl shift
    }
    return ((alpha * 255).roundToInt() shl 24) or channel(16) or channel(8) or channel(0)
}

private class Transition<T>(
    initial: T,
    private val lerp: (T, T, Double) -> T,
) {
    private var from = initial
    private var target = initial
    private var start = 0.0
    private var delay = 0.0
    private var duration = 0.0
    private var curve = Curves.ease

    /** Where a later transition has to head for to count as a reversal, and how much this one was shortened. */
    private var reversingStart = initial
    private var shortening = 1.0

    val end: Double get() = start + delay + duration

    private fun progress(atMs: Double): Double {
        val elapsed = atMs - start - delay
        val p = if (duration <= 0.0) (if (elapsed >= 0.0) 1.0 else 0.0) else (elapsed / duration).coerceIn(0.0, 1.0)
        return curve.transform(p)
    }

    fun value(atMs: Double): T = lerp(from, target, progress(atMs))

    fun to(
        value: T,
        atMs: Double,
        duration: Double,
        delay: Double,
        curve: CubicBezier,
    ) {
        if (value == target) return
        val current = value(atMs)
        val running = atMs < end
        val reversing = running && value == reversingStart
        val factor = if (reversing) (progress(atMs) * shortening + 1 - shortening).coerceIn(0.0, 1.0) else 1.0
        reversingStart = if (reversing) target else current
        shortening = factor
        from = current
        target = value
        start = atMs
        this.curve = curve
        if (current == value) {
            // Already there: the running transition is cancelled and nothing new starts.
            this.delay = 0.0
            this.duration = 0.0
        } else {
            this.delay = delay
            this.duration = duration * factor
        }
    }
}
