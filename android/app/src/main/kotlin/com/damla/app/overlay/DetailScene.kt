package com.damla.app.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.core.Formats
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.gauge.OverlayChart
import com.damla.gauge.OverlayFrame
import com.damla.gauge.OverlayGeometry
import com.damla.gauge.OverlayScene
import com.damla.gauge.OverlayStage
import com.damla.gauge.Reveal
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The detail scene (docs/06): a frameless 260 x 380 scene at 0.86 scale leaning on the widget's side,
 * opening as a circle from the chosen cell. Coordinates inside are the design's.
 */
@Composable
internal fun DetailScene(
    model: OverlayModel,
    frame: OverlayFrame,
    actions: OverlayActions,
) {
    val geometry = model.geometry
    val k = OverlayGeometry.DETAIL_SCALE
    val active = model.scene.stage == OverlayStage.DETAIL && !model.scene.sharing
    val asset = model.selected
    Box(
        Modifier
            .size(OverlayGeometry.DETAIL_WIDTH.toFloat().dp, OverlayGeometry.DETAIL_HEIGHT.toFloat().dp)
            .graphicsLayer {
                translationX =
                    geometry.detailLeft
                        .toFloat()
                        .dp
                        .toPx()
                translationY =
                    geometry.detailTop
                        .toFloat()
                        .dp
                        .toPx()
                scaleX = k.toFloat()
                scaleY = k.toFloat()
                transformOrigin = TransformOrigin(if (geometry.right) 1f else 0f, 0f)
            },
    ) {
        // The circle opens on the chosen cell; in scene coordinates it is undone from the scene's own scaling.
        val cx =
            if (geometry.right) {
                OverlayGeometry.DETAIL_WIDTH - (geometry.detailLeft + OverlayGeometry.DETAIL_WIDTH - frame.detailClipX) / k
            } else {
                (frame.detailClipX - geometry.detailLeft) / k
            }
        val cy = (frame.detailClipY - geometry.detailTop) / k
        val radius = frame.detailClipRadius / k
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = CircleAt(cx.toFloat().dp.toPx(), cy.toFloat().dp.toPx(), radius.toFloat().dp.toPx())
                    alpha = frame.detailClipAlpha.toFloat()
                },
        ) {
            SwipeArea(model, active)
            TitleSection(model, asset, frame.sections[0])
            Annotations(model, asset, frame)
            PriceSection(model, asset, frame.sections[2])
            ChartSection(model, asset, frame, active)
            RangePicker(model, frame, active)
            ButtonRow(model, asset, frame, active, actions)
        }
    }
}

/** A circle at a point of the layer, for the opening clip. A round rectangle, so every Android version clips it. */
private class CircleAt(
    private val cx: Float,
    private val cy: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(RoundRect(cx - radius, cy - radius, cx + radius, cy + radius, radius, radius))
}

/** Places a part of the scene at design coordinates and plays its reveal: fade, slide and sharpen. */
private fun Modifier.at(
    x: Double,
    y: Double,
    reveal: Reveal? = null,
    horizontal: Boolean = false,
): Modifier =
    graphicsLayer {
        val offset = reveal?.offset ?: 0.0
        translationX = (x + if (horizontal) offset else 0.0).toFloat().dp.toPx()
        translationY = (y + if (horizontal) 0.0 else offset).toFloat().dp.toPx()
        alpha = reveal?.alpha?.toFloat() ?: 1f
        renderEffect = reveal?.let { cssFilter(it.blur, 0.0) }
    }

/** Swipe sideways for the next asset; a tap steps back (design: 40 dp swipe, under 10 dp a tap). */
@Composable
private fun SwipeArea(
    model: OverlayModel,
    active: Boolean,
) {
    Box(
        Modifier
            .size(260.dp, 140.dp)
            .at(0.0, 40.0)
            .then(
                if (!active) {
                    Modifier
                } else {
                    Modifier.pointerInput(model) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var last = down.position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position
                                if (!change.pressed) break
                            }
                            // Positions are in the scene's own units; the design measures on screen.
                            val dx = (last.x - down.position.x) / density * OverlayGeometry.DETAIL_SCALE
                            model.swipe(dx.toDouble())
                        }
                    }
                },
            ),
    )
}

