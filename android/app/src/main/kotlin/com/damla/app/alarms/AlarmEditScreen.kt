package com.damla.app.alarms

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.alarms.AlarmValidation
import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.AssetSnapshot
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.Frequency
import com.damla.api.Joiner
import com.damla.api.Validity
import com.damla.app.R
import com.damla.app.main.AppModel
import com.damla.app.main.AssetGauge
import com.damla.app.main.Navigator
import com.damla.app.main.ShapeGauge
import com.damla.app.main.partColor
import com.damla.app.main.reduceMotion
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Ease
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.NavHeader
import com.damla.app.ui.PartMark
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.SectionTitle
import com.damla.app.ui.Segmented
import com.damla.app.ui.Separator
import com.damla.app.ui.Toggle
import com.damla.app.ui.pressScale
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.Formats
import com.damla.gauge.Shape
import java.time.Instant
import java.util.UUID

/** A condition being edited: the value stays text so typing "4.3" is not rewritten under the finger. */
private data class Draft(
    val key: Long,
    val type: ConditionType = ConditionType.STATE,
    val part: AlarmPart = AlarmPart.DROP,
    val target: Shape = Shape.UP,
    val op: AlarmOp = AlarmOp.UP,
    val value: String = "",
    val interval: String = "1h",
) {
    fun toCondition(): Condition =
        when (type) {
            ConditionType.STATE -> Condition(type, part = part, target = target.name)
            ConditionType.PRICE -> Condition(type, op = op, value = parse(value))
            ConditionType.PERCENT -> Condition(type, op = op, value = parse(value), interval = interval)
            ConditionType.WAVE -> Condition(type, value = parse(value), interval = interval)
        }

    companion object {
        /** Turkish typing: "4.300,50" and "4300.5" both mean 4300.5. */
        fun parse(text: String): Double? {
            val clean = text.trim().replace(" ", "")
            val normal = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
            return normal.toDoubleOrNull()
        }

        fun from(
            condition: Condition,
            key: Long,
        ) = Draft(
            key = key,
            type = condition.type,
            part = condition.part ?: AlarmPart.DROP,
            target = condition.target?.let { runCatching { Shape.valueOf(it) }.getOrNull() } ?: Shape.UP,
            op = condition.op ?: AlarmOp.UP,
            value = condition.value?.let(::alarmNumber).orEmpty(),
            interval = condition.interval ?: "1h",
        )
    }
}

/**
 * "Yeni alarm" (design/AlarmKur.dc.html): the asset, up to three conditions joined by VE/VEYA, how often and
 * how long it rings, and a plain Turkish sentence that is rewritten on every choice.
 */
