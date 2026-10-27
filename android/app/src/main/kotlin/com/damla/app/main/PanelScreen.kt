package com.damla.app.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.app.ui.BottomSheet
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.PartMark
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.Separator
import com.damla.app.ui.enter
import com.damla.app.ui.pressScale
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.AppSettings
import com.damla.core.Formats
import com.damla.core.WidgetConfig
import com.damla.core.WidgetOpening
import com.damla.core.WidgetSize
import com.damla.core.shape
import com.damla.gauge.Shape

/** The main panel (design/Panel.dc.html): mood strip, widget card and the watch list. */
@Composable
fun PanelScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val snapshot by model.snapshot.collectAsState()
    val watchlist by model.watchlist.collectAsState()
    val settings by model.settings.collectAsState()
    val widget by model.widget.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val byId = snapshot?.assets.orEmpty().associateBy { it.id }
    // An asset added a moment ago may only be in the catalogue's gauges until the next snapshot.
    val rows = watchlist.mapNotNull { byId[it.id] ?: model.asset(it.id) }

    Box {
        ScreenColumn(top = 4.dp, bottom = 100.dp, gap = 20.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label(stringResource(R.string.panel_title), text(32f, FontWeight.Bold, letterSpacing = -0.4f))
                        val live = stringResource(R.string.panel_live)
                        Box(Modifier.semantics { contentDescription = live }) { LiveDot(8.dp, 7.dp) }
                    }
                    Label(stringResource(R.string.panel_subtitle, watchlist.size), text(13f, color = DamlaColors.secondaryText))
                }
                RoundButton(Icons.PLUS, stringResource(R.string.panel_add)) { navigator.push(Route.Add) }
            }
            if (!model.online) {
                StatusBanner(
                    stringResource(R.string.state_offline),
                    action = stringResource(R.string.state_retry),
                ) { model.refresh() }
            }
            if (rows.isNotEmpty()) Mood(rows, Modifier.enter(60, reduceMotion))
            WidgetCard(model, widget, settings, rows) { navigator.push(Route.Widget) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(stringResource(R.string.panel_list), text(20f, FontWeight.Bold))
                    Row(
                        Modifier.tap { sheet = true }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.INFO, 14.dp, DamlaColors.accent, 2.2f)
                        Label(stringResource(R.string.panel_how), text(13f, FontWeight.SemiBold, DamlaColors.accent))
                    }
                }
                if (rows.isEmpty()) {
                    EmptyCard(
                        stringResource(R.string.state_empty_list),
                        stringResource(R.string.state_empty_list_hint),
                        stringResource(R.string.panel_add),
                    ) { navigator.push(Route.Add) }
                } else {
                    GlassCard {
                        rows.forEachIndexed { i, asset ->
                            if (i > 0) Separator()
                            WatchRow(
                                asset,
                                settings,
                                watchlist.firstOrNull { it.id == asset.id }?.name,
                                Modifier.enter(160 + i * 55, reduceMotion),
                            ) {
                                navigator.push(Route.Asset(asset.id))
                            }
                        }
                    }
                }
            }
        }
        HowToRead(sheet) { sheet = false }
    }
}

/** "2 yükselişte · 1 sabit…": the share of each state in the list, as a strip and as words. */
@Composable
private fun Mood(
    rows: List<AssetSnapshot>,
    modifier: Modifier,
) {
    val shapes = rows.mapNotNull { it.drop?.shape() }
    val kinds =
        listOf(
            Shape.UP to R.string.mood_up,
            Shape.FLAT to R.string.mood_flat,
            Shape.DOWN to R.string.mood_down,
            Shape.WAVE to R.string.mood_wave,
        )
    val counts = kinds.map { (shape, _) -> shapes.count { it == shape } }
    val description = stringResource(R.string.panel_mood, counts[0], counts[1], counts[2], counts[3])
    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            kinds.forEachIndexed { i, (shape, _) ->
                if (counts[i] > 0) {
                    Box(
                        Modifier
                            .weight(counts[i].toFloat())
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(partColor(shape, grey = false)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            kinds.forEachIndexed { i, (shape, word) ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(partColor(shape, grey = false)))
                    Label(counts[i].toString(), text(12f, FontWeight.Bold))
                    Label(stringResource(word), text(12f, color = DamlaColors.secondaryText))
                }
            }
        }
    }
}

