package com.damla.app.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.core.Formats
import com.damla.core.labelBadge
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.gauge.CellFrame
import com.damla.gauge.Geometry
import com.damla.gauge.OverlayFrame
import com.damla.gauge.OverlayGeometry
import com.damla.gauge.OverlayScene
import com.damla.gauge.OverlayStage
import com.damla.gauge.WidgetArrangement
import com.damla.gauge.android.CssBlur
import com.damla.gauge.android.Damla
import java.time.Instant

/** What the overlay hands to its activity: leaving, opening the app, sharing an image. */
internal class OverlayActions(
    val finish: () -> Unit,
    val openApp: (route: AppRoute) -> Unit,
    val share: (android.graphics.Bitmap) -> Unit,
)

/** Where a button of the overlay leads in the app (the screens arrive in phase 5). */
internal sealed interface AppRoute {
    data class Asset(
        val id: String,
    ) : AppRoute

    data class Alarm(
        val id: String,
    ) : AppRoute

    data class WidgetSettings(
        val widgetId: Int,
    ) : AppRoute
}

/**
 * The overlay over the home screen (docs/06): dim and blur, the widget's box growing into the glass folder
 * or its drops lifting into the flow, then the detail scene and the share card. Every value comes from the
 * [OverlayScene] at the current frame time.
 */
@Composable
internal fun OverlayScreen(
    model: OverlayModel,
    widgetId: Int,
    blur: WindowBlur,
    actions: OverlayActions,
) {
    var now by remember { mutableDoubleStateOf(OverlayModel.now()) }
    LaunchedEffect(model.changes) {
        do {
            now = withFrameNanos { it } / 1_000_000.0
        } while (now < model.scene.settledAt())
        if (model.closing && model.scene.stage == OverlayStage.WIDGET) actions.finish()
    }
    LaunchedEffect(Unit) { model.open() }
    BackHandler { model.back() }

    val frame = model.scene.frame(now)
    val density = LocalDensity.current
    SideEffect {
        blur.set(
            CssBlur.filterRadius(
                with(density) {
                    frame.homeBlur
                        .toFloat()
                        .dp
                        .toPx()
                },
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        // The share card's backdrop blur: everything behind it softens as its dim comes in.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect =
                        if (frame.shareDimAlpha > 0) cssFilter(SHARE_BACKDROP_BLUR * frame.shareDimAlpha, 0.0) else null
                },
        ) {
            val backLabel = stringResource(R.string.overlay_back)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = DIM * frame.dimAlpha.toFloat()))
                    .semantics { contentDescription = backLabel }
                    .pointerInput(model) { detectTapGestures { model.back() } },
            )
            WidgetBox(model, frame, widgetId, actions)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = DIM * frame.detailDimAlpha.toFloat())),
            )
            DetailScene(model, frame, actions)
            Hero(model, frame)
        }
        ShareLayer(model, frame, actions)
    }
}

/** The box: the widget's own card (Kutulu) or nothing (Kutusuz), growing into the glass folder (Klasör). */
@Composable
private fun WidgetBox(
    model: OverlayModel,
    frame: OverlayFrame,
    widgetId: Int,
    actions: OverlayActions,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = frame.boxAlpha.toFloat()
                scaleX = frame.boxScale.toFloat()
                scaleY = frame.boxScale.toFloat()
                transformOrigin =
                    TransformOrigin(
                        ((frame.boxLeft + frame.boxOriginX).toFloat().dp.toPx() / size.width),
                        ((frame.boxTop + frame.boxOriginY).toFloat().dp.toPx() / size.height),
                    )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left =
                frame.boxLeft
                    .toFloat()
                    .dp
                    .toPx()
            val top =
                frame.boxTop
                    .toFloat()
                    .dp
                    .toPx()
            val width =
                frame.boxWidth
                    .toFloat()
                    .dp
                    .toPx()
            val height =
                frame.boxHeight
                    .toFloat()
                    .dp
                    .toPx()
            val radius =
                OverlayScene.BOX_RADIUS
                    .toFloat()
                    .dp
                    .toPx()
            boxShadow(
                left,
                top,
                width,
                height,
                radius,
                frame.boxShadowY
                    .toFloat()
                    .dp
                    .toPx(),
                frame.boxShadowBlur
                    .toFloat()
                    .dp
                    .toPx(),
                Color.Black.copy(alpha = frame.boxShadowAlpha.toFloat()),
            )
            drawRoundRect(Color(frame.boxBackground), Offset(left, top), Size(width, height), CornerRadius(radius))
            if (frame.boxInsetAlpha > 0) {
                val line = 0.5.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = frame.boxInsetAlpha.toFloat()),
                    Offset(left + line / 2, top + line / 2),
                    Size(width - line, height - line),
                    CornerRadius(radius - line / 2),
                    style = Stroke(line),
                )
            }
        }
        if (!model.bare) WidgetLabels(model, frame)
        if (!model.flow) FolderHeader(model, frame, widgetId, actions)
        Box(
            Modifier
                .fillMaxSize()
                .then(if (model.flow) Modifier else Modifier.clipToBox(frame)),
        ) {
            model.assets.forEachIndexed { i, asset ->
                Cell(model, i, frame.cells[i], asset, clickable = model.scene.stage == OverlayStage.FLOW)
            }
        }
    }
}