@Composable
fun AlarmEditScreen(
    model: AppModel,
    navigator: Navigator,
    alarmId: String?,
    assetId: String?,
) {
    val watchlist by model.watchlist.collectAsState()
    val snapshot by model.snapshot.collectAsState()
    val settings by model.settings.collectAsState()
    val context = LocalContext.current
    val texts = remember { alarmText(context) }
    var loaded by remember { mutableStateOf(alarmId == null) }
    var existing by remember { mutableStateOf<Alarm?>(null) }
    var asset by remember { mutableStateOf(assetId ?: watchlist.firstOrNull()?.id) }
    val drafts = remember { mutableStateListOf<Draft>() }
    var nextKey by remember { mutableStateOf(1L) }
    var joiner by remember { mutableStateOf(Joiner.AND) }
    var frequency by remember { mutableStateOf(Frequency.EVERY_TIME) }
    var validity by remember { mutableStateOf(Validity.FOREVER) }
    var sound by remember { mutableStateOf(true) }
    var quiet by remember { mutableStateOf(false) }
    var wait by remember { mutableStateOf(true) }

    LaunchedEffect(alarmId) {
        if (alarmId != null) {
            model.alarmById(alarmId)?.let { alarm ->
                existing = alarm
                asset = alarm.assetId
                drafts.clear()
                alarm.conditions.forEachIndexed { i, condition -> drafts += Draft.from(condition, i + 1L) }
                nextKey = alarm.conditions.size + 1L
                joiner = alarm.joiner
                frequency = alarm.frequency
                validity = alarm.validity
                sound = alarm.sound
                quiet = alarm.waitQuietHours
                wait = alarm.waitMarketClosed
            }
            loaded = true
        }
    }
    val chosen: AssetSnapshot? = asset?.let { id -> snapshot?.assets?.firstOrNull { it.id == id } ?: model.asset(id) }
    LaunchedEffect(watchlist) { if (asset == null) asset = watchlist.firstOrNull()?.id }
    LaunchedEffect(loaded) { if (loaded && drafts.isEmpty()) drafts += Draft(0, target = firstFree(chosen, AlarmPart.DROP)) }
    val newId = remember { UUID.randomUUID().toString() }
    val createdAt = remember { Instant.now().toString() }

    val alarm =
        Alarm(
            id = existing?.id ?: newId,
            assetId = asset.orEmpty(),
            conditions = drafts.map { it.toCondition() },
            joiner = joiner,
            frequency = frequency,
            validity = validity,
            sound = sound,
            waitQuietHours = quiet,
            waitMarketClosed = wait,
            enabled = existing?.enabled ?: true,
            createdAt = existing?.createdAt ?: createdAt,
        )
    val valid = asset != null && AlarmValidation.problems(alarm).isEmpty() && drafts.none { it.state(chosen) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            NavHeader(
                title = stringResource(if (existing == null) R.string.alarm_new_title else R.string.alarm_edit_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {},
                back = null,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (watchlist.isEmpty()) {
                    Label(stringResource(R.string.alarm_need_asset), text(14f, color = DamlaColors.secondaryText))
                }
                AssetPicker(model, watchlist.map { it.id }, asset, settings) { id ->
                    asset = id
                    val picked = snapshot?.assets?.firstOrNull { it.id == id } ?: model.asset(id)
                    for (i in drafts.indices) {
                        val draft = drafts[i]
                        if (draft.type == ConditionType.STATE &&
                            draft.state(picked)
                        ) {
                            drafts[i] = draft.copy(target = firstFree(picked, draft.part))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(
                        stringResource(R.string.alarm_conditions),
                        trailing = stringResource(R.string.alarm_condition_count, drafts.size),
                    )
                    drafts.forEachIndexed { i, draft ->
                        if (i > 0) JoinButton(joiner) { joiner = if (joiner == Joiner.AND) Joiner.OR else Joiner.AND }
                        ConditionCard(
                            index = i,
                            draft = draft,
                            asset = chosen,
                            canRemove = drafts.size > 1,
                            onRemove = { drafts.removeAt(i) },
                        ) { drafts[i] = it }
                    }
                    if (drafts.size < Alarm.MAX_CONDITIONS) {
                        AddConditionButton {
                            drafts += Draft(nextKey, type = ConditionType.PRICE)
                            nextKey++
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.alarm_notification))
                    GlassCard {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            SegmentRow(
                                stringResource(R.string.alarm_frequency),
                                listOf(
                                    R.string.alarm_frequency_once_short,
                                    R.string.alarm_frequency_every_short,
                                    R.string.alarm_frequency_hourly_short,
                                ),
                                Frequency.entries.indexOf(frequency),
                            ) { frequency = Frequency.entries[it] }
                            Separator()
                            SegmentRow(
                                stringResource(R.string.alarm_validity),
                                listOf(
                                    R.string.alarm_validity_day_short,
                                    R.string.alarm_validity_week_short,
                                    R.string.alarm_validity_forever_short,
                                ),
                                Validity.entries.indexOf(validity),
                            ) { validity = Validity.entries[it] }
                            Separator()
                            SwitchRow(stringResource(R.string.alarm_sound), stringResource(R.string.alarm_sound_hint), sound) { sound = it }
                            Separator()
                            SwitchRow(stringResource(R.string.alarm_quiet), stringResource(R.string.alarm_quiet_hint), quiet) { quiet = it }
                            Separator()
                            SwitchRow(stringResource(R.string.alarm_wait), stringResource(R.string.alarm_wait_hint), wait) { wait = it }
                        }
                    }
                }
                if (existing != null) {
                    Label(
                        stringResource(R.string.alarm_delete),
                        text(15f, FontWeight.SemiBold, DamlaColors.red, align = TextAlign.Center),
                        Modifier
                            .fillMaxWidth()
                            .tap {
                                model.deleteAlarm(alarm.id)
                                navigator.pop()
                            }.padding(vertical = 12.dp),
                    )
                }
            }
        }
        // "Vazgeç" on the left of the header.
        Box(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp).height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Label(stringResource(R.string.action_cancel), text(16f, color = DamlaColors.accent), Modifier.tap { navigator.pop() })
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DamlaColors.bottomBar)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                AssetGauge(chosen, 24.dp, settings)
                val name = watchlist.firstOrNull { it.id == asset }?.name ?: chosen?.name ?: asset.orEmpty()
                Label(texts.summary(alarm, name, alarmUnit(chosen)), text(13f, color = DamlaColors.text80, lineHeight = 18f))
            }
            PrimaryButton(
                stringResource(
                    if (existing ==
                        null
                    ) {
                        R.string.alarm_save
                    } else {
                        R.string.alarm_update
                    },
                ),
                height = 52.dp,
                enabled = valid,
            ) {
                model.saveAlarm(alarm)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                navigator.pop()
            }
        }
    }
}

