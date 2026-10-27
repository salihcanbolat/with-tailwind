package com.damla.app.alarms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.AssetSnapshot
import com.damla.api.ConditionType
import com.damla.app.R
import com.damla.app.main.AppModel
import com.damla.app.main.AssetGauge
import com.damla.app.main.Chevron
import com.damla.app.main.EmptyCard
import com.damla.app.main.Navigator
import com.damla.app.main.RoundButton
import com.damla.app.main.Route
import com.damla.app.main.ScreenColumn
import com.damla.app.main.ShapeGauge
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Ease
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.Segmented
import com.damla.app.ui.Separator
import com.damla.app.ui.Toggle
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.FiredAlarm
import com.damla.core.Formats
import com.damla.gauge.Shape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Alarmlar" (design/Alarmlar.dc.html): the alarms with their switches, and what has rung. */
@Composable
fun AlarmsScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val alarms by model.alarms.collectAsState()
    val history by model.history.collectAsState()
    val settings by model.settings.collectAsState()
    val snapshot by model.snapshot.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val texts = remember { alarmText(context) }

    ScreenColumn(top = 4.dp, bottom = 100.dp, gap = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(stringResource(R.string.alarms_title), text(32f, FontWeight.Bold, letterSpacing = -0.4f))
                Label(stringResource(R.string.alarms_active, alarms.count { it.enabled }), text(13f, color = DamlaColors.secondaryText))
            }
            RoundButton(Icons.PLUS, stringResource(R.string.alarms_new), DamlaColors.accent, androidx.compose.ui.graphics.Color.White) {
                navigator.push(Route.AlarmEdit())
            }
        }
        Segmented(listOf(stringResource(R.string.alarms_installed), stringResource(R.string.alarms_history)), tab, { tab = it })
        if (tab == 0) {
            if (alarms.isEmpty()) {
                EmptyCard(
                    stringResource(R.string.state_no_alarms),
                    stringResource(R.string.state_no_alarms_hint),
                    stringResource(R.string.alarms_new),
                ) { navigator.push(Route.AlarmEdit()) }
            }
            for (alarm in alarms) {
                val asset = snapshot?.assets?.firstOrNull { it.id == alarm.assetId } ?: model.asset(alarm.assetId)
                AlarmCard(alarm, asset, texts, settings, onToggle = { model.setAlarmEnabled(alarm.id, it) }) {
                    navigator.push(Route.AlarmEdit(alarmId = alarm.id))
                }
            }
        } else {
            if (history.isEmpty()) {
                Label(stringResource(R.string.state_no_history), text(14f, color = DamlaColors.text55), Modifier.padding(horizontal = 4.dp))
            } else {
                GlassCard {
                    history.forEachIndexed { i, fired ->
                        if (i > 0) Separator()
                        HistoryRow(fired, snapshot?.assets?.firstOrNull { it.id == fired.assetId } ?: model.asset(fired.assetId), settings)
                    }
                }
            }
        }
    }
}

/** An alarm: gauge, name and kind, the switch, and its condition (tap to edit). Off alarms fade to 55%. */
@Composable
private fun AlarmCard(
    alarm: Alarm,
    asset: AssetSnapshot?,
    texts: com.damla.alarms.AlarmText,
    settings: com.damla.core.AppSettings,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val alpha by animateFloatAsState(if (alarm.enabled) 1f else 0.55f, tween(260, easing = Ease.css), label = "alarm")
    val name = asset?.name ?: alarm.assetId
    val states =
        listOfNotNull(
            asset?.drop?.let { AlarmPart.DROP to it.state },
            asset?.upperRing?.let { AlarmPart.UPPER_RING to it.state },
            asset?.lowerRing?.let { AlarmPart.LOWER_RING to it.state },
        ).toMap()
    GlassCard(Modifier.graphicsLayer { this.alpha = alpha }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetGauge(asset, 30.dp, settings)
                Column(Modifier.weight(1f)) {
                    Label(name, text(16f, FontWeight.SemiBold), maxLines = 1)
                    Label(texts.kind(alarm), text(12f, color = DamlaColors.text55))
                }
                Toggle(alarm.enabled, stringResource(R.string.alarms_switch, name), onToggle)
            }
            val edit = stringResource(R.string.alarms_edit)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DamlaColors.card)
                    .semantics { contentDescription = edit }
                    .tap(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConditionMark(alarm, states)
                Label(texts.card(alarm, alarmUnit(asset), states), text(14f, lineHeight = 18f), Modifier.weight(1f))
                Chevron(DamlaColors.text35)
            }
        }
    }
}

/** A single state condition shows its turn with two grey drops; price and percent show an arrow. */
@Composable
private fun ConditionMark(
    alarm: Alarm,
    states: Map<AlarmPart, String>,
) {
    val condition = alarm.conditions.singleOrNull() ?: return
    when (condition.type) {
        ConditionType.STATE -> {
            val to = condition.target?.let { runCatching { Shape.valueOf(it) }.getOrNull() } ?: return
            val from = states[condition.part ?: AlarmPart.DROP]?.let { runCatching { Shape.valueOf(it) }.getOrNull() } ?: return
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeGauge(from, 18.dp, grey = true)
                Icon(Icons.ARROW_RIGHT, 10.dp, DamlaColors.text50, 2f)
                ShapeGauge(to, 18.dp, grey = true)
            }
        }

        ConditionType.PRICE, ConditionType.PERCENT -> {
            when (condition.op ?: AlarmOp.UP) {
                AlarmOp.UP -> Icon(Icons.UP, 18.dp, DamlaColors.green, 2.4f)
                AlarmOp.DOWN -> Icon(Icons.DOWN, 18.dp, DamlaColors.red, 2.4f)
                AlarmOp.BOTH -> Icon(Icons.BOTH, 18.dp, DamlaColors.text70, 2.4f)
            }
        }

        ConditionType.WAVE -> {
            ShapeGauge(Shape.WAVE, 18.dp, grey = true)
        }
    }
}

/** A rung alarm: the asset, what happened and when ("Bugün", "Dün", or the date). */
@Composable
private fun HistoryRow(
    fired: FiredAlarm,
    asset: AssetSnapshot?,
    settings: com.damla.core.AppSettings,
) {
    val name = asset?.name ?: fired.assetId
    val what =
        fired.text
            .removePrefix(name)
            .trim()
            .replaceFirstChar { it.titlecase(Formats.TURKISH) }
    val day = Instant.parse(fired.at).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val `when` =
        when (day) {
            today -> stringResource(R.string.history_today)
            today.minusDays(1) -> stringResource(R.string.history_yesterday)
            else -> DateTimeFormatter.ofPattern("d MMM", Formats.TURKISH).format(day)
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetGauge(asset, 26.dp, settings)
        Column(Modifier.weight(1f)) {
            Label(name, text(15f, FontWeight.SemiBold), maxLines = 1)
            Label(what, text(12f, color = DamlaColors.text55))
        }
        Label(`when`, text(12f, color = DamlaColors.text55))
    }
}
