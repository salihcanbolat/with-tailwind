package com.damla.gauge

import kotlin.math.roundToInt

/** The four shapes of the drop (docs/01). CLOSED is not a shape; the last open shape stays. */
public enum class Shape { UP, DOWN, FLAT, WAVE }

/**
 * Everything a gauge shows. The UI builds it from the engine output and never derives a state itself.
 *
 * @property shape the drop's shape (the drop part's `shapeState`).
 * @property upperRing colour of the upper ring, as the shape it stands for.
 * @property lowerRing colour of the lower ring.
 * @property closed paints everything grey (market closed and "Piyasa kapalıyken gri göster" on).
 * @property pulseTurns signed count of light turns so far: +1 for a higher price, -1 for a lower one.
 */
public data class GaugeVisual(
    val shape: Shape,
    val upperRing: Shape,
    val lowerRing: Shape,
    val closed: Boolean = false,
    val pulseTurns: Int = 0,
)

/** Colours as 0xAARRGGBB (docs/01, design/Gosterge.dc.html). */
public object GaugeColors {
    public const val UP: Int = 0xFF34C759.toInt()
    public const val DOWN: Int = 0xFFFF3B30.toInt()
    public const val FLAT: Int = 0xFFFFCC00.toInt()
    public const val WAVE: Int = 0xFFFF9500.toInt()
    public const val CLOSED: Int = 0xFFAEAEB2.toInt()
    public const val WHITE: Int = 0xFFFFFFFF.toInt()

    /** Text logos on the yellow round FLAT body. */
    public const val LOGO_ON_FLAT: Int = 0xFF5A4300.toInt()

    public fun of(shape: Shape): Int =
        when (shape) {
            Shape.UP -> UP
            Shape.DOWN -> DOWN
            Shape.FLAT -> FLAT
            Shape.WAVE -> WAVE
        }

    /** Linear interpolation per channel in sRGB, as browsers interpolate legacy colours. */
    public fun lerp(
        from: Int,
        to: Int,
        fraction: Double,
    ): Int {
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + (b - a) * fraction).roundToInt().coerceIn(0, 255) shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }
}