/** A state condition aiming at the state its part is already in cannot be saved (the "şu an" badge). */
private fun Draft.state(asset: AssetSnapshot?): Boolean = type == ConditionType.STATE && current(asset, part) == target

private fun current(
    asset: AssetSnapshot?,
    part: AlarmPart,
): Shape? {
    val snapshot =
        when (part) {
            AlarmPart.DROP -> asset?.drop
            AlarmPart.UPPER_RING -> asset?.upperRing
            AlarmPart.LOWER_RING -> asset?.lowerRing
        }
    return snapshot?.state?.let { runCatching { Shape.valueOf(it) }.getOrNull() }
}

/** The first state the part is not in now. */
private fun firstFree(
    asset: AssetSnapshot?,
    part: AlarmPart,
): Shape = Shape.entries.first { it != current(asset, part) }

/** The watched assets in a grid of five; the chosen one is outlined in blue. */
@Composable
private fun AssetPicker(
    model: AppModel,
    ids: List<String>,
    chosen: String?,
    settings: com.damla.core.AppSettings,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.alarm_asset))
        val label = stringResource(R.string.alarm_pick_asset)
        Column(Modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in ids.chunked(5)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in row) {
                        val on = id == chosen
                        val asset = model.asset(id)
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(shape)
                                .background(if (on) Color(0x290A84FF) else DamlaColors.card, shape)
                                .border(if (on) 1.5.dp else 0.5.dp, if (on) DamlaColors.accent else DamlaColors.cardLine, shape)
                                .semantics { selected = on }
                                .tap { onPick(id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            AssetGauge(asset, 28.dp, settings)
                            Label(asset?.short ?: id, text(11f, FontWeight.SemiBold), maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** VE (blue) or VEYA (orange) between two conditions; tap to switch. */
@Composable
private fun JoinButton(
    joiner: Joiner,
    onClick: () -> Unit,
) {
    val and = joiner == Joiner.AND
    val background by animateColorAsState(if (and) Color(0x330A84FF) else Color(0x2EFF9F0A), tween(240, easing = Ease.css), label = "join")
    val color by animateColorAsState(
        if (and) DamlaColors.accentLight else Color(0xFFFFB340),
        tween(240, easing = Ease.css),
        label = "joinText",
    )
    val word = stringResource(if (and) R.string.alarm_join_and else R.string.alarm_join_or)
    val label = stringResource(R.string.alarm_join_label, word)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(background)
                .semantics { contentDescription = label }
                .tap(onClick = onClick)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(word, text(12f, FontWeight.Bold, color, letterSpacing = 0.6f))
            Icon(Icons.SWAP, 10.dp, color, 3f)
        }
    }
}

/** One condition: type, then either part and target state, or value, period and direction. */
@Composable
private fun ConditionCard(
    index: Int,
    draft: Draft,
    asset: AssetSnapshot?,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onChange: (Draft) -> Unit,
) {
    // `dmKartGel`: a new card rises 14 dp from 97% and fades in over 420 ms.
    val enter = remember(draft.key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(draft.key) { enter.animateTo(1f, tween(420, easing = Ease.standard)) }
    GlassCard(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (14 * (1 - enter.value)).dp.toPx()
            scaleX = 0.97f + 0.03f * enter.value
            scaleY = 0.97f + 0.03f * enter.value
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.alarm_condition, index + 1), text(15f, FontWeight.SemiBold), Modifier.weight(1f))
                if (canRemove) {
                    val label = stringResource(R.string.alarm_condition_remove, index + 1)
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DamlaColors.control)
                            .semantics { contentDescription = label }
                            .tap(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.CLOSE, 11.dp, DamlaColors.text70, 1.8f)
                    }
                }
            }
            Segmented(
                listOf(R.string.alarm_type_state, R.string.alarm_type_price, R.string.alarm_type_percent, R.string.alarm_type_wave).map {
                    stringResource(it)
                },
                ConditionType.entries.indexOf(draft.type),
                { onChange(draft.copy(type = ConditionType.entries[it], value = "")) },
                fontSize = 12f,
            )
            if (draft.type == ConditionType.STATE) {
                StateChoice(draft, asset, onChange)
            } else {
                ValueChoice(draft, asset, onChange)
            }
        }
    }
}

