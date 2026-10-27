package com.damla.app.overlay

import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damla.gauge.Shape
import com.damla.gauge.android.CssBlur

/** Colours of design/Prototip.dc.html and docs/01 used by the overlay. */
internal object OverlayColors {
    /** Text on dark backgrounds: docs/01's bright tones (docs/kararlar.md, "Metin renkleri"). */
    fun bright(
        shape: Shape,
        closed: Boolean,
    ): Color =
        when {
            closed -> CLOSED
            shape == Shape.UP -> Color(0xFF30D158)
            shape == Shape.DOWN -> Color(0xFFFF453A)
            shape == Shape.FLAT -> Color(0xFFFFD60A)
            else -> Color(0xFFFF9F0A)
        }

    /** Text on the light glass folder: the prototype's dark tones (`wcol`). */
    fun onLight(
        shape: Shape,
        closed: Boolean,
    ): Color =
        when {
            closed -> Color(0xFF8E8E93)
            shape == Shape.UP -> Color(0xFF1E7B34)
            shape == Shape.DOWN -> Color(0xFFC4271C)
            shape == Shape.FLAT -> Color(0xFF7A5F00)
            else -> Color(0xFFB25E00)
        }

    /** The chart's line (the prototype's `mainCol`; FLAT is #E0B000 there, not the drop's #FFCC00). */
    fun chart(
        shape: Shape,
        closed: Boolean,
    ): Color =
        when {
            closed -> CLOSED
            shape == Shape.UP -> Color(0xFF34C759)
            shape == Shape.DOWN -> Color(0xFFFF3B30)
            shape == Shape.FLAT -> Color(0xFFE0B000)
            else -> Color(0xFFFF9500)
        }

    val CLOSED = Color(0xFFAEAEB2)
    val DARK_TEXT = Color(0xFF1C1C1E)
    val ICON_ON_LIGHT = Color(0xFF3C3C43)
    val LIVE = Color(0xFF34C759)
    val LIVE_ON_DARK = Color(0xFF30D158)
    val SHARE_CARD = Color(0xFF0E141B)
    val SHARE_TEXT = Color(0xFFF2F4F7)
    val SHARE_BUTTON_TEXT = Color(0xFF0B0F14)
}

/** Text sizes of the design are px; the overlay is a fixed layout, so they stay dp-sized whatever the font scale. */
@Composable
internal fun designText(
    size: Float,
    weight: FontWeight,
    color: Color,
    lineHeight: Float? = null,
    letterSpacing: Float = 0f,
    shadow: CssShadow? = null,
    tabular: Boolean = false,
): TextStyle {
    val density = LocalDensity.current
    return with(density) {
        TextStyle(
            color = color,
            fontSize = size.dp.toSp(),
            fontWeight = weight,
            lineHeight = lineHeight?.dp?.toSp() ?: TextStyle.Default.lineHeight,
            letterSpacing = if (letterSpacing == 0f) 0.sp else letterSpacing.dp.toSp(),
            shadow = shadow?.toShadow(density),
            fontFeatureSettings = if (tabular) "tnum" else null,
        )
    }
}

/** A CSS `text-shadow: 0 <y>px <blur>px <color>`. */
internal data class CssShadow(
    val y: Float,
    val blur: Float,
    val color: Color,
) {
    fun toShadow(density: Density): Shadow =
        with(density) {
            Shadow(color, Offset(0f, y.dp.toPx()), CssBlur.shadowRadius(blur.dp.toPx()))
        }
}

/**
 * CSS `filter: blur(b) drop-shadow(0 y s color)` as a render effect (Android 12+; older versions draw
 * without them). Sizes in dp.
 */