@Composable
private fun TitleSection(
    model: OverlayModel,
    asset: AssetSnapshot,
    reveal: Reveal,
) {
    val context = LocalContext.current
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1_000)
            value = Instant.now()
        }
    }
    val freshness = Formats.freshness(context, asset, now)
    val shadow = CssShadow(1f, 3f, Color.Black.copy(alpha = 0.35f))
    Column(Modifier.at(0.0, 0.0, reveal)) {
        BasicText(asset.name, style = designText(17f, FontWeight.Bold, Color.White, 21f, shadow = shadow), maxLines = 1)
        BasicText(
            Formats.subtitle(context, asset),
            style = designText(11f, FontWeight.Normal, Color.White.copy(alpha = 0.75f), shadow = shadow),
        )
        Row(
            Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (freshness.live) LiveDot(5f, OverlayColors.LIVE_ON_DARK, model.reduceMotion)
            BasicText(
                freshness.text,
                style = designText(10f, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f), shadow = shadow, tabular = true),
            )
        }
    }
}

/** Three labels tied to the parts of the gauge with thin lines: Damla, Üst halka, Alt halka. */
@Composable
private fun Annotations(
    model: OverlayModel,
    asset: AssetSnapshot,
    frame: OverlayFrame,
) {
    val lines = remember { ANNOTATION_TOPS.indices.map { annotationPath(it) } }
    Canvas(Modifier.size(OverlayGeometry.DETAIL_WIDTH.toFloat().dp, OverlayGeometry.DETAIL_HEIGHT.toFloat().dp)) {
        val unit = 1.dp.toPx()
        for ((j, points) in lines.withIndex()) {
            val path =
                Path().apply {
                    moveTo(points[0].x * unit, points[0].y * unit)
                    for (point in points.drop(1)) lineTo(point.x * unit, point.y * unit)
                }
            // `stroke-dasharray: 140`: the offset hides the far end of the line.
            val visible = (ANNOTATION_DASH - frame.annotationLineOffsets[j]).coerceAtLeast(0.0).toFloat() * unit
            val measure = PathMeasure().apply { setPath(path, false) }
            val shown = Path()
            if (visible > 0) measure.getSegment(0f, minOf(visible, measure.length), shown, true)
            drawPath(shown, Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
            drawCircle(
                Color.White.copy(alpha = frame.annotationDotAlphas[j].toFloat()),
                radius = 2.dp.toPx(),
                center = Offset(points[0].x * unit, points[0].y * unit),
            )
        }
    }
    val context = LocalContext.current
    val parts = OverlayModel.parts(asset)
    val names = listOf(R.string.part_drop_label, R.string.part_upper_label, R.string.part_lower_label)
    val shadow = CssShadow(1f, 3f, Color.Black.copy(alpha = 0.35f))
    for ((j, part) in parts.withIndex()) {
        val reveal = frame.annotations[j]
        Column(Modifier.at(ANNOTATION_LEFT, ANNOTATION_TOPS[j], reveal, horizontal = true)) {
            BasicText(
                stringResource(names[j], Formats.interval(context, part.interval)),
                style = designText(10f, FontWeight.Normal, Color.White.copy(alpha = 0.78f), 13f, shadow = shadow),
                maxLines = 1,
                softWrap = false,
            )
            BasicText(
                context.getString(stateWord(part.shape(), model.grey(asset))),
                style = designText(14f, FontWeight.Bold, Color(frame.annotationColors[j]), 17f, shadow = shadow),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** From a part of the gauge to the label: the part's point, then across to the label (design `annoPath`). */
private fun annotationPath(k: Int): List<Offset> {
    val unit = OverlayGeometry.HERO_GAUGE / 76
    val (qx, qy) = ANNOTATION_ANCHORS[k]
    val x = OverlayChart.round1(HERO_LEFT + qx * unit)
    val y = OverlayChart.round1(HERO_TOP + (qy - 8) * unit)
    val labelY = ANNOTATION_TOPS[k] + 15
    return listOf(Offset(x.toFloat(), y.toFloat()), Offset(92f, labelY.toFloat()), Offset(98f, labelY.toFloat()))
}

@Composable
private fun PriceSection(
    model: OverlayModel,
    asset: AssetSnapshot,
    reveal: Reveal,
) {
    val context = LocalContext.current
    val drop = asset.drop ?: return
    val grey = model.grey(asset)
    Column(Modifier.at(0.0, 170.0, reveal), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        BasicText(
            asset.price?.let { Formats.price(asset, it) }.orEmpty(),
            style =
                designText(
                    24f,
                    FontWeight.Bold,
                    Color.White,
                    letterSpacing = -0.4f,
                    shadow = CssShadow(1f, 3f, Color.Black.copy(alpha = 0.3f)),
                    tabular = true,
                ),
            maxLines = 1,
        )
        BasicText(
            stringResource(R.string.detail_change, Formats.percent(drop.net), Formats.interval(context, drop.interval)),
            style = designText(11f, FontWeight.SemiBold, OverlayColors.bright(drop.shape(), grey)),
            maxLines = 1,
        )
    }
}

/** The chart: line, soft area, dashed base line, end dot; drag a finger along it to read prices. */
@Composable
private fun ChartSection(
    model: OverlayModel,
    asset: AssetSnapshot,
    frame: OverlayFrame,
    active: Boolean,
) {
    val reveal = frame.sections[3]
    val scrub = model.scrubX
    val target = model.chartTarget
    val description = stringResource(R.string.detail_chart)
    Box(Modifier.size(260.dp, 62.dp).at(0.0, 222.0, reveal)) {
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .size(OverlayScene.CHART_WIDTH.toFloat().dp, OverlayScene.CHART_HEIGHT.toFloat().dp)
                .semantics { contentDescription = description }
                .then(if (active) Modifier.pointerInput(model) { scrubGestures(model) } else Modifier),
        ) {
            val unit = 1.dp.toPx()
            val color = Color(frame.chartColor)
            val ys = frame.chartPoints
            val line =
                Path().apply {
                    moveTo(0f, OverlayChart.round1(ys[0]).toFloat() * unit)
                    for (s in OverlayChart.segments(ys)) {
                        cubicTo(
                            s.c1x.toFloat() * unit,
                            s.c1y.toFloat() * unit,
                            s.c2x.toFloat() * unit,
                            s.c2y.toFloat() * unit,
                            s.x.toFloat() * unit,
                            s.y.toFloat() * unit,
                        )
                    }
                }
            val baseY = OverlayChart.round1(target[0]).toFloat() * unit
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(0f, baseY),
                Offset(size.width, baseY),
                strokeWidth = unit,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * unit, 4 * unit)),
            )
            val area =
                Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(area, color.copy(alpha = frame.chartAreaAlpha.toFloat()))
            // `stroke-dasharray: 600`: the line draws itself as the offset runs out.
            val measure = PathMeasure().apply { setPath(line, false) }
            val visible = ((CHART_DASH - frame.chartLineOffset) * unit).toFloat()
            val drawn = if (visible >= measure.length) line else Path().also { if (visible > 0) measure.getSegment(0f, visible, it, true) }
            drawPath(drawn, color, style = Stroke(2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (scrub == null) {
                drawCircle(
                    Color(frame.chartEndColor).copy(alpha = frame.chartEndAlpha.toFloat()),
                    radius = 3 * unit,
                    center = Offset(size.width, OverlayChart.round1(target.last()).toFloat() * unit),
                )
            } else {
                val x = OverlayChart.round1(scrub).toFloat() * unit
                val y = OverlayChart.round1(OverlayChart.yAt(target, scrub)).toFloat() * unit
                drawLine(Color.White.copy(alpha = 0.6f), Offset(x, -6 * unit), Offset(x, size.height), strokeWidth = unit)
                drawCircle(Color.White, radius = 4 * unit, center = Offset(x, y))
                drawCircle(color, radius = 4 * unit, center = Offset(x, y), style = Stroke(2 * unit))
            }
        }
        if (scrub != null) ScrubTip(model, asset, scrub)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.scrubGestures(model: OverlayModel) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        model.scrub((down.position.x / size.width * OverlayScene.CHART_WIDTH).toDouble())
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            model.scrub((change.position.x / size.width * OverlayScene.CHART_WIDTH).toDouble())
        }
        model.scrub(null)
    }
}

/** "Pzt · ₺4.215,30" above the finger: the time and price of the history point under it. */
@Composable
private fun ScrubTip(
    model: OverlayModel,
    asset: AssetSnapshot,
    scrub: Double,
) {
    val history = model.history ?: return
    if (history.points.isEmpty()) return
    val point = history.points[OverlayChart.indexAt(scrub, history.points.size)]
    val time = Instant.parse(point.t).atZone(ZoneId.systemDefault())
    val pattern = TIP_PATTERNS[model.scene.range]
    val text =
        stringResource(
            R.string.detail_scrub,
            DateTimeFormatter.ofPattern(pattern, Formats.TURKISH).format(time),
            Formats.price(asset, point.p),
        )
    val left = (scrub - TIP_WIDTH / 2).coerceIn(0.0, OverlayScene.CHART_WIDTH - TIP_WIDTH)
    Box(
        Modifier
            .at(left, -12.0)
            .height(17.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = designText(10f, FontWeight.SemiBold, OverlayColors.DARK_TEXT), maxLines = 1, softWrap = false)
    }
}

/** 1 gün / 1 hafta / 1 ay / 1 yıl, with a white pill sliding under the chosen one. */
@Composable
private fun RangePicker(
    model: OverlayModel,
    frame: OverlayFrame,
    active: Boolean,
) {
    val labels = listOf(R.string.range_day, R.string.range_week, R.string.range_month, R.string.range_year)
    Box(
        Modifier
            .size(260.dp, 26.dp)
            .at(0.0, 290.0, frame.sections[4])
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        frame.rangePillX
                            .toFloat()
                            .dp
                            .toPx()
                }.size(62.5.dp, 22.dp)
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == model.scene.range
                val color by animateColorAsState(
                    if (on) OverlayColors.DARK_TEXT else Color.White,
                    tween(250, easing = CSS_EASE),
                    label = "range",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .semantics { selected = on }
                        .clickable(remember { MutableInteractionSource() }, null, enabled = active, role = Role.Tab) { model.pickRange(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(stringResource(label), style = designText(11f, if (on) FontWeight.SemiBold else FontWeight.Normal, color))
                }
            }
        }
    }
}

/** Alarm kur, Uygulamada aç, Paylaş, and the dots that switch assets. */
@Composable
private fun ButtonRow(
    model: OverlayModel,
    asset: AssetSnapshot,
    frame: OverlayFrame,
    active: Boolean,
    actions: OverlayActions,
) {
    Row(
        Modifier
            .width(260.dp)
            .height(40.dp)
            .at(0.0, 328.0, frame.sections[5]),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(OverlayIcon.BELL, 17f, 2f, stringResource(R.string.detail_alarm), active) { actions.openApp(AppRoute.Alarm(asset.id)) }
        GlassButton(OverlayIcon.OPEN, 15f, 2.2f, stringResource(R.string.detail_open), active) { actions.openApp(AppRoute.Asset(asset.id)) }
        GlassButton(OverlayIcon.SHARE, 16f, 2.1f, stringResource(R.string.detail_share), active) { model.share(true) }
        Spacer(Modifier.weight(1f))
        if (model.assets.size > 1) {
            Row {
                model.assets.forEachIndexed { i, other ->
                    Box(
                        Modifier
                            .size(20.dp, 24.dp)
                            .semantics { contentDescription = other.name }
                            .clickable(remember { MutableInteractionSource() }, null, enabled = active) { model.select(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .graphicsLayer { alpha = frame.dotAlphas[i].toFloat() }
                                .size(frame.dotWidths[i].toFloat().dp, 5.dp)
                                .background(Color.White, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** A 40 dp glass circle with a line icon. */
@Composable
private fun GlassButton(
    icon: OverlayIcon,
    size: Float,
    stroke: Float,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .semantics { contentDescription = label }
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pressScale(interaction)
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size, stroke, Color.White)
        }
    }
}

private val CSS_EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** The detail gauge's body centre in the scene (design `HLx`, `HLy`). */
private const val HERO_LEFT = 44.0
private const val HERO_TOP = 100.0

/** Points of the drop and the two rings the labels point at, in the gauge's 76-wide units. */
private val ANNOTATION_ANCHORS = listOf(20.0 to 0.0, 22.9 to 32.8, 24.0 to 41.6)
private val ANNOTATION_TOPS = listOf(48.0, 90.0, 132.0)
private const val ANNOTATION_LEFT = 102.0
private const val ANNOTATION_DASH = 140.0
private const val CHART_DASH = 600.0

/** The tip's width in the design's placement (`scrub - 46`, at most `260 - 92`). */
private const val TIP_WIDTH = 92.0

/** How the scrub tip names a point, per range: hour, weekday, day, month. */
private val TIP_PATTERNS = listOf("HH:mm", "EEE HH:mm", "d MMM", "MMM yyyy")
