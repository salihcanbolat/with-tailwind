package com.damla.gauge

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A path command in absolute coordinates. SVG arcs are converted to centre form, which canvases take. */
public sealed interface PathCommand {
    public data class MoveTo(
        val x: Double,
        val y: Double,
    ) : PathCommand

    public data class LineTo(
        val x: Double,
        val y: Double,
    ) : PathCommand

    public data class CubicTo(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val x: Double,
        val y: Double,
    ) : PathCommand

    /**
     * An elliptical arc from the current point, in centre form. Angles are degrees, clockwise from the
     * positive x axis on a y-down canvas; a positive sweep is clockwise.
     */
    public data class ArcTo(
        val cx: Double,
        val cy: Double,
        val rx: Double,
        val ry: Double,
        val startAngle: Double,
        val sweepAngle: Double,
    ) : PathCommand

    public data object Close : PathCommand
}

/** Parses the absolute SVG path syntax used by the design files: M, L, C, A and Z. */
public object PathData {
    private val token = Regex("""[MLCAZ]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?""")

    public fun parse(data: String): List<PathCommand> {
        val tokens = token.findAll(data).map { it.value }.toList()
        val commands = ArrayList<PathCommand>()
        var i = 0
        var x = 0.0
        var y = 0.0

        fun number() = tokens[i++].toDouble()
        while (i < tokens.size) {
            when (val op = tokens[i++]) {
                "M" -> {
                    x = number()
                    y = number()
                    commands += PathCommand.MoveTo(x, y)
                }

                "L" -> {
                    x = number()
                    y = number()
                    commands += PathCommand.LineTo(x, y)
                }

                "C" -> {
                    val c = PathCommand.CubicTo(number(), number(), number(), number(), number(), number())
                    x = c.x
                    y = c.y
                    commands += c
                }

                "A" -> {
                    val rx = number()
                    val ry = number()
                    val rotation = number()
                    val largeArc = number() != 0.0
                    val sweep = number() != 0.0
                    val endX = number()
                    val endY = number()
                    require(rotation == 0.0) { "Rotated arcs are not used by the designs" }
                    commands += arc(x, y, rx, ry, largeArc, sweep, endX, endY)
                    x = endX
                    y = endY
                }

                "Z" -> {
                    commands += PathCommand.Close
                }

                else -> {
                    error("Unsupported path token '$op'")
                }
            }
        }
        return commands
    }

    /** SVG endpoint to centre parameterisation (SVG 1.1, appendix F.6.5) for an unrotated ellipse. */
    internal fun arc(
        x1: Double,
        y1: Double,
        radiusX: Double,
        radiusY: Double,
        largeArc: Boolean,
        sweep: Boolean,
        x2: Double,
        y2: Double,
    ): PathCommand.ArcTo {
        val dx = (x1 - x2) / 2
        val dy = (y1 - y2) / 2
        var rx = abs(radiusX)
        var ry = abs(radiusY)
        // Radii too small to reach the end point are scaled up (F.6.6).
        val lambda = dx * dx / (rx * rx) + dy * dy / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val numerator = rx * rx * ry * ry - rx * rx * dy * dy - ry * ry * dx * dx
        val denominator = rx * rx * dy * dy + ry * ry * dx * dx
        val sign = if (largeArc == sweep) -1 else 1
        val factor = sign * sqrt(maxOf(0.0, numerator / denominator))
        val cxPrime = factor * rx * dy / ry
        val cyPrime = -factor * ry * dx / rx
        val cx = cxPrime + (x1 + x2) / 2
        val cy = cyPrime + (y1 + y2) / 2
        val start = angle(1.0, 0.0, (dx - cxPrime) / rx, (dy - cyPrime) / ry)
        var delta = angle((dx - cxPrime) / rx, (dy - cyPrime) / ry, (-dx - cxPrime) / rx, (-dy - cyPrime) / ry)
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        return PathCommand.ArcTo(cx, cy, rx, ry, start * 180 / PI, delta * 180 / PI)
    }

    private fun angle(
        ux: Double,
        uy: Double,
        vx: Double,
        vy: Double,
    ): Double {
        val cosine = (ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))
        val value = acos(cosine.coerceIn(-1.0, 1.0))
        return if (ux * vy - uy * vx < 0) -value else value
    }

    /** End point of an arc command, for tests and for canvases that need it. */
    public fun end(arc: PathCommand.ArcTo): Pair<Double, Double> {
        val radians = (arc.startAngle + arc.sweepAngle) * PI / 180
        return arc.cx + arc.rx * cos(radians) to arc.cy + arc.ry * sin(radians)
    }
}
