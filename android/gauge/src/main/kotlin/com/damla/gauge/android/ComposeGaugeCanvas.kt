package com.damla.gauge.android

import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import com.damla.gauge.GaugeCanvas
import com.damla.gauge.GradientStop
import com.damla.gauge.PathCommand

/** [GaugeCanvas] on a Compose canvas, so the Android gauge draws exactly what the painter describes. */
class ComposeGaugeCanvas(
    private val canvas: Canvas,
) : GaugeCanvas {
    private val paths = HashMap<List<PathCommand>, Path>()

    override fun save() = canvas.save()

    override fun saveLayer(alpha: Double) = canvas.saveLayer(LAYER_BOUNDS, Paint().apply { this.alpha = alpha.toFloat() })

    override fun restore() = canvas.restore()

    override fun translate(
        x: Double,
        y: Double,
    ) = canvas.translate(x.toFloat(), y.toFloat())

    override fun rotate(degrees: Double) = canvas.rotate(degrees.toFloat())

    override fun scale(
        x: Double,
        y: Double,
    ) = canvas.scale(x.toFloat(), y.toFloat())

    override fun clipCircle(
        cx: Double,
        cy: Double,
        radius: Double,
    ) = canvas.clipPath(Path().apply { addOval(oval(cx, cy, radius, radius)) })

    override fun fillPath(
        path: List<PathCommand>,
        color: Int,
        alpha: Double,
    ) = canvas.drawPath(pathOf(path), fill(color, alpha))

    override fun strokePath(
        path: List<PathCommand>,
        width: Double,
        color: Int,
        alpha: Double,
    ) = canvas.drawPath(pathOf(path), stroke(color, alpha, width))

    override fun fillEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        color: Int,
        alpha: Double,
    ) = canvas.drawOval(oval(cx, cy, rx, ry), fill(color, alpha))

    override fun strokeEllipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        width: Double,
        color: Int,
    ) = canvas.drawOval(oval(cx, cy, rx, ry), stroke(color, 1.0, width))

    override fun fillRect(
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        color: Int,
        cornerRadius: Double,
    ) {
        val rect = Rect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat())
        if (cornerRadius > 0) {
            val r = cornerRadius.toFloat()
            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, r, r, fill(color, 1.0))
        } else {
            canvas.drawRect(rect, fill(color, 1.0))
        }
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
    ) = canvas.drawArc(oval(cx, cy, radius, radius), startAngle.toFloat(), sweepAngle.toFloat(), false, stroke(color, alpha, width))

    override fun fillRadialGradient(
        cx: Double,
        cy: Double,
        radius: Double,
        color: Int,
        stops: List<GradientStop>,
        alpha: Double,
    ) {
        val base = Color(color)
        val paint =
            Paint().apply {
                shader =
                    RadialGradientShader(
                        center = Offset(cx.toFloat(), cy.toFloat()),
                        radius = radius.toFloat(),
                        colors = stops.map { base.copy(alpha = it.alpha.toFloat()) },
                        colorStops = stops.map { it.offset.toFloat() },
                    )
                this.alpha = alpha.toFloat()
            }
        canvas.drawCircle(Offset(cx.toFloat(), cy.toFloat()), radius.toFloat(), paint)
    }

    override fun drawText(
        text: String,
        x: Double,
        baseline: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        canvas.nativeCanvas.drawText(text, x.toFloat(), baseline.toFloat(), textPaint(size, color, letterSpacing))
    }

    override fun drawCenteredText(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) {
        val paint = textPaint(size, color, letterSpacing)
        val metrics = paint.fontMetrics
        // With `line-height: 1` the line box is one em tall and centred; its baseline sits (ascent - descent) / 2
        // below the middle.
        val baseline = y.toFloat() - (metrics.ascent + metrics.descent) / 2
        canvas.nativeCanvas.drawText(text, x.toFloat(), baseline, paint)
    }

    private fun pathOf(commands: List<PathCommand>): Path =
        paths.getOrPut(commands) {
            Path().apply {
                for (command in commands) {
                    when (command) {
                        is PathCommand.MoveTo -> {
                            moveTo(command.x.toFloat(), command.y.toFloat())
                        }

                        is PathCommand.LineTo -> {
                            lineTo(command.x.toFloat(), command.y.toFloat())
                        }

                        is PathCommand.CubicTo -> {
                            cubicTo(
                                command.x1.toFloat(),
                                command.y1.toFloat(),
                                command.x2.toFloat(),
                                command.y2.toFloat(),
                                command.x.toFloat(),
                                command.y.toFloat(),
                            )
                        }

                        is PathCommand.ArcTo -> {
                            arcTo(
                                oval(command.cx, command.cy, command.rx, command.ry),
                                command.startAngle.toFloat(),
                                command.sweepAngle.toFloat(),
                                forceMoveTo = false,
                            )
                        }

                        PathCommand.Close -> {
                            close()
                        }
                    }
                }
            }
        }

    private fun fill(
        color: Int,
        alpha: Double,
    ) = Paint().apply {
        this.color = Color(color).copy(alpha = alpha.toFloat())
        isAntiAlias = true
    }

    private fun stroke(
        color: Int,
        alpha: Double,
        width: Double,
    ) = fill(color, alpha).apply {
        style = PaintingStyle.Stroke
        strokeWidth = width.toFloat()
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    }

    private fun textPaint(
        size: Double,
        color: Int,
        letterSpacing: Double,
    ) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size.toFloat()
        this.color = color
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = EXTRA_BOLD
        this.letterSpacing = (letterSpacing / size).toFloat()
    }

    private fun oval(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
    ) = Rect((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat())

    private companion object {
        /** Large enough for anything the painter draws inside a layer (drop or circle, in design units). */
        val LAYER_BOUNDS = Rect(-100f, -100f, 100f, 100f)

        /** The designs use weight 800 for logos; older Android versions only offer bold. */
        val EXTRA_BOLD: Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.DEFAULT, 800, false)
            } else {
                Typeface.DEFAULT_BOLD
            }
    }
}
