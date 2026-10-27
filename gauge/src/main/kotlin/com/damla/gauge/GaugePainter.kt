package com.damla.gauge

/**
 * Draws one [GaugeFrame] the way design/Gosterge.dc.html stacks its layers: halo, rings, drop, flat circle,
 * then the logo with its highlight. Coordinates follow [Geometry]; the canvas origin is the gauge's top-left
 * corner in pixels.
 */
public object GaugePainter {
    private val dropPath = PathData.parse(Geometry.DROP_PATH)
    private val upperRing = arcOf(Geometry.UPPER_RING_PATH)
    private val lowerRing = arcOf(Geometry.LOWER_RING_PATH)
    private val lightArc = arcOf(Geometry.LIGHT_ARC_PATH)

    public fun paint(
        frame: GaugeFrame,
        layout: GaugeLayout,
        logo: Logo?,
        canvas: GaugeCanvas,
    ) {
        canvas.save()
        canvas.scale(layout.unit, layout.unit)
        canvas.translate(Geometry.WIDTH / 2, Geometry.WIDTH / 2)
        halo(frame, canvas)
        rings(frame, layout, canvas)
        if (frame.dropAlpha > 0) drop(frame, canvas)
        if (frame.circleAlpha > 0) circle(frame, canvas)
        logo(frame, layout, logo, canvas)
        canvas.restore()
    }

    private fun halo(
        frame: GaugeFrame,
        canvas: GaugeCanvas,
    ) {
        if (frame.haloAlpha <= 0) return
        val radius = Geometry.HALO_DIAMETER / 2
        canvas.save()
        canvas.translate(frame.haloX, frame.haloY)
        canvas.scale(frame.haloScale, frame.haloScale)
        canvas.fillRadialGradient(
            cx = 0.0,
            cy = 0.0,
            radius = BlurProfile.outerRadius(radius, Geometry.HALO_BLUR),
            color = frame.haloColor,
            stops = BlurProfile.stops(radius, Geometry.HALO_BLUR),
            alpha = frame.haloAlpha,
        )
        canvas.restore()
    }

    /**
     * The rings live in their own S x H box with a 76 x 92 view box. H is rounded to whole pixels, so like
     * SVG's default `xMidYMid meet` they use the smaller of the two scales and are centred in the box.
     */
    private fun rings(
        frame: GaugeFrame,
        layout: GaugeLayout,
        canvas: GaugeCanvas,
    ) {
        val unit = layout.unit
        val scale = minOf(unit, layout.heightPx / Geometry.HEIGHT)
        canvas.save()
        canvas.translate(-Geometry.WIDTH / 2, -Geometry.WIDTH / 2)
        canvas.scale(1 / unit, 1 / unit)
        canvas.translate((layout.sizePx - Geometry.WIDTH * scale) / 2, (layout.heightPx - Geometry.HEIGHT * scale) / 2)
        canvas.scale(scale, scale)
        canvas.translate(Geometry.WIDTH / 2, Geometry.WIDTH / 2)
        ring(upperRing, frame.upperRingColor, frame.upperRingDashOffset, canvas)
        ring(lowerRing, frame.lowerRingColor, frame.lowerRingDashOffset, canvas)
        canvas.restore()
    }

    /** The ring path has `pathLength=100` and `stroke-dasharray: 100`, so the offset hides its far end. */
    private fun ring(
        arc: PathCommand.ArcTo,
        color: Int,
        dashOffset: Double,
        canvas: GaugeCanvas,
    ) {
        val visible = ((100 - dashOffset) / 100).coerceIn(0.0, 1.0)
        if (visible > 0) {
            canvas.strokeArc(arc.cx, arc.cy, arc.rx, arc.startAngle, arc.sweepAngle * visible, Geometry.RING_STROKE, color)
        } else {
            // Fully offset, the dash pattern starts a zero-length dash exactly at the end of the path, and its
            // round caps leave a dot there while the redraw waits for its delay.
            val (x, y) = PathData.end(arc)
            val r = Geometry.RING_STROKE / 2
            canvas.fillEllipse(x, y, r, r, color)
        }
    }

    private fun drop(
        frame: GaugeFrame,
        canvas: GaugeCanvas,
    ) {
        canvas.save()
        canvas.translate(0.0, frame.dropTranslateY)
        canvas.rotate(frame.dropRotation)
        canvas.scale(frame.dropScale, frame.dropScale)
        layer(frame.dropAlpha, canvas)
        squash(frame, Geometry.DROP_SQUASH_PIVOT_Y, canvas) { canvas.fillPath(dropPath, frame.bodyColor) }
        light(Geometry.DROP_LIGHT_Y, frame.lightRotation, canvas)
        canvas.restore()
        canvas.restore()
    }