/** In the folder the cells stay inside the glass (`overflow: hidden`). */
private fun Modifier.clipToBox(frame: OverlayFrame): Modifier =
    drawWithContent {
        val path =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        frame.boxLeft
                            .toFloat()
                            .dp
                            .toPx(),
                        frame.boxTop
                            .toFloat()
                            .dp
                            .toPx(),
                        (frame.boxLeft + frame.boxWidth).toFloat().dp.toPx(),
                        (frame.boxTop + frame.boxHeight).toFloat().dp.toPx(),
                        CornerRadius(
                            OverlayScene.BOX_RADIUS
                                .toFloat()
                                .dp
                                .toPx(),
                        ),
                    ),
                )
            }
        clipPath(path) { this@drawWithContent.drawContent() }
    }

/**
 * The boxed widget's own labels (badge and short name, or "Damla" for the mini widget) on the card. Bare
 * widgets draw nothing here: their real labels stay visible under the dim.
 */
@Composable
private fun WidgetLabels(
    model: OverlayModel,
    frame: OverlayFrame,
) {
    val geometry = model.geometry
    val arrangement = model.arrangement
    val top = geometry.widgetTop + (geometry.widgetHeight - arrangement.blockHeight) / 2 + arrangement.labelTop
    val labels: List<Pair<Double, AssetSnapshot?>> =
        if (arrangement.kind == WidgetArrangement.Kind.MINI) {
            listOf(geometry.widgetLeft + geometry.widgetWidth / 2 to null)
        } else {
            geometry.widgetDrops.mapIndexed { i, (x, _) -> x to model.assets[i] }
        }
    val style = designText(10f, FontWeight.SemiBold, OverlayColors.DARK_TEXT)
    for ((x, asset) in labels) {
        Row(
            Modifier
                .width(LABEL_WIDTH.toFloat().dp)
                .graphicsLayer {
                    translationX = (x - LABEL_WIDTH / 2).toFloat().dp.toPx()
                    translationY = top.toFloat().dp.toPx()
                    alpha = frame.widgetLayerAlpha.toFloat()
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (asset == null) {
                BasicText(stringResource(R.string.overlay_mini_label), style = style)
            } else {
                val badge = labelBadge(asset.badge)
                Box(Modifier.size(12.dp).background(Color(badge.background), CircleShape), contentAlignment = Alignment.Center) {
                    BasicText(badge.symbol, style = designText(6f, FontWeight.Bold, Color(badge.foreground)))
                }
                BasicText(asset.short, style = style)
            }
        }
    }
}

/** The folder's header (Klasör): title with the live dot, settings and the collapse button. */
@Composable
private fun FolderHeader(
    model: OverlayModel,
    frame: OverlayFrame,
    widgetId: Int,
    actions: OverlayActions,
) {
    val geometry = model.geometry
    val right = geometry.right
    val active = model.scene.stage == OverlayStage.FLOW
    Row(
        Modifier
            .width((OverlayGeometry.FOLDER_WIDTH - 20).toFloat().dp)
            .height(30.dp)
            .graphicsLayer {
                translationX = (geometry.folderLeft + 10).toFloat().dp.toPx()
                translationY = (geometry.folderTop + 6 + frame.headerOffset).toFloat().dp.toPx()
                alpha = frame.headerAlpha.toFloat()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (right) Arrangement.Start else Arrangement.End,
    ) {
        val title: @Composable RowScope.() -> Unit = {
            Row(
                Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, if (right) Alignment.Start else Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(R.string.overlay_folder_title), style = designText(14f, FontWeight.Bold, OverlayColors.DARK_TEXT))
                LiveDot(6f, OverlayColors.LIVE, model.reduceMotion)
            }
        }
        val editLabel = stringResource(R.string.overlay_edit_folder)
        val collapseLabel = stringResource(R.string.overlay_collapse)
        val settings: @Composable RowScope.() -> Unit = {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(30.dp)
                    .clickable(interaction, null, enabled = active) { actions.openApp(AppRoute.WidgetSettings(widgetId)) }
                    .semantics { contentDescription = editLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(OverlayIcon.SLIDERS, 15f, 2.2f, OverlayColors.ICON_ON_LIGHT, Modifier.pressScale(interaction))
            }
        }
        val collapse: @Composable RowScope.() -> Unit = {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(30.dp)
                    .clickable(interaction, null, enabled = active) { model.closeAll() }
                    .semantics { contentDescription = collapseLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .pressScale(interaction)
                        .size(24.dp)
                        .background(Color(0x29787880), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (right) OverlayIcon.COLLAPSE_RIGHT else OverlayIcon.COLLAPSE_LEFT, 11f, 3f, OverlayColors.ICON_ON_LIGHT)
                }
            }
        }
        if (right) {
            title()
            settings()
            collapse()
        } else {
            collapse()
            settings()
            title()
        }
    }
}

