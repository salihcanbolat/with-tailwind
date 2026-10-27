package com.damla.gauge

import kotlin.math.roundToInt

/**
 * Gauge geometry in design units (docs/04, design/Gosterge.dc.html). The gauge is 76 units wide and
 * 92 high; the origin is the centre of the 76 x 76 body box, so the body spans -38..38 and the rings
 * reach down to 54. One unit is `sizePx / 76` pixels.
 */
public object Geometry {
    public const val WIDTH: Double = 76.0
    public const val HEIGHT: Double = 92.0

    /** The drop pointing up; its body circle is centred at (0, 12) with radius 22. */
    public const val DROP_PATH: String = "M0,-34 C3,-24 11.4,-12.2 19.05,1 A22 22 0 1 1 -19.05,1 C-11.4,-12.2 -3,-24 0,-34 Z"
    public const val FLAT_CIRCLE_X: Double = 0.0
    public const val FLAT_CIRCLE_Y: Double = 6.0
    public const val FLAT_CIRCLE_RADIUS: Double = 25.0

    public const val UPPER_RING_PATH: String = "M-22.9,32.8 A40 40 0 0 0 22.9,32.8"
    public const val LOWER_RING_PATH: String = "M-24,41.6 A48 48 0 0 0 24,41.6"
    public const val RING_STROKE: Double = 5.5

    /** Pivot of the drop's squash: 50% / 66% of the body box. */
    public const val DROP_SQUASH_PIVOT_Y: Double = 0.66 * WIDTH - WIDTH / 2

    /** Pivot of the circle's scale and squash: 50% / 58% of the body box. */
    public const val CIRCLE_PIVOT_Y: Double = 0.58 * WIDTH - WIDTH / 2

    /** The light arc of the pulse, drawn around the centre of the body (40 x 40 box, viewBox -20..20). */
    public const val LIGHT_ARC_PATH: String = "M-9.25,-16.02 A18.5 18.5 0 0 1 11.89,-14.17"
    public const val LIGHT_ARC_STROKE: Double = 3.0
    public const val LIGHT_ARC_ALPHA: Double = 0.66
    public const val LIGHT_DOT_X: Double = 16.77
    public const val LIGHT_DOT_Y: Double = -7.82
    public const val LIGHT_DOT_RADIUS: Double = 1.3
    public const val LIGHT_DOT_ALPHA: Double = 0.5

    /** Centre of the light arc in the drop (its body circle) and in the flat circle. */
    public const val DROP_LIGHT_Y: Double = 12.0
    public const val CIRCLE_LIGHT_Y: Double = 6.0

    /** The halo: a 60-unit disc blurred by 9 units, centred on the badge. */
    public const val HALO_DIAMETER: Double = 60.0
    public const val HALO_BLUR: Double = 9.0

    /** The highlight: an 8 x 12 ellipse whose top-left corner sits at (-12, -15) from the badge centre. */
    public const val HIGHLIGHT_LEFT: Double = -12.0
    public const val HIGHLIGHT_TOP: Double = -15.0
    public const val HIGHLIGHT_WIDTH: Double = 8.0
    public const val HIGHLIGHT_HEIGHT: Double = 12.0
    public const val HIGHLIGHT_ROTATION: Double = -24.0
    public const val HIGHLIGHT_ALPHA: Double = 0.26
    public const val HIGHLIGHT_BLUR: Double = 1.1

    /** Drop transform per shape, applied about the origin: translateY, then rotate, then scale. */
    public data class DropPose(
        val translateY: Double,
        val rotation: Double,
        val scale: Double,
    )

    public fun dropPose(shape: Shape): DropPose =
        when (shape) {
            Shape.UP -> DropPose(0.0, 0.0, 1.0)
            Shape.DOWN -> DropPose(-6.0, 180.0, 1.0)
            Shape.WAVE -> DropPose(6.0, 90.0, 1.0)
            Shape.FLAT -> DropPose(0.0, 0.0, 0.55)
        }

    /** Centre of the logo badge per shape (the middle of the drop's round part). */
    public fun badgeCentre(shape: Shape): Pair<Double, Double> =
        when (shape) {
            Shape.UP -> 0.0 to 12.0
            Shape.DOWN -> 0.0 to -18.0
            Shape.WAVE -> -12.0 to 6.0
            Shape.FLAT -> 0.0 to 6.0
        }
}

/** Pixel sizes that the design rounds or clamps (design/Gosterge.dc.html). */
public data class GaugeLayout(
    val sizePx: Double,
) {
    public val unit: Double get() = sizePx / Geometry.WIDTH

    /** `H = round(S * 92 / 76)`. */
    public val heightPx: Int get() = (sizePx * Geometry.HEIGHT / Geometry.WIDTH).roundToInt()

    /** Badge diameter: 40% of the width, at least 3 px. */
    public val badgePx: Int get() = maxOf(3, (sizePx * 0.4).roundToInt())

    /** Below 14 px the badge switches to its simple symbol (docs/01). */
    public val smallBadge: Boolean get() = badgePx < 14

    /** White ring around the badge. */
    public val badgeRingPx: Double get() = maxOf(0.6, sizePx * 0.014)

    public val highlightBlurPx: Double get() = maxOf(0.4, Geometry.HIGHLIGHT_BLUR * unit)

    /** Font size of text logos: 23% of the width for two or more characters, 30% for one. */
    public fun logoFontPx(text: String): Double = (if (text.length > 1) 0.23 else 0.3) * sizePx
}
