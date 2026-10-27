package com.damla.app.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.app.R
import com.damla.app.ui.Chip
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Ease
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.Segmented
import com.damla.app.ui.Separator
import com.damla.app.ui.Toast
import com.damla.app.ui.Toggle
import com.damla.app.ui.rememberToast
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.AppSettings
import com.damla.core.Formats
import com.damla.core.INTERVAL_CHOICES
import com.damla.core.PulseMode
import com.damla.core.ThemeMode
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import kotlinx.coroutines.delay
import kotlin.random.Random

/** "Ayarlar" (design/Ayarlar.dc.html): pulse, vibration, grey when closed, theme, new asset intervals, source. */
@Composable
fun SettingsScreen(model: AppModel) {
    val settings by model.settings.collectAsState()
    val snapshot by model.snapshot.collectAsState()
    var saved by remember { mutableIntStateOf(0) }
    val toast = rememberToast(saved)
    var open by remember { mutableIntStateOf(-1) }

    fun save(next: AppSettings) {
        model.saveSettings(next)
        saved++
    }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn(top = 4.dp, bottom = 110.dp, gap = 16.dp) {
            Label(stringResource(R.string.settings_title), text(32f, FontWeight.Bold, letterSpacing = -0.4f))
            Section(R.string.settings_liveliness) {
                Row(
                    Modifier.heightIn(min = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsePreview(settings.pulse)
                    Label(stringResource(R.string.settings_pulse), text(15f), Modifier.width(48.dp))
                    val modes = listOf(PulseMode.CALM, PulseMode.NORMAL, PulseMode.FAST, PulseMode.OFF)
                    Segmented(
                        listOf(
                            R.string.settings_pulse_calm,
                            R.string.settings_pulse_normal,
                            R.string.settings_pulse_fast,
                            R.string.settings_pulse_off,
                        ).map {
                            stringResource(it)
                        },
                        modes.indexOf(settings.pulse),
                        { save(settings.copy(pulse = modes[it])) },
                        Modifier.weight(1f),
                        height = 32.dp,
                        fontSize = 12f,
                    )
                }
                Separator()
                SwitchLine(stringResource(R.string.settings_vibration), settings.vibration) { save(settings.copy(vibration = it)) }
                Separator()
                SwitchLine(stringResource(R.string.settings_gray), settings.grayWhenClosed) { save(settings.copy(grayWhenClosed = it)) }
            }
            Section(R.string.settings_look) {
                Row(
                    Modifier.heightIn(min = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(stringResource(R.string.settings_theme), text(15f), Modifier.width(84.dp))
                    Segmented(
                        listOf(
                            R.string.settings_theme_dark,
                            R.string.settings_theme_light,
                            R.string.settings_theme_system,
                        ).map { stringResource(it) },
                        settings.theme.ordinal,
                        { save(settings.copy(theme = ThemeMode.entries[it])) },
                        Modifier.weight(1f),
                        height = 32.dp,
                        fontSize = 12f,
                    )
                }
            }
            Section(R.string.settings_new_intervals) {
                val parts =
                    listOf(
                        com.damla.core.R.string.part_drop,
                        com.damla.core.R.string.part_upper_ring,
                        com.damla.core.R.string.part_lower_ring,
                    )
                parts.forEachIndexed { j, part ->
                    if (j > 0) Separator()
                    Accordion(stringResource(part), settings.newAssetIntervals[j], open == j, {
                        open =
                            if (open ==
                                j
                            ) {
                                -1
                            } else {
                                j
                            }
                    }) { choice ->
                        save(settings.copy(newAssetIntervals = settings.newAssetIntervals.toMutableList().also { it[j] = choice }))
                    }
                }
                Separator()
                Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label(stringResource(R.string.settings_source), text(15f), Modifier.weight(1f))
                    val sources =
                        snapshot
                            ?.assets
                            .orEmpty()
                            .mapNotNull { it.source }
                            .distinct()
                            .joinToString(", ") { it.replaceFirstChar { c -> c.titlecase(Formats.TURKISH) } }
                    Label(sources.ifEmpty { "—" }, text(15f, color = DamlaColors.text50))
                }
            }
        }
        Toast(toast, stringResource(R.string.saved), Modifier.align(Alignment.BottomCenter).padding(bottom = 98.dp))
    }
}

@Composable
private fun Section(
    title: Int,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(title), text(13f, FontWeight.SemiBold, DamlaColors.secondaryText), Modifier.padding(horizontal = 4.dp))
        GlassCard(radius = 20.dp) {
            Column(Modifier.padding(horizontal = 14.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchLine(
    title: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, text(15f), Modifier.weight(1f))
        Toggle(on, title, onChange)
    }
}

/**
 * The live drop beside "Nabız": it turns now and then at the chosen pace (design: a 400 ms tick with a turn
 * chance of 8%, 20%, 45% or none). A preview of the setting, not data.
 */
@Composable
private fun PulsePreview(mode: PulseMode) {
    var turns by remember { mutableIntStateOf(0) }
    LaunchedEffect(mode) {
        val chance =
            when (mode) {
                PulseMode.CALM -> 0.08
                PulseMode.NORMAL -> 0.2
                PulseMode.FAST -> 0.45
                PulseMode.OFF -> 0.0
            }
        while (true) {
            delay(400)
            if (Random.nextDouble() < chance) turns += if (Random.nextDouble() < 0.6) 1 else -1
        }
    }
    Damla(
        GaugeVisual(Shape.UP, Shape.FLAT, Shape.UP, pulseTurns = turns),
        26.dp,
        com.damla.gauge.Logo
            .Badge(com.damla.gauge.BadgeKind.GOLD),
        "",
        reduceMotion = reduceMotion,
    )
}

/** A row that opens six interval chips below it (chevron 300 ms; panel 380 ms easeStandard). */
@Composable
private fun Accordion(
    title: String,
    value: String,
    open: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(300, easing = Ease.css), label = "chevron")
    val alpha by animateFloatAsState(if (open) 1f else 0f, tween(260, easing = Ease.css), label = "panel")
    val long = stringResource(LONG.getValue(value))
    val label = stringResource(R.string.settings_change, title, long)
    Column(Modifier.animateContentSize(tween(380, easing = Ease.standard))) {
        Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(title, text(15f), Modifier.weight(1f))
            Row(
                Modifier
                    .semantics { contentDescription = label }
                    .tap(onClick = onToggle)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(long, text(15f, color = DamlaColors.secondaryText))
                Box(Modifier.graphicsLayer { rotationZ = chevron }) { Icon(Icons.CHEVRON_DOWN, 8.dp, DamlaColors.text45, 2f) }
            }
        }
        if (open) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).graphicsLayer { this.alpha = alpha },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (choice in INTERVAL_CHOICES) {
                    Chip(Formats.intervalShort(context, choice), on = choice == value, modifier = Modifier.weight(1f)) { onPick(choice) }
                }
            }
        }
    }
}

private val LONG =
    mapOf(
        "15m" to R.string.settings_long_15m,
        "1h" to R.string.settings_long_1h,
        "4h" to R.string.settings_long_4h,
        "1d" to R.string.settings_long_1d,
        "1w" to R.string.settings_long_1w,
        "1mo" to R.string.settings_long_1mo,
    )