@Composable
private fun StateChoice(
    draft: Draft,
    asset: AssetSnapshot?,
    onChange: (Draft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val parts =
                listOf(com.damla.core.R.string.part_drop, com.damla.core.R.string.part_upper_ring, com.damla.core.R.string.part_lower_ring)
            AlarmPart.entries.forEachIndexed { j, part ->
                PartChip(j, stringResource(parts[j]), draft.part == part, Modifier.weight(1f)) {
                    val target = if (current(asset, part) == draft.target) firstFree(asset, part) else draft.target
                    onChange(draft.copy(part = part, target = target))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val labels =
                listOf(
                    R.string.alarm_target_label_up,
                    R.string.alarm_target_label_down,
                    R.string.alarm_target_label_flat,
                    R.string.alarm_target_label_wave,
                )
            Shape.entries.forEachIndexed { k, shape ->
                val on = draft.target == shape
                val now = current(asset, draft.part) == shape
                TargetCard(shape, stringResource(labels[k]), on, now, Modifier.weight(1f)) { onChange(draft.copy(target = shape)) }
            }
        }
    }
}

/** A part chip: its own part lit in the mark, blue tint and outline when chosen (design `chip()`). */
@Composable
private fun PartChip(
    part: Int,
    label: String,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (on) Color(0x330A84FF) else DamlaColors.field, tween(220, easing = Ease.css), label = "part")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, if (on) Color(0xB30A84FF) else Color.Transparent, shape)
            .semantics { selected = on }
            .tap(onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PartMark(part, DamlaColors.accent, width = 13.dp, height = 16.dp, others = DamlaColors.text30, stroke = 9f)
        Label(
            label,
            text(12f, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) Color.White else DamlaColors.text80),
            maxLines = 1,
        )
    }
}

/** The four target states; the one the part is in now is marked "şu an" and cannot be chosen. */
@Composable
private fun TargetCard(
    shape: Shape,
    label: String,
    on: Boolean,
    now: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (on) 1.06f else 0.94f, tween(300, easing = Ease.press), label = "target")
    val ring = if (on) partColor(shape, grey = false) else DamlaColors.cardLine
    val tile = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(70.dp)
            .clip(tile)
            .background(if (on) Color(0x17FFFFFF) else DamlaColors.card, tile)
            .border(if (on) 1.5.dp else 0.5.dp, ring, tile)
            .semantics { selected = on }
            .tap(enabled = !now, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            ShapeGauge(shape, 24.dp)
            Label(
                label,
                text(11f, FontWeight.SemiBold, if (on) partColor(shape, grey = false) else DamlaColors.secondaryText),
                maxLines = 1,
            )
        }
        if (now) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x24FFFFFF))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(stringResource(R.string.alarm_now), text(9f, FontWeight.Bold, DamlaColors.text85))
            }
        }
    }
}

