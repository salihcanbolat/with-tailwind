package com.damla.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The design's curves (docs/05). */
object Ease {
    val standard = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val css = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val softSpring = CubicBezierEasing(0.25f, 1.04f, 0.5f, 1f)
    val press = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)
}

/** App screens follow the user's font size, so their text is in sp (the overlay is the exception). */
fun text(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = DamlaColors.text,
    lineHeight: Float? = null,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
): TextStyle =
    TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight?.sp ?: TextStyle.Default.lineHeight,
        letterSpacing = letterSpacing.sp,
        textAlign = align,
    )

@Composable
fun Label(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(value, modifier, style, maxLines = maxLines)

/** `.dmBas`: shrinks while pressed (180 ms springy curve). */
@Composable
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.94f,
): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, tween(180, easing = Ease.press), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A tappable area without ripple; the designs answer with [pressScale] instead. */
@Composable
fun Modifier.tap(
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = clickable(interaction, null, enabled = enabled, role = role, onClick = onClick)

/** Glass card: white 5% with a 0.5 white 8% inner line (docs/07). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(DamlaColors.card, shape)
            .border(0.5.dp, DamlaColors.cardLine, shape),
        content = content,
    )
}

/** A 0.5 separator line between rows. */
@Composable
fun Separator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(DamlaColors.separator),
    )
}

/** The section title above a card ("Zaman aralıkları"). */
@Composable
fun SectionTitle(
    value: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Label(value, text(13f, FontWeight.SemiBold, DamlaColors.secondaryText))
        if (trailing != null) Label(trailing, text(12f, color = DamlaColors.text45))
    }
}

/** The iOS-like switch of the designs: 50 x 30, green when on. */
@Composable
fun Toggle(
    on: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
) {
    val track by animateColorAsState(if (on) DamlaColors.green else DamlaColors.toggleOff, tween(240, easing = Ease.css), label = "track")
    val knob by animateFloatAsState(if (on) 20f else 0f, tween(280, easing = Ease.press), label = "knob")
    Box(
        Modifier
            .size(50.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .semantics {
                contentDescription = label
                selected = on
            }.tap(role = Role.Switch) { onChange(!on) },
    ) {
        Box(
            Modifier
                .offset(x = (2 + knob).dp, y = 2.dp)
                .size(26.dp)
                .graphicsLayer {
                    shadowElevation = 2.dp.toPx()
                    shape = CircleShape
                    clip = true
                }.background(Color.White, CircleShape),
        )
    }
}

/** A segmented control with a sliding pill (380 ms soft spring). */
@Composable
fun Segmented(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    fontSize: Float = 13f,
) {
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(DamlaColors.control)
            .padding(2.dp),
    ) {
        val cell = maxWidth / items.size
        val x by animateFloatAsState((cell * selected).value, tween(380, easing = Ease.softSpring), label = "pill")
        Box(
            Modifier
                .offset(x = x.dp)
                .width(cell)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(DamlaColors.controlPill),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics { this.selected = i == selected }
                        .tap(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Label(item, text(fontSize, if (i == selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
                }
            }
        }
    }
}

/** A choice chip: blue when chosen (220 ms colour change). */
@Composable
fun Chip(
    value: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (on) DamlaColors.accent else DamlaColors.control,
        tween(220, easing = Ease.css),
        label = "chip",
    )
    val color by animateColorAsState(if (on) Color.White else DamlaColors.text85, tween(220, easing = Ease.css), label = "chipText")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .semantics { selected = on }
            .tap { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Label(value, text(12f, if (on) FontWeight.SemiBold else FontWeight.Normal, color), maxLines = 1)
    }
}

/** The small "Kaydedildi" bubble (opacity 260 ms, rise and scale 420 ms springy). */
@Composable
fun Toast(
    visible: Boolean,
    value: String,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(260, easing = Ease.css), label = "toast")
    val motion by animateFloatAsState(if (visible) 1f else 0f, tween(420, easing = Ease.press), label = "toastMove")
    Row(
        modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = (12 * (1 - motion)).dp.toPx()
                scaleX = 0.94f + 0.06f * motion
                scaleY = 0.94f + 0.06f * motion
            }.height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DamlaColors.toast)
            .border(0.5.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.CHECK, 16.dp, DamlaColors.green, 2.8f)
        Label(value, text(14f, FontWeight.SemiBold), maxLines = 1)
    }
}

/** Shows a toast for 1.4 s each time [key] changes to a positive value. */
@Composable
fun rememberToast(key: Int): Boolean {
    val visible = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(key) {
        if (key > 0) {
            visible.value = true
            delay(TOAST_MILLIS)
            visible.value = false
        }
    }
    return visible.value
}

