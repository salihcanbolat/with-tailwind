@file:OptIn(ExperimentalJsExport::class)

package com.damla.gauge.js

import com.damla.gauge.GaugeLayout
import com.damla.gauge.GaugeMotion
import com.damla.gauge.GaugePainter
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Logo
import com.damla.gauge.Shape
import com.damla.gauge.SvgCanvas

/**
 * One gauge for the desktop: the motion model and painter of :gauge behind a small JavaScript API. States
 * come from the backend as engine names ("UP", "DOWN", "FLAT", "WAVE"); the desktop never derives them.
 */
@JsExport
public class DesktopGauge(
    shape: String,
    upperRing: String,
    lowerRing: String,
    closed: Boolean,
    pulseTurns: Int,
    reduceMotion: Boolean,
) {
    private val motion = GaugeMotion(visual(shape, upperRing, lowerRing, closed, pulseTurns), reduceMotion = reduceMotion)

    /**
     * What the gauge shows from [atMs] on: the drop morphs and the rings recolour as on the phone (docs/04);
     * a higher [pulseTurns] turns the light once more (+1 for a higher price, -1 for a lower one).
     */
    public fun update(
        shape: String,
        upperRing: String,
        lowerRing: String,
        closed: Boolean,
        pulseTurns: Int,
        atMs: Double,
    ) {
        motion.update(visual(shape, upperRing, lowerRing, closed, pulseTurns), atMs)
    }

    /** True once nothing moves any more, so the page can stop drawing frames. */
    public fun settled(atMs: Double): Boolean = motion.settledAt() <= atMs

    /** The frame at [atMs] as an SVG document [sizePx] wide; [badge] is the catalogue's badge id or "". */
    public fun svg(
        atMs: Double,
        sizePx: Double,
        badge: String,
    ): String {
        val layout = GaugeLayout(sizePx)
        val canvas = SvgCanvas(sizePx, layout.heightPx.toDouble())
        GaugePainter.paint(motion.frame(atMs), layout, if (badge.isEmpty()) null else Logo.forBadge(badge), canvas)
        return canvas.toSvg()
    }
}

private fun visual(
    shape: String,
    upperRing: String,
    lowerRing: String,
    closed: Boolean,
    pulseTurns: Int,
) = GaugeVisual(Shape.valueOf(shape), Shape.valueOf(upperRing), Shape.valueOf(lowerRing), closed, pulseTurns)

/** The design's height for a gauge [sizePx] wide (92 / 76 of the width). */
@JsExport
public fun gaugeHeight(sizePx: Double): Int = GaugeLayout(sizePx).heightPx
