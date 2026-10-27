package com.damla.app.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSettings
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.app.ui.Chip
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.NavHeader
import com.damla.app.ui.PartMark
import com.damla.app.ui.SectionTitle
import com.damla.app.ui.Separator
import com.damla.app.ui.Toast
import com.damla.app.ui.Toggle
import com.damla.app.ui.ValueSlider
import com.damla.app.ui.rememberToast
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.Formats
import com.damla.core.INTERVAL_CHOICES
import com.damla.core.WidgetConfig
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.engine.Category
import com.damla.engine.Thresholds

/**
 * An asset's settings (design/VarlikAyar.dc.html): its gauge and what each part shows, the three intervals,
 * the stable and wave thresholds and whether it sits in the widget. Every change is saved at once.
 */
@Composable
fun AssetScreen(
    model: AppModel,
    navigator: Navigator,
    id: String,
) {
    val watchlist by model.watchlist.collectAsState()
    val settings by model.settings.collectAsState()
    val widget by model.widget.collectAsState()
    val snapshot by model.snapshot.collectAsState()
    val entry = watchlist.firstOrNull { it.id == id }
    if (entry == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    val asset = snapshot?.assets?.firstOrNull { it.id == id } ?: model.asset(id)
    var saved by remember { mutableIntStateOf(0) }
    val toast = rememberToast(saved)
    var notice by remember { mutableStateOf(R.string.saved) }
    val defaults = Thresholds.defaultFor(category(model, id, asset))
    val title = entry.name ?: asset?.name ?: id

    fun save(next: AssetSettings) {
        model.updateAsset(next)
        notice = R.string.saved
        saved++
    }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn(top = 0.dp, bottom = 90.dp) {
            NavHeader(
                title = title,
                back = stringResource(R.string.action_back_panel),
                onBack = { navigator.pop() },
                trailing = {
                    val label = stringResource(R.string.asset_alarm)
                    Box(
                        Modifier
                            .size(44.dp)
                            .semantics { contentDescription = label }
                            .tap { navigator.push(Route.AlarmEdit(assetId = id)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.BELL, 20.dp, DamlaColors.accent, 2f)
                    }
                },
            )
            Legend(asset, entry, settings)
            if (entry.id.contains('/')) CrossName(entry) { save(entry.copy(name = it.ifBlank { null })) }
            SectionTitle(stringResource(R.string.asset_intervals))
            Intervals(entry, asset) { save(it) }
            SectionTitle(stringResource(R.string.asset_sensitivity))
            Sensitivity(entry, defaults) { save(it) }
            WidgetToggle(widget, id) { on ->
                val order = widget.order
                when {
                    !on -> {
                        model.saveWidget(widget.copy(order = order - id))
                        notice = R.string.saved
                        saved++
                    }

                    order.size >= WidgetConfig.MAX_ASSETS -> {
                        notice = R.string.asset_widget_full
                        saved++
                    }

                    else -> {
                        model.saveWidget(widget.copy(order = order + id))
                        notice = R.string.saved
                        saved++
                    }
                }
            }
            Label(
                stringResource(R.string.asset_autosave),
                text(12f, color = DamlaColors.text45, align = TextAlign.Center),
                Modifier.fillMaxWidth(),
            )
            Label(
                stringResource(R.string.asset_remove),
                text(15f, FontWeight.SemiBold, DamlaColors.red, align = TextAlign.Center),
                Modifier
                    .fillMaxWidth()
                    .tap {
                        model.remove(id)
                        navigator.pop()
                    }.padding(vertical = 12.dp),
            )
        }
        Toast(toast, stringResource(notice), Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
    }
}

/** The category whose thresholds an asset starts from; a cross rate uses its numerator's (docs/02). */
private fun category(
    model: AppModel,
    id: String,
    asset: AssetSnapshot?,
): Category {
    val kind = if (id.contains('/')) model.asset(id.substringBefore('/'))?.kind else asset?.kind
    return when (kind) {
        "metal" -> Category.METAL
        "crypto" -> Category.CRYPTO
        else -> Category.FX
    }
}

/** The big gauge and what each part says: "Damla · son 1 saat — Yükselişte". */
@Composable
private fun Legend(
    asset: AssetSnapshot?,
    entry: AssetSettings,
    settings: com.damla.core.AppSettings,
) {
    val context = LocalContext.current
    GlassCard {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssetGauge(asset, 72.dp, settings)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val names = listOf(R.string.part_drop_label, R.string.part_upper_label, R.string.part_lower_label)
                val intervals = listOf(entry.drop, entry.upperRing, entry.lowerRing)
                val parts = listOf(asset?.drop, asset?.upperRing, asset?.lowerRing)
                val grey = asset?.grey(settings) ?: false
                for (j in 0..2) {
                    val part = parts[j]
                    val interval = intervals[j] ?: part?.interval
                    val color = part?.let { partColor(it.shape(), grey) } ?: DamlaColors.text45
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Label(
                            interval?.let { context.getString(names[j], Formats.interval(context, it)) }.orEmpty(),
                            text(13f, color = DamlaColors.text70),
                            Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Label(part?.let { stringResource(stateWord(it.shape(), grey)) }.orEmpty(), text(13f, FontWeight.SemiBold, color))
                    }
                }
            }
        }
    }
}

