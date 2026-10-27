package com.damla.app.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damla.app.R
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.stateWord
import com.damla.gauge.BadgeKind
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Logo
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import kotlinx.coroutines.delay
import kotlin.random.Random

/** One logo choice of the preview. */
private data class LogoChoice(
    @StringRes val name: Int,
    val logo: Logo,
)

private val logos =
    listOf(
        LogoChoice(R.string.logo_gold, Logo.Badge(BadgeKind.GOLD)),
        LogoChoice(R.string.logo_silver, Logo.Badge(BadgeKind.SILVER)),
        LogoChoice(R.string.logo_usd, Logo.Badge(BadgeKind.USD)),
        LogoChoice(R.string.logo_eur, Logo.Badge(BadgeKind.EUR)),
        LogoChoice(R.string.logo_btc, Logo.Badge(BadgeKind.BITCOIN)),
        LogoChoice(R.string.logo_eth, Logo.Text("Ξ")),
        LogoChoice(R.string.logo_gbp, Logo.Text("£")),
        LogoChoice(R.string.logo_try, Logo.Text("₺")),
    )

private val sizes = listOf(16, 24, 32, 48, 64, 96, 120, 160, 240)
private val speeds = listOf(0.25, 0.5, 1.0, 2.0, 3.0)
private val shapes = Shape.entries

/** Interval of simulated prices, as in design/Prototip.dc.html. */
private const val AUTO_PRICE_MS = 400L

/**
 * The gauge preview of phase 3 (docs/09): every state, sizes from 16 to 240, pulses, state changes and a
 * speed multiplier, for comparing the component with the prototype side by side.
 */
@Composable
fun GaugePreviewScreen() {
    var shape by remember { mutableStateOf(Shape.UP) }
    var upper by remember { mutableStateOf(Shape.UP) }
    var lower by remember { mutableStateOf(Shape.DOWN) }
    var closed by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(0) }
    var autoPrices by remember { mutableStateOf(false) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var reduceMotion by remember { mutableStateOf(false) }
    var logo by remember { mutableStateOf(logos.first()) }
    var size by remember { mutableIntStateOf(160) }

    LaunchedEffect(autoPrices) {
        while (autoPrices) {
            delay(AUTO_PRICE_MS)
            turns += if (Random.nextBoolean()) 1 else -1
        }
    }

    val visual = GaugeVisual(shape, upper, lower, closed, turns)
    val description = stringResource(R.string.gauge_description, stringResource(logo.name), stringResource(stateWord(shape, closed)))

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DamlaColors.background)
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title(stringResource(R.string.preview_title))
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Damla(visual, size.dp, logo.logo, description, speed = speed, reduceMotion = reduceMotion)
        }
        Choices(R.string.preview_drop, shapes, shape, { stringResource(stateWord(it, false)) }) { shape = it }
        Choices(R.string.preview_upper_ring, shapes, upper, { stringResource(stateWord(it, false)) }) { upper = it }
        Choices(R.string.preview_lower_ring, shapes, lower, { stringResource(stateWord(it, false)) }) { lower = it }
        Toggle(R.string.preview_closed, closed) { closed = it }
        Section(R.string.preview_pulse) {
            Chip(stringResource(R.string.preview_pulse_up), selected = false) { turns += 1 }
            Chip(stringResource(R.string.preview_pulse_down), selected = false) { turns -= 1 }
        }
        Toggle(R.string.preview_auto_prices, autoPrices) { autoPrices = it }
        Choices(R.string.preview_speed, speeds, speed, { "$it×" }) { speed = it }
        Toggle(R.string.preview_reduce_motion, reduceMotion) { reduceMotion = it }
        Choices(R.string.preview_logo, logos, logo, { stringResource(it.name) }) { logo = it }
        Choices(R.string.preview_size, sizes, size, { "$it" }) { size = it }

        Label(stringResource(R.string.preview_all_sizes))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (each in sizes) Damla(visual, each.dp, logo.logo, description, speed = speed, reduceMotion = reduceMotion)
        }

        Label(stringResource(R.string.preview_all_states))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (each in shapes) {
                val word = stringResource(R.string.gauge_description, stringResource(logo.name), stringResource(stateWord(each, false)))
                Damla(GaugeVisual(each, each, each), 64.dp, logo.logo, word)
            }
            val word = stringResource(R.string.gauge_description, stringResource(logo.name), stringResource(R.string.state_closed))
            Damla(GaugeVisual(Shape.UP, Shape.UP, Shape.UP, closed = true), 64.dp, logo.logo, word)
        }
    }
}

@Composable
private fun <T> Choices(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) = Section(title) {
    for (option in options) Chip(label(option), option == selected) { onSelect(option) }
}

@Composable
private fun Toggle(
    @StringRes title: Int,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) = Section(title) {
    Chip(stringResource(R.string.preview_on), value) { onChange(true) }
    Chip(stringResource(R.string.preview_off), !value) { onChange(false) }
}

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(stringResource(title))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        style = TextStyle(color = DamlaColors.text, fontSize = 14.sp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) DamlaColors.accent else DamlaColors.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Title(text: String) =
    BasicText(text, style = TextStyle(color = DamlaColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold))

@Composable
private fun Label(text: String) = BasicText(text, style = TextStyle(color = DamlaColors.secondaryText, fontSize = 13.sp))
