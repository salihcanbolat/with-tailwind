package com.damla.app.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import com.damla.app.ui.Segmented
import com.damla.app.ui.Separator
import com.damla.app.ui.Toast
import com.damla.app.ui.rememberToast
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.WidgetConfig
import com.damla.core.WidgetOpening
import com.damla.core.WidgetSize
import com.damla.core.WidgetStyle

/** What the widget screen edits; saved only on "Kaydet" (docs/07, taslak/kaydet akışı). */
private data class WidgetDraft(
    val order: List<String>,
    val removed: List<String>,
    val style: WidgetStyle,
    val size: WidgetSize,
    val opening: WidgetOpening,
) {
    fun toConfig() = WidgetConfig(style, size, opening, order)

    companion object {
        fun of(config: WidgetConfig) = WidgetDraft(config.order, emptyList(), config.style, config.size, config.opening)
    }
}

/**
 * "Widget" (design/KlasorIcerik.dc.html): closed and open previews side by side, editing in the preview,
 * style, size and opening, the order, and a draft that is only applied on "Kaydet".
 */
@Composable
fun WidgetScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val saved by model.widget.collectAsState()
    val settings by model.settings.collectAsState()
    model.snapshot.collectAsState().value
    var draft by remember { mutableStateOf<WidgetDraft?>(null) }
    var base by remember { mutableStateOf<WidgetDraft?>(null) }
    LaunchedEffect(saved) {
        if (draft == null || draft == base) {
            draft = WidgetDraft.of(saved)
            base = draft
        }
    }
    val current = draft ?: return
    val dirty = current.toConfig() != (base ?: current).toConfig() || current.removed.isNotEmpty()
    var confirm by remember { mutableStateOf(false) }
    var savedTimes by remember { mutableIntStateOf(0) }
    val toast = rememberToast(savedTimes)

    fun save() {
        val config = current.toConfig()
        model.saveWidget(config)
        draft = WidgetDraft.of(config)
        base = draft
        confirm = false
    }

    fun discard() {
        draft = base
        confirm = false
    }
    BackHandler(enabled = dirty) { confirm = true }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn(top = 0.dp, bottom = 120.dp, gap = 14.dp) {
            NavHeader(
                title = stringResource(R.string.widget_title),
                subtitle = if (dirty) stringResource(R.string.widget_unsaved) else null,
                subtitleColor = DamlaColors.orange,
                back = stringResource(R.string.action_back_panel),
                onBack = { if (dirty) confirm = true else navigator.pop() },
                trailing = {
                    val color by animateColorAsState(
                        if (dirty) DamlaColors.accent else DamlaColors.text30,
                        tween(240, easing = Ease.css),
                        label = "save",
                    )
                    Label(
                        stringResource(R.string.action_save),
                        text(16f, FontWeight.SemiBold, color),
                        Modifier
                            .tap(enabled = dirty) {
                                save()
                                savedTimes++
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                },
            )
            Preview(model, current, settings, onChange = { draft = it }) { navigator.push(Route.Add) }
            GlassCard(radius = 20.dp) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    OptionRow(
                        R.string.widget_style,
                        listOf(R.string.widget_style_bare, R.string.widget_style_boxed),
                        current.style.ordinal,
                    ) {
                        draft = current.copy(style = WidgetStyle.entries[it])
                    }
                    Separator()
                    OptionRow(
                        R.string.widget_size,
                        listOf(R.string.widget_size_mini, R.string.widget_size_wide, R.string.widget_size_single),
                        current.size.ordinal,
                    ) {
                        draft = current.copy(size = WidgetSize.entries[it])
                    }
                    Separator()
                    OptionRow(
                        R.string.widget_opening,
                        listOf(R.string.widget_opening_flow, R.string.widget_opening_folder),
                        current.opening.ordinal,
                    ) {
                        draft = current.copy(opening = WidgetOpening.entries[it])
                    }
                }
            }
            Hints(current)
            Order(model, current, settings, onChange = { draft = it }) { navigator.push(Route.Add) }
        }
        // The draft bar slides up when there is something to save (opacity 260 ms, rise 460 ms).
        val shown by animateFloatAsState(if (dirty) 1f else 0f, tween(460, easing = Ease.standard), label = "bar")
        val fade by animateFloatAsState(if (dirty) 1f else 0f, tween(260, easing = Ease.css), label = "barFade")
        if (fade > 0f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = 26.dp)
                    .graphicsLayer {
                        alpha = fade
                        translationY = (30 * (1 - shown)).dp.toPx()
                    }.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE61E232C))
                    .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton(
                    stringResource(R.string.widget_undo),
                    Modifier.weight(1f),
                    enabled = dirty,
                    background = Color(0x14FFFFFF),
                    color = DamlaColors.text,
                    height = 46.dp,
                ) { discard() }
                PrimaryButton(stringResource(R.string.action_save), Modifier.weight(2f), enabled = dirty, height = 46.dp) {
                    save()
                    savedTimes++
                }
            }
        }
        Toast(toast, stringResource(R.string.widget_saved), Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
        BottomSheet(confirm && dirty, stringResource(R.string.action_close), { confirm = false }) {
            Label(stringResource(R.string.widget_leave_title), text(20f, FontWeight.Bold))
            Label(stringResource(R.string.widget_leave_text), text(14f, color = DamlaColors.text65, lineHeight = 19f))
            PrimaryButton(stringResource(R.string.widget_leave_save)) {
                save()
                navigator.pop()
            }
            PrimaryButton(stringResource(R.string.widget_leave_discard), background = Color(0x24FF453A), color = DamlaColors.red) {
                discard()
                navigator.pop()
            }
            Label(
                stringResource(R.string.widget_leave_stay),
                text(16f, FontWeight.SemiBold, DamlaColors.accent, align = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.fillMaxWidth().tap { confirm = false }.padding(vertical = 12.dp),
            )
        }
    }
}

