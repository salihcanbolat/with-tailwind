package com.damla.app.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.api.PairingCode
import com.damla.app.R
import com.damla.app.ui.BottomSheet
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
import com.damla.core.StatusConfig
import com.damla.core.ThemeMode
import com.damla.core.WidgetConfig
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import kotlinx.coroutines.delay
import kotlin.random.Random

/** "Ayarlar" (design/Ayarlar.dc.html): pulse, vibration, grey when closed, theme, new asset intervals, source. */
@Composable
fun SettingsScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val settings by model.settings.collectAsState()
    val snapshot by model.snapshot.collectAsState()
    val status by model.status.collectAsState()
    val widget by model.widget.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
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
            // Not in the designs (docs/08 asks for it in Ayarlar): a proposal in the screen's own language.
            Section(R.string.settings_status) {
                SwitchLine(stringResource(R.string.settings_status_show), status.enabled) { on ->
                    model.saveStatus(status.copy(enabled = on, paused = false))
                    if (on &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    saved++
                }
                if (status.enabled) {
                    Separator()
                    val names = status.shown(widget).joinToString(", ") { model.asset(it)?.short ?: it }
                    val label = stringResource(R.string.settings_status_change, names)
                    Row(
                        Modifier
                            .heightIn(min = 46.dp)
                            .semantics { contentDescription = label }
                            .tap { picking = true },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(stringResource(R.string.settings_status_assets), text(15f), Modifier.weight(1f))
                        Label(names, text(15f, color = DamlaColors.secondaryText), maxLines = 1)
                        Chevron()
                    }
                }
            }
            // docs/09 "Günün özeti": not in the designs, a proposal in the screen's own language.
            Section(R.string.settings_digest) {
                SwitchLine(stringResource(R.string.settings_digest_show), settings.digest) { on ->
                    save(settings.copy(digest = on))
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (settings.digest) {
                    Separator()
                    Row(
                        Modifier.heightIn(min = 46.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(stringResource(R.string.settings_digest_time), text(15f), Modifier.width(84.dp))
                        val times = AppSettings.DIGEST_TIMES
                        Segmented(
                            times,
                            times.indexOf(settings.digestAt).coerceAtLeast(0),
                            { save(settings.copy(digestAt = times[it])) },
                            Modifier.weight(1f),
                            height = 32.dp,
                            fontSize = 12f,
                        )
                    }
                    Separator()
                    Row(
                        Modifier
                            .heightIn(min = 46.dp)
                            .tap { navigator.push(Route.Digest) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(stringResource(R.string.settings_digest_open), text(15f), Modifier.weight(1f))
                        Chevron()
                    }
                }
            }
            // docs/08: the computer follows the phone's list; not in the designs, a proposal.
            Section(R.string.settings_computer) {
                Row(
                    Modifier
                        .heightIn(min = 46.dp)
                        .tap { pairing = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(stringResource(R.string.settings_pair), text(15f), Modifier.weight(1f))
                    Chevron()
                }
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
        StatusAssetsSheet(model, picking, status) { picking = false }
        PairingSheet(model, pairing) { pairing = false }
    }
}

/** Choosing the ongoing notification's three assets: in the order tapped; none follows the widget. */
@Composable
private fun StatusAssetsSheet(
    model: AppModel,
    open: Boolean,
    status: StatusConfig,
    onClose: () -> Unit,
) {
    val watchlist by model.watchlist.collectAsState()
    BottomSheet(open, stringResource(R.string.action_close), onClose) {
        Label(stringResource(R.string.settings_status_pick), text(20f, FontWeight.Bold))
        Label(stringResource(R.string.settings_status_pick_hint), text(13f, color = DamlaColors.secondaryText, lineHeight = 18f))
        GlassCard(radius = 20.dp) {
            watchlist.forEachIndexed { i, entry ->
                if (i > 0) Separator()
                val place = status.assets.indexOf(entry.id)
                val full = status.assets.size >= StatusConfig.MAX_ASSETS
                val name = entry.name ?: model.asset(entry.id)?.name ?: entry.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .semantics { selected = place >= 0 }
                        .tap(enabled = place >= 0 || !full) {
                            val next = if (place >= 0) status.assets - entry.id else status.assets + entry.id
                            model.saveStatus(status.copy(assets = next))
                        }.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssetGauge(model.asset(entry.id), 28.dp, model.settings.value)
                    Label(
                        name,
                        text(
                            15f,
                            color =
                                if (place < 0 &&
                                    full
                                ) {
                                    DamlaColors.text45
                                } else {
                                    DamlaColors.text
                                },
                        ),
                        Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (place >= 0) DamlaColors.accent else DamlaColors.control),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (place >= 0) Label((place + 1).toString(), text(12f, FontWeight.SemiBold, Color.White))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(
                stringResource(R.string.settings_status_widget),
                on = status.assets.isEmpty(),
                modifier = Modifier.weight(1f),
                height = 40.dp,
            ) {
                model.saveStatus(status.copy(assets = emptyList()))
            }
            Chip(
                stringResource(R.string.settings_status_done),
                on = false,
                modifier = Modifier.weight(1f),
                height = 40.dp,
                onClick = onClose,
            )
        }
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

/** A fresh code each time the sheet opens, shown large and spaced so it reads well across a desk. */
@Composable
private fun PairingSheet(
    model: AppModel,
    open: Boolean,
    onClose: () -> Unit,
) {
    var code by remember { mutableStateOf<PairingCode?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        code = null
        failed = false
        code = model.pairingCode()
        failed = code == null
    }
    BottomSheet(open, stringResource(R.string.action_close), onClose) {
        Label(stringResource(R.string.pair_title), text(20f, FontWeight.Bold))
        Label(stringResource(R.string.pair_hint), text(13f, color = DamlaColors.secondaryText, lineHeight = 18f))
        val current = code
        when {
            current != null -> {
                val spaced = current.code.chunked(3).joinToString(" ")
                val description = stringResource(R.string.pair_code_description, current.code.toList().joinToString(" "))
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp) {
                    Label(
                        spaced,
                        text(34f, FontWeight.Bold, letterSpacing = 4f, align = TextAlign.Center),
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                            .semantics { contentDescription = description },
                    )
                }
                val until =
                    java.time.Instant
                        .parse(current.expiresAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                        .format(
                            java.time.format.DateTimeFormatter
                                .ofPattern("HH:mm"),
                        )
                Label(stringResource(R.string.pair_expires, until), text(13f, color = DamlaColors.text50))
            }

            failed -> {
                StatusBanner(stringResource(R.string.pair_failed))
            }

            else -> {
                Label(stringResource(R.string.pair_waiting), text(15f, color = DamlaColors.secondaryText))
            }
        }
    }
}
