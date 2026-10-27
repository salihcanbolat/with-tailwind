package com.damla.app.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.api.CatalogEntry
import com.damla.app.R
import com.damla.app.ui.BottomSheet
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Ease
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.NavHeader
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.SectionTitle
import com.damla.app.ui.Separator
import com.damla.app.ui.Toggle
import com.damla.app.ui.pressScale
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.Formats
import com.damla.core.WidgetConfig
import com.damla.gauge.Shape

/**
 * "Çapraz kur" (design/CaprazKur.dc.html): numerator ÷ denominator, the pair's gauge from the backend (the
 * app never works out a state itself), presets, a name, and adding it to the list.
 */
@Composable
fun CrossScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val settings by model.settings.collectAsState()
    val watchlist by model.watchlist.collectAsState()
    val widget by model.widget.collectAsState()
    LaunchedEffect(Unit) { model.loadCatalog() }
    val sides = model.catalog?.crossSides.orEmpty()
    var numerator by remember { mutableStateOf("xau_gram") }
    var denominator by remember { mutableStateOf("xag_gram") }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var turns by remember { mutableIntStateOf(0) }
    var bumps by remember { mutableIntStateOf(0) }
    var inWidget by remember { mutableStateOf(true) }
    var suggestAlarm by remember { mutableStateOf(false) }
    val pair = "$numerator/$denominator"
    LaunchedEffect(pair) { model.loadGauge(pair) }
    val a = sides.firstOrNull { it.id == numerator }
    val b = sides.firstOrNull { it.id == denominator }
    val name = "${a?.short.orEmpty()}/${b?.short.orEmpty()}"
    val added = watchlist.any { it.id == pair }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun choose(
        top: String,
        bottom: String,
    ) {
        numerator = top
        denominator = bottom
        bumps++
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            NavHeader(
                title = stringResource(R.string.cross_title),
                back = stringResource(R.string.cross_back),
                onBack = { navigator.pop() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GlassCard(radius = 24.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Side(model, a, R.string.cross_numerator, R.string.cross_numerator_label, settings, Modifier.weight(1f)) {
                                picking =
                                    true
                            }
                            Column(
                                Modifier.width(44.dp).heightIn(min = 120.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                            ) {
                                Label("÷", text(26f, FontWeight.Light, DamlaColors.secondaryText))
                                SwapButton(turns) {
                                    turns++
                                    choose(denominator, numerator)
                                }
                            }
                            Side(model, b, R.string.cross_denominator, R.string.cross_denominator_label, settings, Modifier.weight(1f)) {
                                picking =
                                    false
                            }
                        }
                        Result(model, pair, name, a, b, bumps, settings)
                        Label(stringResource(R.string.cross_explain), text(12f, color = DamlaColors.text50, lineHeight = 16f))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.cross_presets))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((top, bottom, label) in PRESETS) {
                            if (sides.none { it.id == top } || sides.none { it.id == bottom }) continue
                            val on = top == numerator && bottom == denominator
                            Box(
                                Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (on) DamlaColors.accent else DamlaColors.control)
                                    .tap { choose(top, bottom) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Label(
                                    stringResource(label),
                                    text(13f, FontWeight.SemiBold, if (on) Color.White else DamlaColors.text85),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.cross_look))
                    GlassCard {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            ToggleRow(stringResource(R.string.cross_in_widget), stringResource(R.string.cross_in_widget_hint), inWidget) {
                                inWidget =
                                    it
                            }
                            Separator()
                            ToggleRow(
                                stringResource(R.string.cross_suggest_alarm),
                                stringResource(R.string.cross_suggest_alarm_hint),
                                suggestAlarm,
                            ) {
                                suggestAlarm = it
                            }
                            Separator()
                            Row(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .fillMaxWidth()
                                    .tap {
                                        if (!added) model.add(pair, null)
                                        navigator.push(Route.Asset(pair))
                                    },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Label(stringResource(R.string.cross_intervals), text(15f), Modifier.weight(1f))
                                val chosen = watchlist.firstOrNull { it.id == pair }
                                val intervals =
                                    listOfNotNull(
                                        chosen?.drop,
                                        chosen?.upperRing,
                                        chosen?.lowerRing,
                                    ).ifEmpty { settings.newAssetIntervals }
                                Label(
                                    intervals.joinToString(" · ") { Formats.intervalShort(context, it) },
                                    text(14f, color = DamlaColors.text55),
                                )
                                Chevron()
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DamlaColors.bottomBar)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        ) {
            PrimaryButton(
                stringResource(if (added) R.string.cross_added else R.string.cross_add, name),
                height = 52.dp,
                background = if (added) DamlaColors.green else DamlaColors.accent,
                icon = if (added) Icons.CHECK else Icons.PLUS,
            ) {
                if (added) {
                    model.remove(pair)
                } else {
                    model.add(pair, null)
                    if (inWidget && widget.order.size < WidgetConfig.MAX_ASSETS && pair !in widget.order) {
                        model.saveWidget(widget.copy(order = widget.order + pair))
                    }
                    if (suggestAlarm) navigator.push(Route.AlarmEdit(assetId = pair))
                }
            }
        }
        SidePicker(model, sides, picking, numerator, denominator, settings, onDismiss = { picking = null }) { id ->
            if (picking == true) choose(id, denominator) else choose(numerator, id)
            picking = null
        }
    }
}

private val PRESETS =
    listOf(
        Triple("xau_gram", "xag_gram", R.string.cross_preset_gold_silver),
        Triple("btcusdt", "xau_gram", R.string.cross_preset_btc_gold),
        Triple("eurtry", "usdtry", R.string.cross_preset_eur_usd),
        Triple("xag_gram", "try", R.string.cross_preset_silver_try),
        Triple("btcusdt", "usdtry", R.string.cross_preset_btc_usd),
        Triple("xau_ons", "usdtry", R.string.cross_preset_ons_usd),
    )

/** A side card: "Pay", its gauge, its name and "Değiştir". */
@Composable
private fun Side(
    model: AppModel,
    entry: CatalogEntry?,
    title: Int,
    label: Int,
    settings: com.damla.core.AppSettings,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(label, entry?.name.orEmpty())
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DamlaColors.field)
            .semantics { contentDescription = description }
            .tap(interaction) { onClick() }
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)
            .pressScale(interaction, 0.95f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(stringResource(title), text(11f, color = DamlaColors.text55))
        SideGauge(model, entry, 40, settings)
        Label(entry?.short.orEmpty(), text(15f, FontWeight.Bold), maxLines = 1)
        Label(stringResource(R.string.cross_change), text(11f, FontWeight.SemiBold, DamlaColors.accent))
    }
}