/** The closed widget, an arrow, and the open 2 x 2 in edit mode on a piece of wallpaper. */
@Composable
private fun Preview(
    model: AppModel,
    draft: WidgetDraft,
    settings: com.damla.core.AppSettings,
    onChange: (WidgetDraft) -> Unit,
    onAdd: () -> Unit,
) {
    val inWidget = draft.order.take(if (draft.size == WidgetSize.SINGLE) 1 else 3)
    val folder = draft.opening == WidgetOpening.FOLDER
    Box(
        Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DamlaColors.wallpaper)
            .padding(bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val gap by animateFloatAsState(if (draft.size == WidgetSize.MINI) 1f else 10f, tween(300, easing = Ease.css), label = "gap")
                val boxed = draft.style == WidgetStyle.BOXED
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (boxed) Color.White else Color.Transparent)
                        .padding(start = if (boxed) 6.dp else 0.dp, end = if (boxed) 6.dp else 0.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (id in inWidget) AssetGauge(model.asset(id), 20.dp, settings)
                }
                Label(stringResource(R.string.widget_preview_label), text(11f, color = Color.White))
            }
            Icon(Icons.ARROW_RIGHT, 14.dp, Color(0x99FFFFFF), 2f, width = 22.dp)
            val gridBackground by animateColorAsState(
                if (folder) Color(0xDBFAFAFC) else Color(0x00FAFAFC),
                tween(300, easing = Ease.css),
                label = "grid",
            )
            val padding by animateFloatAsState(if (folder) 10f else 0f, tween(300, easing = Ease.css), label = "gridPad")
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(gridBackground)
                    .padding(padding.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val cells = draft.order.map { it as String? } + List(WidgetConfig.MAX_ASSETS - draft.order.size) { null }
                for (row in cells.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, id) in row.withIndex()) {
                            val index = cells.indexOf(id).takeIf { id != null } ?: -1
                            if (id == null) {
                                EmptySlot(draft) {
                                    if (draft.removed.isEmpty()) {
                                        onAdd()
                                    } else {
                                        onChange(
                                            draft.copy(
                                                order =
                                                    draft.order + draft.removed.first(),
                                                removed = draft.removed.drop(1),
                                            ),
                                        )
                                    }
                                }
                            } else {
                                EditCell(model, id, index, folder, settings, onPromote = {
                                    onChange(draft.copy(order = listOf(id) + (draft.order - id)))
                                }) {
                                    onChange(draft.copy(order = draft.order - id, removed = draft.removed + id))
                                }
                            }
                            if (k == 0 && row.size == 1) Box(Modifier.size(52.dp, 50.dp))
                        }
                    }
                }
            }
        }
        Label(
            stringResource(R.string.widget_preview_hint),
            text(11f, color = Color(0xB3FFFFFF)),
            Modifier.align(Alignment.BottomCenter).offset(y = 10.dp),
        )
    }
}

/** A drop of the open preview: it jiggles (`dmSalla`, ±1.8°), tap to bring it first, × to remove. */
@Composable
private fun EditCell(
    model: AppModel,
    id: String,
    index: Int,
    folder: Boolean,
    settings: com.damla.core.AppSettings,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    val asset = model.asset(id)
    val name = asset?.name ?: id
    val rotation =
        if (reduceMotion) {
            0f
        } else {
            val transition = rememberInfiniteTransition(label = "jiggle")
            val period = 280 + (index % 2) * 40
            val value by transition.animateFloat(
                -1.8f,
                1.8f,
                infiniteRepeatable(
                    tween(period / 2, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    initialStartOffset =
                        androidx.compose.animation.core
                            .StartOffset(index * 110),
                ),
                label = "jiggle",
            )
            value
        }
    Box(
        Modifier
            .size(52.dp, 50.dp)
            .graphicsLayer {
                rotationZ = rotation
                transformOrigin =
                    androidx.compose.ui.graphics
                        .TransformOrigin(0.5f, 0.6f)
            },
    ) {
        val promote = stringResource(R.string.widget_promote, name)
        Column(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = promote }
                .tap(onClick = onPromote),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            AssetGauge(asset, 26.dp, settings)
            Label(
                asset?.short ?: id,
                text(9f, FontWeight.SemiBold, if (folder) DamlaColors.background else Color.White),
                Modifier.graphicsLayer { alpha = if (index < 3) 1f else 0.6f },
                maxLines = 1,
            )
        }
        val remove = stringResource(R.string.widget_remove, name)
        Box(
            Modifier
                .offset(y = (-6).dp)
                .size(24.dp)
                .semantics { contentDescription = remove }
                .tap(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Color(0xF22C2C30))
                    .border(1.dp, Color(0x47FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.CLOSE, 7.dp, Color.White, 2.2f)
            }
        }
    }
}

