package com.damla.gauge

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** One stop of a radial gradient: [offset] 0..1 of the gradient radius, opacity [alpha] 0..1. */
public data class GradientStop(
    val offset: Double,
    val alpha: Double,
)

/**
 * The design blurs the halo and the highlight with CSS `filter: blur(σ)`. Canvas blur is not available on
 * every Android version, so the blurred disc is drawn as a radial gradient whose stops follow the exact
 * profile of a disc convolved with a Gaussian of standard deviation σ.
 */
public object BlurProfile {
    /** Reach of the gradient beyond the disc, in standard deviations. */
    public const val REACH: Double = 3.0
    private const val STOPS = 24
    private val cache = HashMap<Double, List<GradientStop>>()

    /** Gradient radius for a disc of [radius] blurred by [sigma]. */
    public fun outerRadius(
        radius: Double,
        sigma: Double,
    ): Double = radius + REACH * sigma

    /** Stops for a disc of [radius] blurred by [sigma]; only their ratio matters. */
    @Synchronized
    public fun stops(
        radius: Double,
        sigma: Double,
    ): List<GradientStop> {
        val ratio = sigma / radius
        return cache.getOrPut(ratio) {
            val outer = outerRadius(1.0, ratio)
            (0..STOPS).map { i ->
                val offset = i.toDouble() / STOPS
                GradientStop(offset, intensity(offset * outer, ratio))
            }
        }
    }

    /**
     * Value of a unit disc convolved with a Gaussian of standard deviation [sigma], at distance [r] from the
     * centre: ∫₀¹ (ρ/σ²) exp(-(r-ρ)²/2σ²) · e^(-rρ/σ²) I₀(rρ/σ²) dρ, by Simpson's rule.
     */
    internal fun intensity(
        r: Double,
        sigma: Double,
    ): Double {
        val steps = 400
        val h = 1.0 / steps
        var sum = 0.0
        for (i in 0..steps) {
            val rho = i * h
            val weight =
                if (i == 0 || i == steps) {
                    1
                } else if (i % 2 == 1) {
                    4
                } else {
                    2
                }
            val x = r * rho / (sigma * sigma)
            sum += weight * (rho / (sigma * sigma)) * exp(-(r - rho) * (r - rho) / (2 * sigma * sigma)) * besselI0Scaled(x)
        }
        return (sum * h / 3).coerceIn(0.0, 1.0)
    }

    /** e^(-x)·I₀(x) for x ≥ 0 (Abramowitz & Stegun 9.8.1 and 9.8.2). */
    internal fun besselI0Scaled(x: Double): Double {
        val ax = abs(x)
        return if (ax < 3.75) {
            val t = (ax / 3.75) * (ax / 3.75)
            exp(-ax) *
                (1 + t * (3.5156229 + t * (3.0899424 + t * (1.2067492 + t * (0.2659732 + t * (0.0360768 + t * 0.0045813))))))
        } else {
            val t = 3.75 / ax
            (1 / sqrt(ax)) *
                (
                    0.39894228 +
                        t * (
                            0.01328592 +
                                t *
                                (
                                    0.00225319 +
                                        t *
                                        (
                                            -0.00157565 +
                                                t * (0.00916281 + t * (-0.02057706 + t * (0.02635537 + t * (-0.01647633 + t * 0.00392377))))
                                        )
                                )
                        )
                )
        }
    }
}