/** The widget card: a tiny home screen with the widget on it, its size and opening, and "Düzenle". */
@Composable
private fun WidgetCard(
    model: AppModel,
    config: WidgetConfig,
    settings: AppSettings,
    rows: List<AssetSnapshot>,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val inWidget = config.inWidget.mapNotNull { id -> rows.firstOrNull { it.id == id } ?: model.asset(id) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .pressScale(interaction, 0.97f)
            .fillMaxWidth()
            .clip(shape)
            .background(DamlaColors.card, shape)
            .border(0.5.dp, DamlaColors.cardLine, shape)
            .tap(interaction) { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(132.dp, 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DamlaColors.wallpaper),
        ) {
            // A 4 x 2 grid of app icons; the widget takes the fourth place of the first row.
            for (k in 0 until 8) {
                val x = 10 + (k % 4) * 30
                val y = 14 + (k / 4) * 34
                if (k == 3) {
                    Row(
                        Modifier.offset(x.dp, y.dp).size(22.dp, 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        inWidget
                            .take(
                                if (config.size ==
                                    WidgetSize.SINGLE
                                ) {
                                    1
                                } else {
                                    3
                                },
                            ).forEach {
                                AssetGauge(
                                    it,
                                    if (config.size ==
                                        WidgetSize.SINGLE
                                    ) {
                                        16.dp
                                    } else {
                                        10.dp
                                    },
                                    settings,
                                )
                            }
                    }
                } else {
                    Box(
                        Modifier
                            .offset(x.dp, y.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics
                                    .Color(0x38FFFFFF),
                            ),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(stringResource(R.string.panel_widget_title), text(15f, FontWeight.SemiBold))
            val size =
                when (config.size) {
                    WidgetSize.MINI -> stringResource(R.string.panel_widget_mini, minOf(3, config.order.size))
                    WidgetSize.WIDE -> stringResource(R.string.panel_widget_wide, minOf(3, config.order.size))
                    WidgetSize.SINGLE -> stringResource(R.string.panel_widget_single)
                }
            val opening =
                stringResource(
                    if (config.opening ==
                        WidgetOpening.FLOW
                    ) {
                        R.string.panel_widget_flow
                    } else {
                        R.string.panel_widget_folder
                    },
                )
            Label("$size\n$opening", text(12f, color = DamlaColors.secondaryText, lineHeight = 16f))
            Label(stringResource(R.string.panel_widget_edit), text(13f, FontWeight.SemiBold, DamlaColors.accent))
        }
        Chevron()
    }
}

/** A row of the watch list: gauge, name, the three intervals with their colours, the drop's word. */
@Composable
private fun WatchRow(
    asset: AssetSnapshot,
    settings: AppSettings,
    customName: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val (word, color) = stateText(asset, settings)
    val grey = asset.grey(settings)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .tap(interaction) { onClick() }
            .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp).pressScale(interaction), contentAlignment = Alignment.Center) { AssetGauge(asset, 36.dp, settings) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Label(customName ?: asset.name, text(16f, FontWeight.SemiBold), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOfNotNull(asset.drop, asset.upperRing, asset.lowerRing).forEachIndexed { j, part ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PartMark(j, partColor(part.shape(), grey))
                        Label(Formats.intervalShort(context, part.interval), text(11f, color = DamlaColors.secondaryText))
                    }
                }
            }
        }
        Label(word, text(13f, FontWeight.SemiBold, color))
        Chevron()
    }
}

/** The 44 dp round button of the headers (add, new alarm). */
@Composable
fun RoundButton(
    icon: Icons,
    label: String,
    background: androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics
            .Color(0x14FFFFFF),
    tint: androidx.compose.ui.graphics.Color = DamlaColors.text,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction)
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                0.5.dp,
                androidx.compose.ui.graphics
                    .Color(0x1FFFFFFF),
                CircleShape,
            ).semantics { contentDescription = label }
            .tap(interaction) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, 18.dp, tint, 2.4f)
    }
}

/** "Damla nasıl okunur?": the four shapes, the three parts and the closed market. */
@Composable
private fun HowToRead(
    open: Boolean,
    onClose: () -> Unit,
) {
    BottomSheet(open, stringResource(R.string.action_close), onClose) {
        Label(stringResource(R.string.how_title), text(22f, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shapes =
                listOf(
                    Triple(Shape.UP, R.string.how_up, R.string.how_up_desc),
                    Triple(Shape.DOWN, R.string.how_down, R.string.how_down_desc),
                    Triple(Shape.FLAT, R.string.how_flat, R.string.how_flat_desc),
                    Triple(Shape.WAVE, R.string.how_wave, R.string.how_wave_desc),
                )
            for ((shape, name, desc) in shapes) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DamlaColors.card)
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShapeGauge(shape, 34.dp)
                    Label(stringResource(name), text(13f, FontWeight.Bold, partColor(shape, grey = false)))
                    Label(
                        stringResource(desc),
                        text(11f, color = DamlaColors.text65, lineHeight = 14f, align = androidx.compose.ui.text.style.TextAlign.Center),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DamlaColors.card)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeGauge(Shape.UP, 60.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val parts =
                    listOf(
                        com.damla.core.R.string.part_drop to R.string.how_drop,
                        com.damla.core.R.string.part_upper_ring to R.string.how_upper,
                        com.damla.core.R.string.part_lower_ring to R.string.how_lower,
                    )
                for ((part, desc) in parts) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(stringResource(part), text(13f, FontWeight.Bold, lineHeight = 17f))
                        Label(stringResource(desc), text(13f, color = DamlaColors.text70, lineHeight = 17f))
                    }
                }
            }
        }
        Label(stringResource(R.string.how_note), text(12f, color = DamlaColors.secondaryText, lineHeight = 16f))
        PrimaryButton(stringResource(R.string.how_ok), onClick = onClose)
    }
}