/** Value with its unit (and the current price as a hint), period for percent and wave, direction. */
@Composable
private fun ValueChoice(
    draft: Draft,
    asset: AssetSnapshot?,
    onChange: (Draft) -> Unit,
) {
    val price = draft.type == ConditionType.PRICE
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val label = stringResource(if (price) R.string.alarm_value_price else R.string.alarm_value_percent)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DamlaColors.field)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(if (price) alarmUnit(asset).trim() else "%", text(20f, FontWeight.Bold, DamlaColors.text55))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (draft.value.isEmpty()) {
                    Label(
                        stringResource(if (price) R.string.alarm_value_price_hint else R.string.alarm_value_percent_hint),
                        text(22f, FontWeight.Bold, DamlaColors.text35),
                    )
                }
                BasicTextField(
                    value = draft.value,
                    onValueChange = { typed -> onChange(draft.copy(value = typed.filter { it.isDigit() || it == ',' || it == '.' })) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = text(22f, FontWeight.Bold),
                    cursorBrush = SolidColor(DamlaColors.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (price && asset?.price != null) {
                Label(stringResource(R.string.alarm_now_value, Formats.price(asset, asset.price!!)), text(12f, color = DamlaColors.text50))
            }
        }
        if (draft.type != ConditionType.PRICE) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (period in Alarm.PERIODS) {
                    PlainChip(stringResource(PERIOD_LABELS.getValue(period)), draft.interval == period, Modifier.weight(1f), 34) {
                        onChange(draft.copy(interval = period))
                    }
                }
            }
        }
        if (draft.type != ConditionType.WAVE) {
            val labels =
                if (price) {
                    listOf(R.string.alarm_dir_above, R.string.alarm_dir_below, R.string.alarm_dir_cross)
                } else {
                    listOf(R.string.alarm_dir_rise, R.string.alarm_dir_fall, R.string.alarm_dir_either)
                }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlarmOp.entries.forEachIndexed { j, op ->
                    PlainChip(stringResource(labels[j]), draft.op == op, Modifier.weight(1f), 38, icon = DIRECTION_ICONS[j]) {
                        onChange(draft.copy(op = op))
                    }
                }
            }
        }
    }
}

private val PERIOD_LABELS =
    mapOf(
        "15m" to R.string.alarm_period_15m,
        "1h" to R.string.alarm_period_1h,
        "1d" to R.string.alarm_period_1d,
        "1w" to R.string.alarm_period_1w,
    )
private val DIRECTION_ICONS = listOf(Icons.UP, Icons.DOWN, Icons.BOTH)

/** The alarm form's chips: blue tint and outline when chosen (design `chip()`), 220 ms. */
@Composable
private fun PlainChip(
    label: String,
    on: Boolean,
    modifier: Modifier,
    height: Int,
    icon: Icons? = null,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (on) Color(0x330A84FF) else DamlaColors.field, tween(220, easing = Ease.css), label = "chip")
    val color by animateColorAsState(if (on) Color.White else DamlaColors.text80, tween(220, easing = Ease.css), label = "chipText")
    val shape = RoundedCornerShape(if (height > 34) 12.dp else 11.dp)
    Row(
        modifier
            .height(height.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, if (on) Color(0xB30A84FF) else Color.Transparent, shape)
            .semantics { selected = on }
            .tap(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, 12.dp, color, 2.8f)
        Label(label, text(12f, if (on) FontWeight.SemiBold else FontWeight.Normal, color), maxLines = 1)
    }
}

/** "Koşul ekle": a dashed blue outline (design: 1.5 px dashed, 18 corner). */
@Composable
private fun AddConditionButton(onClick: () -> Unit) {
    val interaction =
        remember {
            androidx.compose.foundation.interaction
                .MutableInteractionSource()
        }
    Row(
        Modifier
            .pressScale(interaction, 0.95f)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x0F0A84FF))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    Color(0x800A84FF),
                    topLeft =
                        androidx.compose.ui.geometry
                            .Offset(stroke / 2, stroke / 2),
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }.tap(interaction) { onClick() },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.PLUS, 14.dp, DamlaColors.accent, 2.8f)
        Label(stringResource(R.string.alarm_add_condition), text(15f, FontWeight.SemiBold, DamlaColors.accent))
    }
}

@Composable
private fun SegmentRow(
    title: String,
    items: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, text(14f, color = DamlaColors.text85))
        Segmented(items.map { stringResource(it) }, selected, onSelect, height = 32.dp, fontSize = 12f)
    }
}

@Composable
private fun SwitchRow(
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
