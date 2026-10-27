package com.damla.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.damla.core.WearContract
import com.damla.core.WearEvent
import com.damla.core.logo
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.android.CssBlur
import com.damla.gauge.android.Damla
import java.util.concurrent.Executors

/**
 * The alert screen (design/Saat.dc.html, "Uyarı ekranı"): a soft glow in the event's colour behind the
 * drop, the asset's name, the message, "Tamam" and "Telefonda aç".
 */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val event =
            intent
                .getStringExtra(WatchAlerts.EXTRA_EVENT)
                ?.let { runCatching { WearContract.json.decodeFromString(WearEvent.serializer(), it) }.getOrNull() }
        if (event == null) {
            finish()
            return
        }
        setContent { AlertScreen(event, onDone = ::finish, onPhone = { openOnPhone(event) }) }
    }

    /** Opens the asset (a turn) or the alarms (an alarm) in the phone app, then closes. */
    private fun openOnPhone(event: WearEvent) {
        val uri = if (event.kind == WearEvent.Kind.ALARM) "damla://alarms" else "damla://asset/${event.asset}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)
        RemoteActivityHelper(this, Executors.newSingleThreadExecutor()).startRemoteActivity(intent)
        finish()
    }
}

@Composable
private fun AlertScreen(
    event: WearEvent,
    onDone: () -> Unit,
    onPhone: () -> Unit,
) {
    var shown by remember { mutableStateOf<WatchData.Shown?>(null) }
    var name by remember { mutableStateOf(event.asset) }
    LaunchedEffect(event) {
        shown = WatchData.shown()
        name = WatchAlerts.name(event.asset)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = WatchAlerts.shape(event)
    val color = if (shape != null) WatchStyle.bright(shape, false) else WatchStyle.accent
    val asset = shown?.assets?.firstOrNull { it.id == event.asset }
    val settings = shown?.settings
    // The event's shape is the engine's; the stored snapshot may not have caught up with it yet.
    val visual =
        settings?.let { asset?.visual(it) }?.let { visual ->
            if (event.kind == WearEvent.Kind.STATE && shape != null) visual.copy(shape = shape) else visual
        }
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, tween(360, easing = WatchStyle.standard)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = entry.value
                val scale = 0.94f + 0.06f * entry.value
                scaleX = scale
                scaleY = scale
            },
    ) {
        val u = maxWidth / WatchStyle.DESIGN_SIZE
        val density = LocalDensity.current
        val sigmaPx = with(density) { (u * 46).toPx() }
        Box(
            Modifier
                .offset(u * 70, u * 30)
                .size(u * 160)
                .blur(with(density) { CssBlur.filterRadius(sigmaPx).toDp() }, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = 0.5f }
                .background(color, CircleShape),
        )
        if (visual != null && asset != null) {
            val description =
                context.getString(
                    com.damla.core.R.string.gauge_description,
                    name,
                    context.getString(stateWordLower(visual.shape, visual.closed)),
                )
            Damla(visual, u * 92, asset.logo(), description, Modifier.offset(u * 104, u * 30))
        }
        BasicText(
            name,
            Modifier.fillMaxWidth().offset(y = u * 148),
            TextStyle(
                color = WatchStyle.text60,
                fontSize = (13 * u.value).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            WatchAlerts.message(context, event, name),
            Modifier.fillMaxWidth().offset(y = u * 166).padding(horizontal = u * 40),
            TextStyle(
                color = color,
                fontSize = (18 * u.value).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.2.em,
                textAlign = TextAlign.Center,
            ),
        )
        Row(
            Modifier.fillMaxWidth().offset(y = u * 218),
            horizontalArrangement = Arrangement.spacedBy(u * 14, Alignment.CenterHorizontally),
        ) {
            RoundButton(u, stringResource(R.string.alert_ok), onDone) { CheckIcon(u * 18) }
            RoundButton(u, stringResource(R.string.alert_phone), onPhone) { PhoneIcon(u * 16, u * 18) }
        }
    }
}

@Composable
private fun RoundButton(
    u: Dp,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(u * 46)
            .clip(CircleShape)
            .background(WatchStyle.button)
            .semantics {
                contentDescription = label
                role = Role.Button
            }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** `M5 12.5l4.5 4.5L19 7.5`, stroke 2.6 of 24. */
@Composable
private fun CheckIcon(size: Dp) {
    val path = remember { PathParser().parsePathString("M5 12.5l4.5 4.5L19 7.5").toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, Color.White, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The phone: a 12 x 19 rounded rectangle (corner 2.5) and its button line, stroke 2.2 of 24. */
@Composable
private fun PhoneIcon(
    width: Dp,
    height: Dp,
) {
    Canvas(Modifier.size(width, height)) {
        val scale = minOf(size.width, size.height) / 24f
        val left = (size.width - 24 * scale) / 2
        val top = (size.height - 24 * scale) / 2
        val stroke = Stroke(2.2f * scale, cap = StrokeCap.Round)
        drawRoundRect(
            Color.White,
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(left + 6 * scale, top + 2.5f * scale),
            size =
                androidx.compose.ui.geometry
                    .Size(12 * scale, 19 * scale),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(2.5f * scale),
            style = stroke,
        )
        drawLine(
            Color.White,
            androidx.compose.ui.geometry
                .Offset(left + 11 * scale, top + 18.5f * scale),
            androidx.compose.ui.geometry
                .Offset(left + 13 * scale, top + 18.5f * scale),
            strokeWidth = 2.2f * scale,
            cap = StrokeCap.Round,
        )
    }
}