    private fun circle(
        frame: GaugeFrame,
        canvas: GaugeCanvas,
    ) {
        canvas.save()
        canvas.translate(0.0, Geometry.CIRCLE_PIVOT_Y)
        canvas.scale(frame.circleScale, frame.circleScale)
        canvas.translate(0.0, -Geometry.CIRCLE_PIVOT_Y)
        layer(frame.circleAlpha, canvas)
        squash(frame, Geometry.CIRCLE_PIVOT_Y, canvas) {
            val r = Geometry.FLAT_CIRCLE_RADIUS
            canvas.fillEllipse(Geometry.FLAT_CIRCLE_X, Geometry.FLAT_CIRCLE_Y, r, r, frame.bodyColor)
        }
        light(Geometry.CIRCLE_LIGHT_Y, frame.lightRotation, canvas)
        canvas.restore()
        canvas.restore()
    }

    private fun layer(
        alpha: Double,
        canvas: GaugeCanvas,
    ) = if (alpha < 1) canvas.saveLayer(alpha) else canvas.save()

    private inline fun squash(
        frame: GaugeFrame,
        pivotY: Double,
        canvas: GaugeCanvas,
        draw: () -> Unit,
    ) {
        canvas.save()
        canvas.translate(0.0, pivotY)
        canvas.scale(frame.squashX, frame.squashY)
        canvas.translate(0.0, -pivotY)
        draw()
        canvas.restore()
    }

    /** The pulse light: an arc and a dot turning around the centre of the body. Not squashed. */
    private fun light(
        centreY: Double,
        rotation: Double,
        canvas: GaugeCanvas,
    ) {
        canvas.save()
        canvas.translate(0.0, centreY)
        canvas.rotate(rotation)
        canvas.strokeArc(
            lightArc.cx,
            lightArc.cy,
            lightArc.rx,
            lightArc.startAngle,
            lightArc.sweepAngle,
            Geometry.LIGHT_ARC_STROKE,
            GaugeColors.WHITE,
            Geometry.LIGHT_ARC_ALPHA,
        )
        val dot = Geometry.LIGHT_DOT_RADIUS
        canvas.fillEllipse(Geometry.LIGHT_DOT_X, Geometry.LIGHT_DOT_Y, dot, dot, GaugeColors.WHITE, Geometry.LIGHT_DOT_ALPHA)
        canvas.restore()
    }

    private fun logo(
        frame: GaugeFrame,
        layout: GaugeLayout,
        logo: Logo?,
        canvas: GaugeCanvas,
    ) {
        canvas.save()
        canvas.translate(frame.logoX, frame.logoY)
        when (logo) {
            null -> {}

            is Logo.Text -> {
                val size = layout.logoFontPx(logo.text) / layout.unit
                val spacing = if (logo.text.length > 1) -0.04 * size else 0.0
                canvas.drawCenteredText(logo.text, 0.0, 0.0, size, frame.logoTextColor, spacing)
            }

            is Logo.Badge -> {
                Badges.draw(logo.kind, layout, canvas)
            }
        }
        // The highlight comes first in the markup, but it is absolutely positioned, so CSS paints it above
        // the badge and the text.
        highlight(frame, layout, canvas)
        canvas.restore()
    }

    /** The soft reflection: never rotates with the drop, so the light always comes from above. */
    private fun highlight(
        frame: GaugeFrame,
        layout: GaugeLayout,
        canvas: GaugeCanvas,
    ) {
        val rx = Geometry.HIGHLIGHT_WIDTH / 2
        val sigma = layout.highlightBlurPx / layout.unit
        canvas.save()
        canvas.translate(Geometry.HIGHLIGHT_LEFT + rx + frame.glintX, Geometry.HIGHLIGHT_TOP + Geometry.HIGHLIGHT_HEIGHT / 2 + frame.glintY)
        canvas.rotate(Geometry.HIGHLIGHT_ROTATION)
        canvas.scale(1.0, Geometry.HIGHLIGHT_HEIGHT / Geometry.HIGHLIGHT_WIDTH)
        canvas.fillRadialGradient(
            0.0,
            0.0,
            BlurProfile.outerRadius(rx, sigma),
            GaugeColors.WHITE,
            BlurProfile.stops(rx, sigma),
            frame.glintAlpha,
        )
        canvas.restore()
    }

    private fun arcOf(data: String): PathCommand.ArcTo = PathData.parse(data).filterIsInstance<PathCommand.ArcTo>().single()
}
