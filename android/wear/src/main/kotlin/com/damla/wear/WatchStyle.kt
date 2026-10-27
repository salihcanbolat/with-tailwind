package com.damla.wear

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import com.damla.gauge.Shape

/** The watch designs' colours and curves (design/Saat.dc.html). */
object WatchStyle {
    /** State words and bars: the bright tones of docs/01; grey while the market is closed. */
    fun bright(
        shape: Shape,
        closed: Boolean,
    ): Color =
        when {
            closed -> Color(0xFFAEAEB2)
            shape == Shape.UP -> Color(0xFF30D158)
            shape == Shape.DOWN -> Color(0xFFFF453A)
            shape == Shape.FLAT -> Color(0xFFFFD60A)
            else -> Color(0xFFFF9F0A)
        }

    /** Price and percent alarms (the design's Bitcoin alarm). */
    val accent = Color(0xFF0A84FF)
    val button = Color(0xFF1F2329)
    val text60 = Color(0x99FFFFFF)
    val text55 = Color(0x8CFFFFFF)
    val dotOff = Color(0x4DFFFFFF)

    val standard = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** The design is drawn on a 300 px round screen; everything scales with the real screen. */
    const val DESIGN_SIZE = 300f
}
