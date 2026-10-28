package com.damla.app.main

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.app.overlay.OverlayColors
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.AppSettings
import com.damla.core.Formats
import com.damla.core.closed
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Geometry
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import java.time.Duration
import java.time.Instant

/** The system's "remove animations" setting (CLAUDE.md: then pulses and accents stay still). */
val reduceMotion: Boolean get() = !ValueAnimator.areAnimatorsEnabled()

/** Grey only while the market is closed and the user wants grey. */
fun AssetSnapshot.grey(settings: AppSettings): Boolean = closed && settings.grayWhenClosed

/** The drop's state word and its bright colour (docs/01), "veri gecikiyor" when the data is late. */
@Composable
fun stateText(
    asset: AssetSnapshot,
    settings: AppSettings,
): Pair<String, Color> {
    val context = LocalContext.current
    val drop =
        asset.drop ?: run {
            // A new server collects its own history first (docs/kararlar.md): say when it will be enough.
            val ready = asset.readyAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val left = ready?.let { Duration.between(Instant.now(), it).toMinutes() }
            val text =
                if (left != null && left >= 0) {
                    stringResource(R.string.state_ready_in, Formats.durationShort(context, left))
                } else {
                    stringResource(R.string.state_no_data)
                }
            return text to DamlaColors.text55
        }
    if (Formats.freshness(context, asset, Instant.now()).stale && asset.marketOpen) {
        return stringResource(R.string.panel_late) to DamlaColors.text55
    }
    val grey = asset.grey(settings)
    return stringResource(stateWord(drop.shape(), grey)) to OverlayColors.bright(drop.shape(), grey)
}

/**
 * An asset's gauge (docs/04), faded to 60% while its data is late (docs/03). Without states yet it keeps its
 * place empty instead of guessing one.
 */
@Composable
fun AssetGauge(
    asset: AssetSnapshot?,
    size: Dp,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val visual = asset?.visual(settings)
    if (asset == null || visual == null) {
        Box(modifier.size(size, size * (Geometry.HEIGHT / Geometry.WIDTH).toFloat()))
        return
    }
    val context = LocalContext.current
    val drop = asset.drop?.shape() ?: Shape.FLAT
    val stale = Formats.freshness(context, asset, Instant.now()).stale && asset.marketOpen
    Damla(
        visual = visual,
        size = size,
        logo = asset.logo(),
        contentDescription =
            context.getString(
                com.damla.core.R.string.gauge_description,
                asset.short,
                context.getString(stateWordLower(drop, asset.grey(settings))),
            ),
        modifier = modifier.alpha(if (stale) Formats.STALE_ALPHA else 1f),
        reduceMotion = reduceMotion,
    )
}

/** A gauge of a bare state (the "Nasıl okunur?" sheet, alarm targets): one colour, no logo. */
@Composable
fun ShapeGauge(
    shape: Shape,
    size: Dp,
    modifier: Modifier = Modifier,
    grey: Boolean = false,
    description: String = "",
) {
    Damla(GaugeVisual(shape, shape, shape, closed = grey), size, null, description, modifier, reduceMotion = reduceMotion)
}

/** The panel's live dot (`dmCanliK`: 8 px, a ring growing 7 px in the first 70% of 2 s). */
@Composable
fun LiveDot(
    diameter: Dp,
    ring: Dp,
    color: Color = DamlaColors.green,
) {
    val progress =
        if (reduceMotion) {
            1f
        } else {
            val transition = rememberInfiniteTransition(label = "live")
            val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "live")
            value
        }
    Canvas(Modifier.size(diameter)) {
        val r = size.minDimension / 2
        if (progress < 0.7f) {
            val eased = EASE_OUT.transform(progress / 0.7f)
            drawCircle(color.copy(alpha = 0.55f * (1 - eased)), radius = r + ring.toPx() * eased)
        }
        drawCircle(color, radius = r)
    }
}

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** An undesigned state (docs/07), drawn in the cards' language: icon, sentence, optional action. */
@Composable
fun StatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x1FFF9F0A), shape)
            .border(0.5.dp, Color(0x66FF9F0A), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.WIFI_OFF, 18.dp, DamlaColors.orange, 2f)
        Label(message, text(13f, color = DamlaColors.text85, lineHeight = 17f), Modifier.weight(1f))
        if (action != null) Label(action, text(13f, FontWeight.SemiBold, DamlaColors.accent), Modifier.tap(onClick = onAction))
    }
}

/** An empty list (docs/07): a sentence and the way forward. */
@Composable
fun EmptyCard(
    title: String,
    hint: String,
    action: String,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DamlaColors.card, shape)
            .border(0.5.dp, DamlaColors.cardLine, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShapeGauge(Shape.FLAT, 40.dp, grey = true)
        Label(title, text(17f, FontWeight.SemiBold, align = TextAlign.Center))
        Label(hint, text(13f, color = DamlaColors.secondaryText, lineHeight = 18f, align = TextAlign.Center))
        PrimaryButton(action, height = 46.dp, icon = Icons.PLUS, onClick = onAction)
    }
}

/** The bottom tab bar: Panel, Alarmlar, Ayarlar (82 tall, blurred dark glass). */
@Composable
fun TabBar(
    current: Route,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs =
        listOf(
            Triple(Route.Panel, R.string.tab_panel, Icons.DROP),
            Triple(Route.Alarms, R.string.tab_alarms, Icons.BELL),
            Triple(Route.Settings, R.string.tab_settings, Icons.SLIDERS),
        )
    Column(
        modifier
            .fillMaxWidth()
            .background(DamlaColors.bar),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(DamlaColors.cardLine),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(74.dp)
                .padding(top = 8.dp),
        ) {
            for ((route, label, icon) in tabs) {
                val on = route == current
                val color = if (on) DamlaColors.accent else DamlaColors.text55
                val name = stringResource(label)
                Column(
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = name
                            selected = on
                        }.tap(role = Role.Tab) { onSelect(route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val shown = if (on && icon == Icons.DROP) Icons.DROP_FILLED else icon
                    Icon(shown, 24.dp, color, if (on) 2.2f else 1.8f)
                    Label(name, text(10f, FontWeight.SemiBold, color))
                }
            }
        }
    }
}

/**
 * A screen's scrolling column: below the status bar, 16 dp side margins (the designs' 16 px), with room at
 * the bottom for bars drawn over it.
 */
@Composable
fun ScreenColumn(
    top: Dp = 0.dp,
    bottom: Dp = 24.dp,
    gap: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** A thin wrapper for rows that open something: content, then the chevron. */
@Composable
fun Chevron(color: Color = DamlaColors.text30) {
    Icon(Icons.CHEVRON, 12.dp, color, 2f)
}

/** The bright colour of a state word for a part, grey when its market is closed and grey is on. */
fun partColor(
    shape: Shape,
    grey: Boolean,
): Color = OverlayColors.bright(shape, grey)
