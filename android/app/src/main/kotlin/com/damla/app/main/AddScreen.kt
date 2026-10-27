package com.damla.app.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.api.CatalogEntry
import com.damla.app.R
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.Ease
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icon
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.NavHeader
import com.damla.app.ui.Segmented
import com.damla.app.ui.Separator
import com.damla.app.ui.pressScale
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.core.Formats
import com.damla.gauge.Shape

/** "Varlık ekle" (design/Ekle.dc.html): search, categories, add or remove in place, the way to cross rates. */
@Composable
fun AddScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val watchlist by model.watchlist.collectAsState()
    val settings by model.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { model.loadCatalog() }
    val catalog = model.catalog
    val kinds = listOf(null, "fx", "metal", "crypto", null)
    val needle = query.trim().lowercase(Formats.TURKISH)
    val cross = category == CROSS
    val items =
        catalog
            ?.assets
            .orEmpty()
            .filter { kinds[category] == null || it.kind == kinds[category] }
            .filter {
                needle.isEmpty() || it.name.lowercase(Formats.TURKISH).contains(needle) ||
                    it.short.lowercase(Formats.TURKISH).contains(needle)
            }.take(if (cross) 3 else Int.MAX_VALUE)

    ScreenColumn(top = 0.dp, bottom = 32.dp, gap = 14.dp) {
        NavHeader(
            title = stringResource(R.string.add_title),
            subtitle = stringResource(R.string.add_count, watchlist.size),
            trailing = {
                Label(
                    stringResource(R.string.action_done),
                    text(16f, FontWeight.SemiBold, DamlaColors.accent),
                    Modifier.tap { navigator.pop() }.padding(vertical = 12.dp),
                )
            },
        )
        SearchField(query) { query = it }
        Segmented(
            listOf(
                R.string.add_all,
                R.string.add_fx,
                R.string.add_metal,
                R.string.add_crypto,
                R.string.add_cross,
            ).map { stringResource(it) },
            category,
            { category = it },
        )
        if (!model.online) {
            StatusBanner(stringResource(R.string.state_offline), action = stringResource(R.string.state_retry)) {
                model.loadCatalog()
            }
        }
        if (cross) CrossEntry(model, settings) { navigator.push(Route.Cross) }
        GlassCard {
            items.forEachIndexed { i, entry ->
                if (i > 0) Separator()
                CatalogRow(model, entry, settings, entry.id in watchlist.map { it.id })
            }
            if (catalog != null && items.isEmpty()) {
                Label(
                    stringResource(R.string.add_none),
                    text(14f, color = DamlaColors.text55, align = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            if (catalog == null && model.online) {
                Label(
                    stringResource(R.string.add_loading),
                    text(14f, color = DamlaColors.text55, align = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

private const val CROSS = 4

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
) {
    val label = stringResource(R.string.add_search_label)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DamlaColors.control)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.SEARCH, 16.dp, DamlaColors.text50, 2.4f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Label(stringResource(R.string.add_search), text(16f, color = DamlaColors.text45), maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = text(16f),
                cursorBrush = SolidColor(DamlaColors.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

/** The Çapraz tab's entry: two drops with ÷ between them. */
@Composable
private fun CrossEntry(
    model: AppModel,
    settings: com.damla.core.AppSettings,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .pressScale(interaction, 0.97f)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x1F0A84FF), shape)
            .border(0.5.dp, Color(0x660A84FF), shape)
            .tap(interaction) { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetGauge(model.asset("xau_gram"), 34.dp, settings)
            Label("÷", text(20f, FontWeight.Light, DamlaColors.secondaryText), Modifier.padding(horizontal = 2.dp))
            AssetGauge(model.asset("xag_gram"), 34.dp, settings)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(stringResource(R.string.add_cross_title), text(16f, FontWeight.Bold))
            Label(stringResource(R.string.add_cross_hint), text(12f, color = DamlaColors.text65, lineHeight = 16f))
        }
        Chevron(DamlaColors.accent)
    }
}

/** An asset of the catalogue with the round add (+, blue) or remove (✓, green) button. */
@Composable
private fun CatalogRow(
    model: AppModel,
    entry: CatalogEntry,
    settings: com.damla.core.AppSettings,
    watched: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        if (watched) DamlaColors.green else DamlaColors.accent,
        tween(260, easing = Ease.css),
        label = "add",
    )
    val label = stringResource(if (watched) R.string.add_on else R.string.add_off, entry.name)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            val asset = model.asset(entry.id)
            if (asset != null) AssetGauge(asset, 30.dp, settings) else ShapeGauge(Shape.FLAT, 30.dp, grey = true)
        }
        Column(Modifier.weight(1f)) {
            Label(entry.name, text(16f, FontWeight.SemiBold), maxLines = 1)
            Label(subtitle(entry), text(12f, color = DamlaColors.text55), maxLines = 1)
        }
        Box(
            Modifier
                .size(44.dp)
                .semantics { contentDescription = label }
                .tap(interaction) { if (watched) model.remove(entry.id) else model.add(entry.id) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .pressScale(interaction, 0.9f)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (watched) Icons.CHECK else Icons.PLUS, 14.dp, Color.White, 3f)
            }
        }
    }
}

@Composable
fun subtitle(entry: CatalogEntry): String =
    when (entry.kind) {
        "fx" -> stringResource(R.string.add_sub_fx)
        "metal" -> stringResource(R.string.add_sub_metal, entry.currency)
        else -> stringResource(R.string.add_sub_crypto, entry.currency)
    }