const val TOAST_MILLIS = 1_400L

/**
 * Where open sheets draw: above the whole app shell, the tab bar included, which a screen's own content
 * stays under (DamlaApp). Without a layer a sheet draws in place.
 */
class SheetLayer {
    internal val sheets = mutableStateListOf<@Composable () -> Unit>()

    @Composable
    fun Sheets() {
        sheets.forEach { sheet -> key(sheet) { sheet() } }
    }
}

val LocalSheetLayer = staticCompositionLocalOf<SheetLayer?> { null }

/**
 * A sheet rising from the bottom over a dim (dim 300 ms; sheet 500 ms easeStandard), with the grab handle.
 */
@Composable
fun BottomSheet(
    open: Boolean,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layer = LocalSheetLayer.current
    if (layer == null) {
        Sheet(open, dismissLabel, onDismiss, content)
        return
    }
    val latestOpen by rememberUpdatedState(open)
    val latestLabel by rememberUpdatedState(dismissLabel)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestContent by rememberUpdatedState(content)
    DisposableEffect(layer) {
        val sheet: @Composable () -> Unit = { Sheet(latestOpen, latestLabel, latestDismiss, latestContent) }
        layer.sheets += sheet
        onDispose { layer.sheets -= sheet }
    }
}

@Composable
private fun Sheet(
    open: Boolean,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dim by animateFloatAsState(if (open) 1f else 0f, tween(300, easing = Ease.css), label = "sheetDim")
    val rise by animateFloatAsState(if (open) 0f else 1.1f, tween(500, easing = Ease.standard), label = "sheet")
    if (dim == 0f && !open) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * dim))
                .semantics { contentDescription = dismissLabel }
                .tap(enabled = open, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = size.height * rise }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(DamlaColors.sheet)
                // Taps on the sheet must not reach the dim behind it.
                .pointerInput(Unit) { detectTapGestures {} }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(36.dp, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DamlaColors.text30),
            )
            content()
        }
    }
}

/** A full-width button of the designs: 50 tall, corner 16. */
@Composable
fun PrimaryButton(
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = DamlaColors.accent,
    color: Color = Color.White,
    height: Dp = 50.dp,
    icon: Icons? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, 0.95f)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .tap(interaction, enabled) { onClick() },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, 16.dp, color, 2.8f)
        Label(value, text(17f, FontWeight.SemiBold, color), maxLines = 1)
    }
}

/** The header of pushed screens: back link on the left, title, optional action on the right. */
@Composable
fun NavHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = DamlaColors.text55,
    back: String? = null,
    onBack: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(44.dp)) {
        if (back != null) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-4).dp)
                    .height(44.dp)
                    .tap(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.BACK, 18.dp, DamlaColors.accent, 2.6f, width = 11.dp)
                Label(back, text(16f, color = DamlaColors.accent), maxLines = 1)
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(title, text(17f, FontWeight.SemiBold), maxLines = 1)
            if (subtitle != null) Label(subtitle, text(12f, FontWeight.SemiBold, subtitleColor), maxLines = 1)
        }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** Line icons of the designs: SVG paths in a 24 x 24 box (or [viewBox]), stroked with round ends. */
enum class Icons(
    val paths: List<String>,
    val viewBox: Float = 24f,
    val viewHeight: Float = viewBox,
    val filled: Boolean = false,
) {
    PLUS(listOf("M12 5v14M5 12h14")),
    CHECK(listOf("M5 12.5l4.5 4.5L19 7.5")),
    CHEVRON(listOf("M1 1l6 6-6 6"), 8f, 14f),
    BACK(listOf("M10 2L2 10l8 8"), 12f, 20f),
    BELL(listOf("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")),
    SLIDERS(listOf("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")),
    DROP(listOf("M12 2.5c3.2 4.3 6.5 8 6.5 11.7a6.5 6.5 0 0 1-13 0c0-3.7 3.3-7.4 6.5-11.7z")),
    DROP_FILLED(listOf("M12 2.5c3.2 4.3 6.5 8 6.5 11.7a6.5 6.5 0 0 1-13 0c0-3.7 3.3-7.4 6.5-11.7z"), filled = true),
    INFO(listOf("M12 11v6M12 7.5v.5")),
    SEARCH(listOf("M20 20l-4-4")),
    SWAP(listOf("M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7")),
    CLOSE(listOf("M1.5 1.5l7 7M8.5 1.5l-7 7"), 10f),
    UP(listOf("M12 19V5M5 12l7-7 7 7")),
    DOWN(listOf("M12 5v14M5 12l7 7 7-7")),
    BOTH(listOf("M12 3v18M7 8l5-5 5 5M7 16l5 5 5-5")),
    ARROW_RIGHT(listOf("M1 6h13M9 1l5 5-5 5"), 16f, 12f),
    CHEVRON_DOWN(listOf("M1 1.5l5 5 5-5"), 12f, 8f),
    CHEVRON_UP(listOf("M6 15l6-6 6 6")),
    WIDGET(listOf("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z")),
    WIFI_OFF(
        listOf(
            "M2 8.8a15 15 0 0 1 4.2-2.5",
            "M22 8.8a15 15 0 0 0-11-3.7",
            "M5 12.6a10 10 0 0 1 5.2-2.3",
            "M19 12.6a10 10 0 0 0-2.2-1.4",
            "M8.5 16.3a5 5 0 0 1 7 0",
            "M12 20h.01",
            "M3 3l18 18",
        ),
    ),
    ;

    val parsed: List<Path> by lazy { paths.map { PathParser().parsePathString(it).toPath() } }
}

