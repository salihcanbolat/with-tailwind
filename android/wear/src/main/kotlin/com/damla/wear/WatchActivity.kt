package com.damla.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.damla.api.AssetSnapshot
import com.damla.core.DamlaServices
import com.damla.core.closed
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

/**
 * The watch app (design/Saat.dc.html, "Uygulama"): one asset at a time with its big drop, name, state and
 * the three parts; the crown (or a tap on either side) changes the asset.
 */
class WatchActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INDEX = "com.damla.wear.INDEX"
    }

    private var start by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        start = intent.getIntExtra(EXTRA_INDEX, 0)
        // Alerts are notifications; Wear OS 4+ asks the user once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { WatchScreen(start) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        start = intent.getIntExtra(EXTRA_INDEX, start)
    }
}

@Composable
private fun WatchScreen(start: Int) {
    val store = DamlaServices.repository.store
    val context = LocalContext.current
    var shown by remember { mutableStateOf<WatchData.Shown?>(null) }
    LaunchedEffect(Unit) {
        // Opening the app is a good moment to fetch, phone or not; the store answers at once meanwhile.
        try {
            WatchData.fetch(context.applicationContext)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline: the last stored data stays on screen.
        }
    }
    LaunchedEffect(Unit) {
        combine(store.snapshot, store.settings, store.widgetConfig, store.watchlist) { _, _, _, _ -> }.collect { shown = WatchData.shown() }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val u = maxWidth / WatchStyle.DESIGN_SIZE
        val data = shown
        if (data == null || data.assets.isEmpty()) {
            if (data != null) {
                BasicText(
                    stringResource(R.string.watch_waiting),
                    Modifier.align(Alignment.Center),
                    TextStyle(color = WatchStyle.text60, fontSize = (13 * u.value).sp, textAlign = TextAlign.Center),
                )
            }
            return@BoxWithConstraints
        }
        AssetPager(data, start.coerceIn(0, data.assets.lastIndex), u)
    }
}

@Composable
private fun AssetPager(
    data: WatchData.Shown,
    start: Int,
    u: Dp,
) {
    var index by remember(start) { mutableIntStateOf(start) }
    val count = data.assets.size
    val current = index.coerceIn(0, count - 1)
    val focus = remember { FocusRequester() }
    var scrolled by remember { mutableFloatStateOf(0f) }
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        entry.animateTo(1f, tween(420, easing = WatchStyle.standard))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = entry.value
                val scale = 0.94f + 0.06f * entry.value
                scaleX = scale
                scaleY = scale
            }.onRotaryScrollEvent { event ->
                // One asset per notch-ish turn of the crown.
                scrolled += event.verticalScrollPixels
                if (abs(scrolled) > ROTARY_STEP) {
                    index = (current + if (scrolled > 0) 1 else count - 1) % count
                    scrolled = 0f
                }
                true
            }.focusRequester(focus)
            .focusable(),
    ) {
        AssetPage(data.assets[current], data, u)
        Dots(count, current, u)
        val previous = stringResource(R.string.watch_previous)
        val next = stringResource(R.string.watch_next)
        Box(
            Modifier
                .offset(y = u * 60)
                .size(u * 70, u * 180)
                .semantics { contentDescription = previous }
                .clickable(remember { MutableInteractionSource() }, null) { index = (current + count - 1) % count },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = u * 60)
                .size(u * 70, u * 180)
                .semantics { contentDescription = next }
                .clickable(remember { MutableInteractionSource() }, null) { index = (current + 1) % count },
        )
    }
}

/** 100 drop at (100, 36), name at 164, state at 186, the three parts at 212 (80 in from each side). */
@Composable
private fun AssetPage(
    asset: AssetSnapshot,
    data: WatchData.Shown,
    u: Dp,
) {
    val context = LocalContext.current
    val visual = asset.visual(data.settings) ?: return
    val grey = asset.closed && data.settings.grayWhenClosed
    val shape = asset.drop?.shape() ?: Shape.FLAT
    val name = data.names[asset.id] ?: asset.name
    val word = stringResource(stateWord(shape, grey))
    val wordColor by animateColorAsState(WatchStyle.bright(shape, grey), tween(400, easing = WatchStyle.ease), label = "word")
    val description =
        context.getString(
            R.string.watch_open_detail,
            context.getString(com.damla.core.R.string.gauge_description, name, context.getString(stateWordLower(shape, grey))),
        )
    Box(Modifier.fillMaxSize()) {
        Damla(visual, u * 100, asset.logo(), description, Modifier.offset(u * 100, u * 36))
        BasicText(
            name,
            Modifier.fillMaxWidth().offset(y = u * 164),
            TextStyle(color = Color.White, fontSize = (17 * u.value).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        BasicText(
            word,
            Modifier.fillMaxWidth().offset(y = u * 186),
            TextStyle(color = wordColor, fontSize = (13 * u.value).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        Row(
            Modifier.fillMaxWidth().offset(y = u * 212).padding(horizontal = u * 80),
            horizontalArrangement = Arrangement.spacedBy(u * 4),
        ) {
            val parts = listOf(asset.drop, asset.upperRing, asset.lowerRing)
            val labels = listOf(R.string.watch_part_drop, R.string.watch_part_upper, R.string.watch_part_lower)
            parts.forEachIndexed { i, part ->
                val color by animateColorAsState(
                    WatchStyle.bright(part?.shape() ?: Shape.FLAT, grey),
                    tween(400, easing = WatchStyle.ease),
                    label = "part",
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(u * 3),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(u * 5)
                            .background(color, RoundedCornerShape(u * 3)),
                    )
                    BasicText(
                        stringResource(labels[i]),
                        style = TextStyle(color = WatchStyle.text55, fontSize = (9 * u.value).sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Page dots at 256: the current one 14 wide and white, the others 5 wide (300 ms). */
@Composable
private fun Dots(
    count: Int,
    current: Int,
    u: Dp,
) {
    Row(
        Modifier.fillMaxWidth().offset(y = u * 256),
        horizontalArrangement = Arrangement.spacedBy(u * 5, Alignment.CenterHorizontally),
    ) {
        for (i in 0 until count) {
            val on = i == current
            val width by animateDpAsState(if (on) u * 14 else u * 5, tween(300, easing = WatchStyle.ease), label = "dot")
            val color by animateColorAsState(
                if (on) Color.White else WatchStyle.dotOff,
                tween(300, easing = WatchStyle.ease),
                label = "dotColor",
            )
            Box(
                Modifier
                    .width(width)
                    .height(u * 5)
                    .background(color, RoundedCornerShape(u * 3)),
            )
        }
    }
}

private const val ROTARY_STEP = 48f