/** An empty place: dashed, pops in (`dmBelir`), brings back the last removed drop or opens "Varlık ekle". */
@Composable
private fun EmptySlot(
    draft: WidgetDraft,
    onClick: () -> Unit,
) {
    val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            1f,
            keyframes {
                durationMillis = 420
                1.08f at 294 using Ease.press
            },
        )
    }
    val label = draft.removed.firstOrNull()?.let { stringResource(R.string.widget_readd, it) } ?: stringResource(R.string.widget_add)
    Box(
        Modifier
            .size(52.dp, 50.dp)
            .graphicsLayer {
                val scale = 0.4f + 0.6f * pop.value
                scaleX = scale
                scaleY = scale
                alpha = (pop.value / 0.7f).coerceAtMost(1f)
            }.clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    Color(0x59FFFFFF),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }.semantics { contentDescription = label }
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.PLUS, 14.dp, Color.White, 2.6f)
    }
}

@Composable
private fun OptionRow(
    title: Int,
    items: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(stringResource(title), text(15f), Modifier.width(64.dp))
        Segmented(items.map { stringResource(it) }, selected, onSelect, Modifier.weight(1f), height = 32.dp, fontSize = 12f)
    }
}

/** One line per choice, saying what it does ("Kutusuz: damlalar doğrudan ana ekranda…"). */
@Composable
private fun Hints(draft: WidgetDraft) {
    val lines =
        listOf(
            if (draft.style ==
                WidgetStyle.BARE
            ) {
                R.string.widget_hint_bare_key to R.string.widget_hint_bare
            } else {
                R.string.widget_hint_boxed_key to
                    R.string.widget_hint_boxed
            },
            when (draft.size) {
                WidgetSize.MINI -> R.string.widget_hint_mini_key to R.string.widget_hint_mini
                WidgetSize.WIDE -> R.string.widget_hint_wide_key to R.string.widget_hint_wide
                WidgetSize.SINGLE -> R.string.widget_hint_single_key to R.string.widget_hint_single
            },
            if (draft.opening ==
                WidgetOpening.FLOW
            ) {
                R.string.widget_hint_flow_key to R.string.widget_hint_flow
            } else {
                R.string.widget_hint_folder_key to
                    R.string.widget_hint_folder
            },
        )
    Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((keyRes, lineRes) in lines) {
            val key = stringResource(keyRes)
            val line = stringResource(lineRes)
            androidx.compose.foundation.text.BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = DamlaColors.text85)) { append(key) }
                    append(" ")
                    append(line)
                },
                style = text(12f, color = DamlaColors.secondaryText, lineHeight = 16f),
            )
        }
    }
}

/** The order: the first three (or one) are in the widget, the rest open in the flow; ↑ moves up. */
@Composable
private fun Order(
    model: AppModel,
    draft: WidgetDraft,
    settings: com.damla.core.AppSettings,
    onChange: (WidgetDraft) -> Unit,
    onAdd: () -> Unit,
) {
    val inWidget = if (draft.size == WidgetSize.SINGLE) 1 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            stringResource(R.string.widget_order),
            trailing = stringResource(if (draft.size == WidgetSize.SINGLE) R.string.widget_order_one else R.string.widget_order_three),
        )
        GlassCard(radius = 20.dp) {
            draft.order.forEachIndexed { i, id ->
                if (i > 0) Separator()
                val asset = model.asset(id)
                val name = asset?.name ?: id
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssetGauge(asset, 28.dp, settings)
                    Label(name, text(16f, FontWeight.SemiBold), Modifier.weight(1f), maxLines = 1)
                    val widget = i < inWidget
                    Box(
                        Modifier
                            .height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (widget) Color(0x2E0A84FF) else Color(0x14FFFFFF))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            stringResource(if (widget) R.string.widget_badge_in else R.string.widget_badge_open),
                            text(11f, FontWeight.SemiBold, if (widget) DamlaColors.accentLight else DamlaColors.secondaryText),
                        )
                    }
                    val up = stringResource(R.string.widget_move_up, name)
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer { alpha = if (i == 0) 0f else 1f }
                            .semantics { contentDescription = up }
                            .tap(enabled = i > 0) {
                                val order = draft.order.toMutableList()
                                order[i] = order[i - 1].also { order[i - 1] = order[i] }
                                onChange(draft.copy(order = order))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.CHEVRON_UP, 16.dp, DamlaColors.text70, 2.4f)
                    }
                }
            }
            Separator()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .tap(onClick = onAdd)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x2E0A84FF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.PLUS, 12.dp, DamlaColors.accent, 3f)
                }
                Label(stringResource(R.string.widget_add), text(15f, FontWeight.SemiBold, DamlaColors.accent))
            }
        }
    }
}