/** A cross rate's own name (design/CaprazKur.dc.html, "Ad"). */
@Composable
private fun CrossName(
    entry: AssetSettings,
    onChange: (String) -> Unit,
) {
    var value by remember(entry.id) { mutableStateOf(entry.name.orEmpty()) }
    GlassCard {
        Row(
            Modifier.padding(horizontal = 14.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(stringResource(R.string.asset_name), text(15f))
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onChange(it)
                },
                singleLine = true,
                textStyle = text(15f, color = DamlaColors.text85, align = TextAlign.End),
                cursorBrush = SolidColor(DamlaColors.accent),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Damla, Üst halka, Alt halka: six interval chips each, the part lit in the mark beside its title. */
@Composable
private fun Intervals(
    entry: AssetSettings,
    asset: AssetSnapshot?,
    onChange: (AssetSettings) -> Unit,
) {
    val context = LocalContext.current
    val titles = listOf(com.damla.core.R.string.part_drop, com.damla.core.R.string.part_upper_ring, com.damla.core.R.string.part_lower_ring)
    val current =
        listOf(
            entry.drop ?: asset?.drop?.interval,
            entry.upperRing ?: asset?.upperRing?.interval,
            entry.lowerRing ?: asset?.lowerRing?.interval,
        )
    GlassCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            for (j in 0..2) {
                if (j > 0) Separator()
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PartMark(
                            j,
                            DamlaColors.accent,
                            width = 18.dp,
                            height = 22.dp,
                            others =
                                androidx.compose.ui.graphics
                                    .Color(0x2EEBEBF5),
                            stroke = 7f,
                        )
                        Label(stringResource(titles[j]), text(15f, FontWeight.SemiBold))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (choice in INTERVAL_CHOICES) {
                            Chip(Formats.intervalShort(context, choice), on = current[j] == choice, modifier = Modifier.weight(1f)) {
                                onChange(
                                    when (j) {
                                        0 -> entry.copy(drop = choice)
                                        1 -> entry.copy(upperRing = choice)
                                        else -> entry.copy(lowerRing = choice)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The stable (0–1%) and wave (0–3%) thresholds; the wave stays above the stable one (docs/02). */
@Composable
private fun Sensitivity(
    entry: AssetSettings,
    defaults: Thresholds,
    onChange: (AssetSettings) -> Unit,
) {
    val stable = ((entry.stable ?: defaults.stable) * 100).toInt()
    val wave = ((entry.wave ?: defaults.wave) * 100).toInt()
    GlassCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Threshold(
                stringResource(R.string.asset_stable),
                stringResource(R.string.asset_stable_hint),
                stable,
                1..100,
                DamlaColors.yellow,
            ) { value ->
                val nextWave = if (wave <= value) value + 1 else wave
                onChange(entry.copy(stable = value / 100.0, wave = nextWave / 100.0))
            }
            Separator()
            Threshold(
                stringResource(R.string.asset_wave),
                stringResource(R.string.asset_wave_hint),
                wave,
                2..300,
                DamlaColors.orange,
            ) { value ->
                val nextStable = if (stable >= value) value - 1 else stable
                onChange(entry.copy(stable = nextStable / 100.0, wave = value / 100.0))
            }
        }
    }
}

@Composable
private fun Threshold(
    title: String,
    hint: String,
    value: Int,
    range: IntRange,
    color: androidx.compose.ui.graphics.Color,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(title, text(15f))
            Label("%" + Formats.number(value / 100.0, 2), text(15f, FontWeight.SemiBold, color))
        }
        ValueSlider(value, range, title, { if (it != value) onChange(it) })
        Label(hint, text(12f, color = DamlaColors.text55))
    }
}

@Composable
private fun WidgetToggle(
    widget: WidgetConfig,
    id: String,
    onChange: (Boolean) -> Unit,
) {
    GlassCard {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = stringResource(R.string.asset_in_widget)
            Label(label, text(15f), Modifier.weight(1f))
            Toggle(id in widget.order, label, onChange)
        }
    }
}
