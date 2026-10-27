package com.damla.gauge

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A [GaugeCanvas] that writes SVG. Used to compare the painter with the prototype in a browser, and handy
 * wherever a vector image of a gauge is needed.
 */
public class SvgCanvas(
    private val width: Double,
    private val height: Double,
) : GaugeCanvas {
    private val defs = StringBuilder()
    private val body = StringBuilder()
    private val saved = ArrayDeque<Int>()
    private var openGroups = 0
    private var nextId = 0

    override fun save() {
        saved.addLast(openGroups)
        openGroups = 0
    }

    override fun saveLayer(alpha: Double) {
        save()
        group("opacity=\"${n(alpha)}\"")
    }

    override fun restore() {
        repeat(openGroups) { body.append("</g>") }
        openGroups = saved.removeLast()
    }

    override fun translate(
        x: Double,
        y: Double,
    ): Unit = group("transform=\"translate(${n(x)} ${n(y)})\"")

    override fun rotate(degrees: Double): Unit = group("transform=\"rotate(${n(degrees)})\"")

    override fun scale(
        x: Double,
        y: Double,
    ): Unit = group("transform=\"scale(${n(x)} ${n(y)})\"")

    override fun clipCircle(
        cx: Double,
        cy: Double,
        radius: Double,
    ) {
        val id = "c${nextId++}"
        defs.append("<clipPath id=\"$id\"><circle cx=\"${n(cx)}\" cy=\"${n(cy)}\" r=\"${n(radius)}\"/></clipPath>")
        group("clip-path=\"url(#$id)\"")
    }

    override fun fillPath(
        path: List<PathCommand>,
        color: Int,
        alpha: Double,
    ) {
        body.append("<path d=\"${d(path)}\" ${fill(color, alpha)}/>")
    }

    override fun strokePath(
        path: List<PathCommand>,
        width: Double,
        color: Int,
        alpha: Double,
    ) {
        body.append("<path d=\"${d(path)}\" fill=\"none\" ${stroke(color, alpha, width)}/>")
    }

    override fun fillEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        color: Int,
        alpha: Double,
    ) {
        body.append("<ellipse cx=\"${n(cx)}\" cy=\"${n(cy)}\" rx=\"${n(rx)}\" ry=\"${n(ry)}\" ${fill(color, alpha)}/>")
    }

    override fun strokeEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        width: Double,
        color: Int,
    ) {
        body.append("<ellipse cx=\"${n(cx)}\" cy=\"${n(cy)}\" rx=\"${n(rx)}\" ry=\"${n(ry)}\" fill=\"none\" ${stroke(color, 1.0, width)}/>")
    }

    override fun fillRect(
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        color: Int,
        cornerRadius: Double,
    ) {
        body.append(
            "<rect x=\"${n(
                left,
            )}\" y=\"${n(top)}\" width=\"${n(width)}\" height=\"${n(height)}\" rx=\"${n(cornerRadius)}\" ${fill(color, 1.0)}/>",
        )
    }

    override fun strokeArc(
        cx: Double,
        cy: Double,
        radius: Double,
        startAngle: Double,
        sweepAngle: Double,
        width: Double,
        color: Int,
        alpha: Double,
    ) {
        val arc = PathCommand.ArcTo(cx, cy, radius, radius, startAngle, sweepAngle)
        val start = point(cx, cy, radius, startAngle)
        strokePath(listOf(PathCommand.MoveTo(start.first, start.second), arc), width, color, alpha)
    }

    override fun fillRadialGradient(
        cx: Double,
        cy: Double,
        radius: Double,
        color: Int,
        stops: List<GradientStop>,
        alpha: Double,
    ) {
        val id = "g${nextId++}"
        defs.append("<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${n(cx)}\" cy=\"${n(cy)}\" r=\"${n(radius)}\">")
        for (stop in stops) {
            defs.append("<stop offset=\"${n(stop.offset)}\" stop-color=\"${hex(color)}\" stop-opacity=\"${n(stop.alpha)}\"/>")
        }
        defs.append("</radialGradient>")
        body.append("<circle cx=\"${n(cx)}\" cy=\"${n(cy)}\" r=\"${n(radius)}\" fill=\"url(#$id)\" opacity=\"${n(alpha)}\"/>")
    }

    override fun drawText(
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        body.append(text(text, x, baseline, size, color, letterSpacing, central = false))
    }

    override fun drawCenteredText(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        body.append(text(text, x, y, size, color, letterSpacing, central = true))
    }

    public fun toSvg(): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${n(width)}\" height=\"${n(height)}\" " +
            "viewBox=\"0 0 ${n(width)} ${n(height)}\" overflow=\"visible\"><defs>$defs</defs>$body</svg>"

    private fun group(attributes: String) {
        body.append("<g $attributes>")
        openGroups++
    }

    private fun text(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
        central: Boolean,
    ): String {
        val baseline = if (central) " dominant-baseline=\"central\"" else ""
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;")
        return "<text x=\"${n(x)}\" y=\"${n(y)}\" text-anchor=\"middle\"$baseline font-size=\"${n(size)}\" font-weight=\"800\" " +
            "letter-spacing=\"${n(
                letterSpacing,
            )}\" font-family=\"-apple-system, system-ui, sans-serif\" ${fill(color, 1.0)}>$escaped</text>"
    }

    private fun fill(
        color: Int,
        alpha: Double,
    ) = "fill=\"${hex(color)}\"" + if (alpha < 1) " fill-opacity=\"${n(alpha)}\"" else ""

    private fun stroke(
        color: Int,
        alpha: Double,
        width: Double,
    ) = "stroke=\"${hex(color)}\" stroke-width=\"${n(width)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"" +
        if (alpha < 1) " stroke-opacity=\"${n(alpha)}\"" else ""

    public companion object {
        internal fun n(value: Double): String {
            val rounded = kotlin.math.round(value * 10_000) / 10_000
            return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
        }

        internal fun hex(color: Int): String = "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0')

        private fun point(
            cx: Double,
            cy: Double,
            radius: Double,
            degrees: Double,
        ): Pair<Double, Double> {
            val radians = degrees * PI / 180
            return cx + radius * cos(radians) to cy + radius * sin(radians)
        }

        /** SVG path data of commands, turning centre-form arcs back into endpoint arcs. */
        internal fun d(path: List<PathCommand>): String =
            path.joinToString(" ") { command ->
                when (command) {
                    is PathCommand.MoveTo -> {
                        "M${n(command.x)},${n(command.y)}"
                    }

                    is PathCommand.LineTo -> {
                        "L${n(command.x)},${n(command.y)}"
                    }

                    is PathCommand.CubicTo -> {
                        "C${n(command.x1)},${n(command.y1)} ${n(command.x2)},${n(command.y2)} ${n(command.x)},${n(command.y)}"
                    }

                    is PathCommand.ArcTo -> {
                        val (x, y) = PathData.end(command)
                        val large = if (abs(command.sweepAngle) > 180) 1 else 0
                        val sweep = if (command.sweepAngle > 0) 1 else 0
                        "A${n(command.rx)} ${n(command.ry)} 0 $large $sweep ${n(x)},${n(y)}"
                    }

                    PathCommand.Close -> {
                        "Z"
                    }
                }
            }
    }
}
