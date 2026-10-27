package com.damla.gauge.android

/**
 * Android's blur APIs (BlurMaskFilter, RenderEffect, shadow layers) take a radius that Skia turns into a
 * Gaussian standard deviation as `0.57735 * radius + 0.5`. CSS speaks in standard deviations: `filter: blur(b)`
 * and `drop-shadow(x y b)` blur with sigma b (measured in Chromium), `box-shadow` and `text-shadow` with b / 2.
 */
object CssBlur {
    /** The Android radius that blurs with standard deviation [sigmaPx]. */
    fun radiusFor(sigmaPx: Float): Float = if (sigmaPx <= MIN_SIGMA) 0f else (sigmaPx - MIN_SIGMA) / SCALE

    /** For `filter: blur()` and `drop-shadow()`. */
    fun filterRadius(blurPx: Float): Float = radiusFor(blurPx)

    /** For `box-shadow` and `text-shadow`. */
    fun shadowRadius(blurPx: Float): Float = radiusFor(blurPx / 2)

    private const val SCALE = 0.57735f
    private const val MIN_SIGMA = 0.5f
}