/** One asset: its gauge and, in the open stages, its name and state word underneath. */
@Composable
private fun Cell(
    model: OverlayModel,
    index: Int,
    cell: CellFrame,
    asset: AssetSnapshot,
    clickable: Boolean,
) {
    val visual = model.visual(asset) ?: return
    val context = LocalContext.current
    val grey = model.grey(asset)
    val shape = asset.drop?.shape() ?: return
    val description =
        context.getString(
            com.damla.core.R.string.gauge_description,
            asset.short,
            context.getString(stateWordLower(shape, grey)),
        )
    val onDark = model.flow
    val stale = Formats.freshness(context, asset, Instant.now()).stale
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(CELL_WIDTH.toFloat().dp, CELL_HEIGHT.toFloat().dp)
            .graphicsLayer {
                translationX = (cell.x - CELL_WIDTH / 2).toFloat().dp.toPx()
                translationY = (cell.y - GAUGE_HEIGHT / 2).toFloat().dp.toPx()
                scaleX = cell.scale.toFloat()
                scaleY = cell.scale.toFloat()
                transformOrigin = TransformOrigin(0.5f, (GAUGE_HEIGHT / 2 / CELL_HEIGHT).toFloat())
                alpha = cell.alpha.toFloat()
                renderEffect = cssFilter(cell.blur, cell.shadowAlpha)
            }.then(if (clickable) Modifier.clickable(interaction, null) { model.openDetail(index) } else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().pressScale(interaction)) {
            Damla(
                visual = visual,
                size = GAUGE.toFloat().dp,
                logo = asset.logo(),
                contentDescription = description,
                modifier = Modifier.alpha(if (stale) Formats.STALE_ALPHA else 1f),
                reduceMotion = model.reduceMotion,
                smallBadge = cell.smallBadge,
            )
            Spacer(Modifier.height(3.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = cell.labelAlpha.toFloat() },
            ) {
                val shadow = if (onDark) CssShadow(1f, 3f, Color.Black.copy(alpha = 0.6f)) else null
                BasicText(
                    asset.name,
                    style =
                        designText(
                            11f,
                            FontWeight.SemiBold,
                            if (onDark) Color.White else OverlayColors.DARK_TEXT,
                            13f,
                            shadow = shadow,
                        ),
                    maxLines = 1,
                    softWrap = false,
                )
                BasicText(
                    context.getString(stateWord(shape, grey)),
                    style =
                        designText(
                            9.5f,
                            FontWeight.SemiBold,
                            if (onDark) OverlayColors.bright(shape, grey) else OverlayColors.onLight(shape, grey),
                            12f,
                            shadow = shadow,
                        ).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The detail's gauge: it flies from the chosen cell into the scene (the shared element). */
@Composable
private fun Hero(
    model: OverlayModel,
    frame: OverlayFrame,
) {
    val asset = model.selected
    val visual = model.visual(asset) ?: return
    val context = LocalContext.current
    val shape = asset.drop?.shape() ?: return
    val grey = model.grey(asset)
    val stale = Formats.freshness(context, asset, Instant.now()).stale
    Box(
        Modifier.graphicsLayer {
            translationX =
                frame.heroX
                    .toFloat()
                    .dp
                    .toPx()
            translationY =
                frame.heroY
                    .toFloat()
                    .dp
                    .toPx()
            scaleX = frame.heroScale.toFloat()
            scaleY = frame.heroScale.toFloat()
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = frame.heroAlpha.toFloat() * (if (stale) Formats.STALE_ALPHA else 1f)
        },
    ) {
        // A swipe shows the next asset as it is, without playing a state change (docs/kararlar.md).
        key(asset.id) {
            Damla(
                visual = visual,
                size = OverlayGeometry.HERO_GAUGE.toFloat().dp,
                logo = asset.logo(),
                contentDescription =
                    context.getString(
                        com.damla.core.R.string.gauge_description,
                        asset.short,
                        context.getString(stateWordLower(shape, grey)),
                    ),
                reduceMotion = model.reduceMotion,
            )
        }
    }
}

private const val DIM = 0.3f
private const val SHARE_BACKDROP_BLUR = 6.0
private const val GAUGE = 40.0
private val GAUGE_HEIGHT = GAUGE * Geometry.HEIGHT / Geometry.WIDTH

/** A folder grid column (176 wide, 10 padding, two columns) and room for two label lines. */
private const val CELL_WIDTH = 78.0
private val CELL_HEIGHT = GAUGE_HEIGHT + 3 + 13 + 12
private const val LABEL_WIDTH = 96.0