/** A side's own gauge; the Turkish lira has no direction of its own, so it stays a quiet grey drop. */
@Composable
private fun SideGauge(
    model: AppModel,
    entry: CatalogEntry?,
    size: Int,
    settings: com.damla.core.AppSettings,
) {
    val asset = entry?.let { model.asset(it.id) }
    if (asset != null) AssetGauge(asset, size.dp, settings) else ShapeGauge(Shape.FLAT, size.dp, grey = true)
}

/** The swap button turns half a turn on every tap (500 ms springy). */
@Composable
private fun SwapButton(
    turns: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(turns * 180f, tween(500, easing = Ease.press), label = "swap")
    val label = stringResource(R.string.cross_swap)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x2E0A84FF))
            .semantics { contentDescription = label }
            .tap(interaction) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.pressScale(interaction, 0.95f).graphicsLayer { rotationZ = rotation },
        ) { Icon(Icons.SWAP, 17.dp, DamlaColors.accent, 2.2f) }
    }
}

/** The pair's gauge, name and "1 Altın = 87,12 Gümüş"; the gauge gives a little bounce on every change. */
@Composable
private fun Result(
    model: AppModel,
    pair: String,
    name: String,
    a: CatalogEntry?,
    b: CatalogEntry?,
    bumps: Int,
    settings: com.damla.core.AppSettings,
) {
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(bumps) {
        if (bumps > 0 && !reduceMotion) {
            // `dmTakla`: 0.92 → 1.04 → 1 in 460 ms.
            bounce.snapTo(0.92f)
            bounce.animateTo(
                1f,
                keyframes {
                    durationMillis = 460
                    1.04f at 230 using Ease.press
                },
            )
        }
    }
    val asset = model.asset(pair)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DamlaColors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            },
        ) {
            if (asset != null) AssetGauge(asset, 34.dp, settings) else ShapeGauge(Shape.FLAT, 34.dp, grey = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(name, text(16f, FontWeight.Bold), maxLines = 1)
            val value = asset?.price?.let { Formats.number(it, 4) } ?: stringResource(R.string.alarm_value_missing)
            Label(
                stringResource(R.string.cross_sentence, a?.short.orEmpty(), value, b?.short.orEmpty()),
                text(13f, color = DamlaColors.text70),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Label(title, text(15f))
            Label(hint, text(12f, color = DamlaColors.text50))
        }
        Toggle(on, title, onChange)
    }
}

/** The side picker: Maden, Döviz (with the Turkish lira), Kripto; the other side cannot be chosen. */
@Composable
private fun SidePicker(
    model: AppModel,
    sides: List<CatalogEntry>,
    picking: Boolean?,
    numerator: String,
    denominator: String,
    settings: com.damla.core.AppSettings,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val numeratorSide = picking != false
    BottomSheet(picking != null, stringResource(R.string.action_close), onDismiss) {
        Label(
            stringResource(if (numeratorSide) R.string.cross_pick_numerator else R.string.cross_pick_denominator),
            text(20f, FontWeight.Bold),
        )
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val groups = listOf("metal" to R.string.add_metal, "fx" to R.string.add_fx, "crypto" to R.string.add_crypto)
            for ((kind, title) in groups) {
                val items = sides.filter { it.kind == kind }
                if (items.isEmpty()) continue
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(stringResource(title), text(12f, FontWeight.SemiBold, DamlaColors.text55), Modifier.padding(horizontal = 4.dp))
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(DamlaColors.card)) {
                        items.forEachIndexed { i, entry ->
                            if (i > 0) Separator()
                            val current = if (numeratorSide) numerator else denominator
                            val other = if (numeratorSide) denominator else numerator
                            val disabled = entry.id == other
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .graphicsLayer { alpha = if (disabled) 0.4f else 1f }
                                    .tap(enabled = !disabled) { onPick(entry.id) }
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SideGauge(model, entry, 26, settings)
                                Label(entry.name, text(15f, FontWeight.SemiBold), Modifier.weight(1f), maxLines = 1)
                                if (disabled) {
                                    Label(
                                        stringResource(if (numeratorSide) R.string.cross_not_numerator else R.string.cross_not_denominator),
                                        text(12f, color = DamlaColors.text45),
                                    )
                                }
                                Box(
                                    Modifier.graphicsLayer {
                                        alpha =
                                            if (entry.id ==
                                                current
                                            ) {
                                                1f
                                            } else {
                                                0f
                                            }
                                    },
                                ) { Icon(Icons.CHECK, 16.dp, DamlaColors.accent, 2.8f) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