@Composable
fun Icon(
    icon: Icons,
    height: Dp,
    color: Color,
    stroke: Float = 2f,
    modifier: Modifier = Modifier,
    width: Dp = height * (icon.viewBox / icon.viewHeight),
) {
    Canvas(modifier.size(width, height)) {
        scale(size.width / icon.viewBox, size.height / icon.viewHeight, pivot = Offset.Zero) {
            for (path in icon.parsed) {
                drawPath(path, color, style = if (icon.filled) Fill else Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (icon == Icons.SEARCH || icon == Icons.INFO) {
            // The circles of the search and info icons.
            val unit = size.width / 24f
            val (cx, cy, r) = if (icon == Icons.SEARCH) Triple(11f, 11f, 7f) else Triple(12f, 12f, 9.5f)
            drawCircle(color, r * unit, Offset(cx * unit, cy * unit), style = Stroke(stroke * unit, cap = StrokeCap.Round))
        }
    }
}

/**
 * The tiny part marks of the designs: the drop filled, or a ring arc stroked (view box -38 -38 76 92).
 */
@Composable
fun PartMark(
    part: Int,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
    height: Dp = 12.dp,
    others: Color? = null,
    stroke: Float = 11f,
) {
    Canvas(modifier.size(width, height)) {
        scale(size.width / 76f, size.height / 92f, pivot = Offset.Zero) {
            translate(38f, 38f) {
                PART_PATHS.forEachIndexed { i, path ->
                    val tint = if (i == part) color else others ?: return@forEachIndexed
                    if (i == 0) drawPath(path, tint) else drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private val PART_PATHS by lazy {
    listOf(
        "M0,-34 C3,-24 11.4,-12.2 19.05,1 A22 22 0 1 1 -19.05,1 C-11.4,-12.2 -3,-24 0,-34 Z",
        "M-22.9,32.8 A40 40 0 0 0 22.9,32.8",
        "M-24,41.6 A48 48 0 0 0 24,41.6",
    ).map { PathParser().parsePathString(it).toPath() }
}

/** `dmGel`: rises 10 dp and fades in over 520 ms after [delayMillis]. */
@Composable
fun Modifier.enter(
    delayMillis: Int,
    reduceMotion: Boolean,
): Modifier {
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, tween(520, easing = Ease.standard))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (10 * (1 - progress.value)).dp.toPx()
    }
}

/** Space that keeps the last rows above a bottom bar. */
@Composable
fun BottomSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

/**
 * The designs' range input (`accent-color: #0A84FF`): a thin track filled in blue up to a white thumb. Drag
 * or tap; [onChange] gets whole steps of [range].
 */
@Composable
fun ValueSlider(
    value: Int,
    range: IntRange,
    label: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = label }
            .pointerInput(range) {
                fun pick(x: Float) = range.first + ((x / size.width).coerceIn(0f, 1f) * span).roundToIntCompat()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(pick(down.position.x))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        onChange(pick(change.position.x))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 28.dp
        val travel = maxWidth - thumb
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33787880)),
        )
        Box(
            Modifier
                .width(thumb / 2 + travel * fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DamlaColors.accent),
        )
        Box(
            Modifier
                .offset(x = travel * fraction)
                .size(thumb)
                .graphicsLayer {
                    shadowElevation = 3.dp.toPx()
                    shape = CircleShape
                    clip = true
                }.background(Color.White, CircleShape),
        )
    }
}

private fun Float.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
