package com.damla.gauge

/**
 * The drawing operations [GaugePainter] needs. Android implements it on a Compose `DrawScope`; tests and
 * the reference comparison implement it as SVG. Transforms apply to everything drawn after them until the
 * matching [restore]; angles are degrees, clockwise on a y-down canvas.
 */
public interface GaugeCanvas {
    public fun save()

    /** Like [save], but everything until the matching [restore] is composited with [alpha] as one layer. */
    public fun saveLayer(alpha: Double)

    public fun restore()

    public fun translate(
        x: Double,
        y: Double,
    )

    public fun rotate(degrees: Double)

    public fun scale(
        x: Double,
        y: Double,
    )

    public fun clipCircle(
        cx: Double,
        cy: Double,
        radius: Double,
    )

    public fun fillPath(
        path: List<PathCommand>,
        color: Int,
        alpha: Double = 1.0,
    )

    /** Strokes with round caps and joins. */
    public fun strokePath(
        path: List<PathCommand>,
        width: Double,
        color: Int,
        alpha: Double = 1.0,
    )

    public fun fillEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        color: Int,
        alpha: Double = 1.0,
    )

    public fun strokeEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        width: Double,
        color: Int,
    )

    public fun fillRect(
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        color: Int,
        cornerRadius: Double = 0.0,
    )

    /** An arc of a circle stroked with round caps. */
    public fun strokeArc(
        cx: Double,
        cy: Double,
        radius: Double,
        startAngle: Double,
        sweepAngle: Double,
        width: Double,
        color: Int,
        alpha: Double = 1.0,
    )

    /** A disc of [radius] filled with [color] whose opacity follows [stops] from the centre outwards. */
    public fun fillRadialGradient(
        cx: Double,
        cy: Double,
        radius: Double,
        color: Int,
        stops: List<GradientStop>,
        alpha: Double,
    )

    /** Text centred horizontally on [x] with its baseline on [baseline]. */
    public fun drawText(
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        color: Int,
        letterSpacing: Double = 0.0,
    )

    /** Text centred on ([x], [y]) using its line box, like a one-line flex item with `line-height: 1`. */
    public fun drawCenteredText(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    )
}
