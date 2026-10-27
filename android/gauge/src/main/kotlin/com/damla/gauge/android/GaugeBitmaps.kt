package com.damla.gauge.android

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.damla.gauge.GaugeLayout
import com.damla.gauge.GaugeMotion
import com.damla.gauge.GaugePainter
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Logo
import kotlin.math.ceil

/** Still images of a gauge at rest, for widgets, notifications and watches (docs/04, docs/06). */
object GaugeBitmaps {
    /** A [sizePx] wide gauge; the height follows the design (92 / 76 of the width). */
    fun render(
        visual: GaugeVisual,
        sizePx: Int,
        logo: Logo?,
    ): Bitmap {
        val layout = GaugeLayout(sizePx.toDouble())
        val image = ImageBitmap(sizePx, layout.heightPx)
        GaugePainter.paint(GaugeMotion(visual).frame(0.0), layout, logo, ComposeGaugeCanvas(Canvas(image)))
        return image.asAndroidBitmap()
    }

    /**
     * The gauge with the soft drop shadow it has on the wallpaper (design/Prototip.dc.html:
     * `drop-shadow(0 2px 3px rgba(0,0,0,0.35))`). The image grows by [margin] on every side so the
     * shadow fits; [density] converts the design's pixels.
     */
    fun renderWithShadow(
        visual: GaugeVisual,
        sizePx: Int,
        logo: Logo?,
        density: Float,
    ): ShadowedBitmap {
        val gauge = render(visual, sizePx, logo)
        val blur = SHADOW_BLUR * density
        val offset = SHADOW_OFFSET_Y * density
        val margin = ceil(blur * 2 + offset).toInt()
        val result = Bitmap.createBitmap(gauge.width + 2 * margin, gauge.height + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val shadowOffset = IntArray(2)
        val alpha = gauge.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }, shadowOffset)
        val shadowPaint = Paint().apply { color = android.graphics.Color.argb(SHADOW_ALPHA, 0, 0, 0) }
        canvas.drawBitmap(alpha, margin + shadowOffset[0].toFloat(), margin + shadowOffset[1] + offset, shadowPaint)
        canvas.drawBitmap(gauge, margin.toFloat(), margin.toFloat(), null)
        alpha.recycle()
        gauge.recycle()
        return ShadowedBitmap(result, margin)
    }

    class ShadowedBitmap(
        val bitmap: Bitmap,
        val marginPx: Int,
    )

    private const val SHADOW_BLUR = 3f
    private const val SHADOW_OFFSET_Y = 2f
    private const val SHADOW_ALPHA = 89 // 0.35 * 255
}
