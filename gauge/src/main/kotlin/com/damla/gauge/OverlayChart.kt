package com.damla.gauge

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The detail scene's price chart (design/Prototip.dc.html): always [OverlayScene.CHART_POINTS] points across
 * [OverlayScene.CHART_WIDTH] x [OverlayScene.CHART_HEIGHT], so a new range or asset morphs point by point.
 */
public object OverlayChart {
    private const val POINTS = OverlayScene.CHART_POINTS
    private const val WIDTH = OverlayScene.CHART_WIDTH
    private const val HEIGHT = OverlayScene.CHART_HEIGHT

    /** The prototype's lines keep between 6% and 94% of the height. */
    private const val TOP = 0.06
    private const val BOTTOM = 0.94

    /** A flat line in the middle, until prices arrive. */
    public val flat: List<Double> = List(POINTS) { HEIGHT / 2 }

    /**
     * y values (0 at the top) for [prices] in time order: resampled to [OverlayScene.CHART_POINTS] points and
     * stretched so the lowest price sits at 94% of the height and the highest at 6%.
     */
    public fun points(prices: List<Double>): List<Double> {
        if (prices.size < 2) return flat
        val resampled =
            List(POINTS) { k ->
                val at = k * (prices.size - 1) / (POINTS - 1).toDouble()
                val i = minOf(at.toInt(), prices.size - 2)
                prices[i] + (prices[i + 1] - prices[i]) * (at - i)
            }
        val low = resampled.min()
        val high = resampled.max()
        if (high <= low) return flat
        return resampled.map { (BOTTOM - (BOTTOM - TOP) * (it - low) / (high - low)) * HEIGHT }
    }

    public fun x(k: Int): Double = k * WIDTH / (POINTS - 1)

    /** One cubic segment of the line: two control points and the end point. */
    public data class Segment(
        val c1x: Double,
        val c1y: Double,
        val c2x: Double,
        val c2y: Double,
        val x: Double,
        val y: Double,
    )

    /**
     * The prototype's smooth line through [ys]: a Catmull-Rom curve as cubic segments, every number rounded to
     * 0.1 like its SVG path. The line starts at `(0, ys[0])`.
     */
    public fun segments(ys: List<Double>): List<Segment> {
        val p = ys.mapIndexed { k, y -> x(k) to y }
        return (0 until p.size - 1).map { i ->
            val p0 = if (i == 0) p[i] else p[i - 1]
            val p1 = p[i]
            val p2 = p[i + 1]
            val p3 = if (i + 2 < p.size) p[i + 2] else p2
            Segment(
                round1(p1.first + (p2.first - p0.first) / 6),
                round1(p1.second + (p2.second - p0.second) / 6),
                round1(p2.first - (p3.first - p1.first) / 6),
                round1(p2.second - (p3.second - p1.second) / 6),
                round1(p2.first),
                round1(p2.second),
            )
        }
    }

    /** The scrub dot's height at [x]: straight between the points, as in the prototype. */
    public fun yAt(
        ys: List<Double>,
        x: Double,
    ): Double {
        val at = (x / WIDTH).coerceIn(0.0, 1.0) * (ys.size - 1)
        val i = minOf(ys.size - 2, at.toInt())
        return ys[i] + (ys[i + 1] - ys[i]) * (at - i)
    }

    /** Which of [count] history points lies under [x]. */
    public fun indexAt(
        x: Double,
        count: Int,
    ): Int = ((x / WIDTH).coerceIn(0.0, 1.0) * (count - 1) + 0.5).toInt()

    public fun round1(value: Double): Double = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
}
