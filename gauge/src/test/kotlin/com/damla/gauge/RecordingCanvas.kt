package com.damla.gauge

/** Records painter calls as short strings, for assertions about what was drawn. */
class RecordingCanvas : GaugeCanvas {
    val calls = mutableListOf<String>()

    override fun save() {
        calls += "save"
    }

    override fun saveLayer(alpha: Double) {
        calls += "layer $alpha"
    }

    override fun restore() {
        calls += "restore"
    }

    override fun translate(
        x: Double,
        y: Double,
    ) {
        calls += "translate"
    }

    override fun rotate(degrees: Double) {
        calls += "rotate $degrees"
    }

    override fun scale(
        x: Double,
        y: Double,
    ) {
        calls += "scale"
    }

    override fun clipCircle(
        cx: Double,
        cy: Double,
        radius: Double,
    ) {
        calls += "clip"
    }

    override fun fillPath(
        path: List<PathCommand>,
        color: Int,
        alpha: Double,
    ) {
        calls += "fillPath ${SvgCanvas.hex(color)}"
    }

    override fun strokePath(
        path: List<PathCommand>,
        width: Double,
        color: Int,
        alpha: Double,
    ) {
        calls += "strokePath ${SvgCanvas.hex(color)}"
    }

    override fun fillEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        color: Int,
        alpha: Double,
    ) {
        calls += "ellipse ${SvgCanvas.hex(color)} $rx"
    }

    override fun strokeEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        width: Double,
        color: Int,
    ) {
        calls += "strokeEllipse ${SvgCanvas.hex(color)}"
    }

    override fun fillRect(
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        color: Int,
        cornerRadius: Double,
    ) {
        calls += "rect ${SvgCanvas.hex(color)}"
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
        calls += "arc ${SvgCanvas.hex(color)} r=${"%.1f".format(radius)} sweep=${"%.1f".format(sweepAngle)}"
    }

    override fun fillRadialGradient(
        cx: Double,
        cy: Double,
        radius: Double,
        color: Int,
        stops: List<GradientStop>,
        alpha: Double,
    ) {
        calls += "gradient ${SvgCanvas.hex(color)} $alpha"
    }

    override fun drawText(
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        calls += "text $text"
    }

    override fun drawCenteredText(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        calls += "centeredText $text ${SvgCanvas.hex(color)} spacing=${"%.2f".format(letterSpacing)}"
    }
}
