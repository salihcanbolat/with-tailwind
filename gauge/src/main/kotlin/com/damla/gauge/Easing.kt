package com.damla.gauge

import kotlin.math.abs

/** A CSS `cubic-bezier(x1, y1, x2, y2)` timing function, evaluated like browsers do (UnitBezier). */
public data class CubicBezier(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
) {
    private val cx = 3 * x1
    private val bx = 3 * (x2 - x1) - cx
    private val ax = 1 - cx - bx
    private val cy = 3 * y1
    private val by = 3 * (y2 - y1) - cy
    private val ay = 1 - cy - by

    /** The eased value for progress [x]; progress outside 0..1 is clamped. */
    public fun transform(x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val t = solveT(x)
        return ((ay * t + by) * t + cy) * t
    }

    private fun sampleX(t: Double) = ((ax * t + bx) * t + cx) * t

    private fun slopeX(t: Double) = (3 * ax * t + 2 * bx) * t + cx

    /** The curve parameter whose x is [x]: Newton's method first, bisection when the slope is too flat. */
    private fun solveT(x: Double): Double {
        var t = x
        repeat(NEWTON_STEPS) {
            val error = sampleX(t) - x
            if (abs(error) < PRECISION) return t
            val slope = slopeX(t)
            if (abs(slope) < MIN_SLOPE) return bisect(x)
            t -= error / slope
        }
        return bisect(x)
    }

    private fun bisect(x: Double): Double {
        var low = 0.0
        var high = 1.0
        var t = x
        while (high - low > PRECISION) {
            if (sampleX(t) < x) low = t else high = t
            t = (low + high) / 2
        }
        return t
    }

    private companion object {
        const val NEWTON_STEPS = 8
        const val PRECISION = 1e-9
        const val MIN_SLOPE = 1e-6
    }
}

/** Every curve the gauge uses (docs/04, docs/05 and design/Gosterge.dc.html). */
public object Curves {
    /** CSS `ease`. */
    public val ease: CubicBezier = CubicBezier(0.25, 0.1, 0.25, 1.0)

    /** `springShape`: the shape change of the drop and the logo slide. */
    public val springShape: CubicBezier = CubicBezier(0.25, 1.1, 0.5, 1.0)
    public val squash: CubicBezier = CubicBezier(0.3, 0.7, 0.4, 1.0)
    public val halo: CubicBezier = CubicBezier(0.2, 0.7, 0.3, 1.0)

    /** `easeStandard`, also the ring redraw. */
    public val easeStandard: CubicBezier = CubicBezier(0.32, 0.72, 0.0, 1.0)

    /** `pulse`: the light turn and the glint. */
    public val pulse: CubicBezier = CubicBezier(0.45, 0.05, 0.2, 1.0)
    public val easeExit: CubicBezier = CubicBezier(0.4, 0.0, 0.2, 1.0)
    public val springOpen: CubicBezier = CubicBezier(0.25, 1.12, 0.5, 1.0)
    public val springSoft: CubicBezier = CubicBezier(0.25, 1.04, 0.5, 1.0)
}