internal fun Density.cssFilter(
    blur: Double,
    shadowAlpha: Double,
    shadowY: Float = 2f,
    shadowBlur: Float = 3f,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val blurRadius = CssBlur.filterRadius(blur.toFloat().dp.toPx())
    val blurred = if (blurRadius > 0) RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.DECAL) else null
    if (shadowAlpha <= 0) return blurred?.asComposeRenderEffect()
    val source = blurred ?: RenderEffect.createOffsetEffect(0f, 0f)
    val tint =
        android.graphics.BlendModeColorFilter(
            Color.Black.copy(alpha = shadowAlpha.toFloat()).toArgb(),
            android.graphics.BlendMode.SRC_IN,
        )
    val shadowRadius = CssBlur.filterRadius(shadowBlur.dp.toPx())
    val tinted = RenderEffect.createColorFilterEffect(tint, source)
    val soft = if (shadowRadius > 0) RenderEffect.createBlurEffect(shadowRadius, shadowRadius, tinted, Shader.TileMode.DECAL) else tinted
    val shadow = RenderEffect.createOffsetEffect(0f, shadowY.dp.toPx(), soft)
    return RenderEffect.createBlendModeEffect(shadow, source, android.graphics.BlendMode.SRC_OVER).asComposeRenderEffect()
}

/** A CSS `box-shadow: 0 <y> <blur> <color>` behind a rounded rectangle, drawn only outside it (sizes in px). */
internal fun DrawScope.boxShadow(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    y: Float,
    blur: Float,
    color: Color,
) {
    if (color.alpha <= 0f) return
    val outside =
        Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry
                    .RoundRect(left, top, left + width, top + height, radius, radius),
            )
        }
    val blurRadius = CssBlur.shadowRadius(blur)
    drawContext.canvas.save()
    drawContext.canvas.clipPath(outside, androidx.compose.ui.graphics.ClipOp.Difference)
    val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            if (blurRadius > 0) maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
    drawContext.canvas.nativeCanvas.drawRoundRect(left, top + y, left + width, top + y + height, radius, radius, paint)
    drawContext.canvas.restore()
}

/** The design's buttons shrink to 92% while pressed (`.dmBas:active`, 180 ms springy curve). */
@Composable
internal fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(180, easing = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The live dot's ring (`dmCanli`, 2 s, ease-out): a ring of [color] grows 6 px from the dot and fades in the
 * first 70% of the cycle. Still with reduced motion.
 */
@Composable
internal fun LiveDot(
    diameter: Float,
    color: Color,
    reduceMotion: Boolean,
) {
    val progress =
        if (reduceMotion) {
            1f
        } else {
            val transition = rememberInfiniteTransition(label = "live")
            val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "live")
            value
        }
    Canvas(Modifier.size(diameter.dp)) {
        val r = size.minDimension / 2
        if (progress < 0.7f) {
            val eased = EASE_OUT.transform(progress / 0.7f)
            drawCircle(RING.copy(alpha = 0.55f * (1 - eased)), radius = r + 6.dp.toPx() * eased)
        }
        drawCircle(color, radius = r)
    }
}

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val RING = Color(0xFF34C759)

/** The design's line icons: SVG paths in a 24 x 24 box, stroked with round ends. */
internal enum class OverlayIcon(
    val paths: List<String>,
) {
    BELL(listOf("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")),
    OPEN(listOf("M7 17L17 7M9 7h8v8")),
    SHARE(listOf("M12 15V3M7 8l5-5 5 5", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7")),
    SLIDERS(listOf("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")),
    COLLAPSE_RIGHT(listOf("M6 6l6 6-6 6M13 6l6 6-6 6")),
    COLLAPSE_LEFT(listOf("M18 6l-6 6 6 6M11 6l-6 6 6 6")),
    ;

    val parsed: List<Path> by lazy { paths.map { PathParser().parsePathString(it).toPath() } }
}

@Composable
internal fun Icon(
    icon: OverlayIcon,
    size: Float,
    stroke: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val paths = remember(icon) { icon.parsed }
    Canvas(modifier.size(size.dp)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            for (path in paths) drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The filled drop of the share card's signature. */
internal val DROP_MARK: Path by lazy {
    PathParser().parsePathString("M12 2.5c3.2 4.3 6.5 8 6.5 11.7a6.5 6.5 0 0 1-13 0c0-3.7 3.3-7.4 6.5-11.7z").toPath()
}
