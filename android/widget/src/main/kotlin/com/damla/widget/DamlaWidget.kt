package com.damla.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.damla.api.AssetSnapshot
import com.damla.api.SnapshotResponse
import com.damla.core.AppSettings
import com.damla.core.DamlaServices
import com.damla.core.WidgetConfig
import com.damla.core.WidgetSize
import com.damla.core.WidgetStyle
import com.damla.core.closed
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.android.GaugeBitmaps
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The home-screen widget (docs/06): still drops drawn from the latest snapshot. All motion happens in the
 * overlay that opens on a tap, from the widget's position (`Intent.getSourceBounds`).
 */
class DamlaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ONE_CELL, TWO_CELLS, THREE_CELLS))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository = DamlaServices.repository
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = repository.store.widget(widgetId)
        val snapshot = repository.store.snapshot.first()
        val settings = repository.store.settings.first()
        provideContent { WidgetContent(widgetId, config, snapshot, settings) }
    }

    companion object {
        val ONE_CELL = DpSize(60.dp, 60.dp)
        val TWO_CELLS = DpSize(130.dp, 60.dp)
        val THREE_CELLS = DpSize(200.dp, 60.dp)

        /** The overlay lives in the app module; it reads this extra to find the widget's settings. */
        const val OVERLAY_ACTIVITY = "com.damla.app.overlay.OverlayActivity"
        const val EXTRA_WIDGET_ID = "com.damla.widget.WIDGET_ID"
    }
}

/** Sizes from design/Prototip.dc.html, in dp. */
private object Sizes {
    const val MINI_THREE = 26
    const val MINI_TWO = 32
    const val BARE = 46
    const val BOXED = 38
    const val BOXED_SINGLE = 42

    /** Centre distance of the small drops in a one-cell widget; they overlap slightly. */
    const val MINI_THREE_STEP = 20
    const val MINI_TWO_STEP = 26
}

@Composable
private fun WidgetContent(
    widgetId: Int,
    config: WidgetConfig,
    snapshot: SnapshotResponse?,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val byId = snapshot?.assets.orEmpty().associateBy { it.id }
    val assets = config.inWidget.mapNotNull(byId::get)
    val oneCell = LocalSize.current.width < DamlaWidget.TWO_CELLS.width
    val bare = config.style == WidgetStyle.BARE
    val open =
        Intent()
            .setClassName(context.packageName, DamlaWidget.OVERLAY_ACTIVITY)
            .putExtra(DamlaWidget.EXTRA_WIDGET_ID, widgetId)
    val root =
        GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(open))
            .semantics { contentDescription = description(context, assets, settings) }
            .let { if (bare) it else it.background(Color.White).cornerRadius(if (config.size == WidgetSize.SINGLE) 20.dp else 22.dp) }
    Box(root, contentAlignment = Alignment.Center) {
        when {
            assets.isEmpty() -> Label(context.getString(R.string.widget_waiting), bare)
            oneCell && config.size != WidgetSize.SINGLE -> Mini(assets, settings, bare)
            oneCell -> Single(assets.first(), settings, bare)
            else -> Wide(assets, settings, bare)
        }
    }
}

@Composable
private fun Mini(
    assets: List<AssetSnapshot>,
    settings: AppSettings,
    bare: Boolean,
) {
    val shown = assets.take(3)
    val size = if (shown.size == 2) Sizes.MINI_TWO else Sizes.MINI_THREE
    val step = if (shown.size == 2) Sizes.MINI_TWO_STEP else Sizes.MINI_THREE_STEP
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!bare) Label(LocalContext.current.getString(R.string.widget_mini_label), bare = false)
        Box(contentAlignment = Alignment.Center) {
            shown.forEachIndexed { index, asset ->
                // Glance cannot overlap rows, so each drop is offset inside a centred box by padding twice the shift.
                val shift = ((index - (shown.size - 1) / 2.0) * step).toFloat()
                val modifier =
                    if (shift >=
                        0
                    ) {
                        GlanceModifier.padding(start = (2 * shift).dp)
                    } else {
                        GlanceModifier.padding(end = (-2 * shift).dp)
                    }
                Drop(asset, settings, size, bare, modifier)
            }
        }
        if (bare) Label(LocalContext.current.getString(R.string.widget_mini_label), bare = true)
    }
}

@Composable
private fun Single(
    asset: AssetSnapshot,
    settings: AppSettings,
    bare: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!bare) BoxedLabel(asset)
        Drop(asset, settings, if (bare) Sizes.BARE else Sizes.BOXED_SINGLE, bare)
        if (bare) Label(asset.short, bare = true)
    }
}

@Composable
private fun Wide(
    assets: List<AssetSnapshot>,
    settings: AppSettings,
    bare: Boolean,
) {
    val columns = if (LocalSize.current.width < DamlaWidget.THREE_CELLS.width) 2 else 3
    Row(verticalAlignment = Alignment.CenterVertically) {
        assets.take(columns).forEachIndexed { index, asset ->
            if (index > 0) Spacer(GlanceModifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!bare) BoxedLabel(asset)
                Drop(asset, settings, if (bare) Sizes.BARE else Sizes.BOXED, bare)
                if (bare) Label(asset.short, bare = true)
            }
        }
    }
}

@Composable
private fun Drop(
    asset: AssetSnapshot,
    settings: AppSettings,
    sizeDp: Int,
    bare: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val visual = asset.visual(settings) ?: return
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).roundToInt()
    val heightDp = sizeDp * 92f / 76f
    if (bare) {
        val shadowed = GaugeBitmaps.renderWithShadow(visual, sizePx, asset.logo(), density)
        val margin = shadowed.marginPx / density
        Image(
            provider = ImageProvider(shadowed.bitmap),
            contentDescription = null,
            modifier = modifier.size((sizeDp + 2 * margin).dp, (heightDp + 2 * margin).dp),
        )
    } else {
        Image(
            provider = ImageProvider(GaugeBitmaps.render(visual, sizePx, asset.logo())),
            contentDescription = null,
            modifier = modifier.size(sizeDp.dp, heightDp.dp),
        )
    }
}

/** A white label with a soft shadow on the wallpaper, or a dark one inside the white box. */
@Composable
private fun Label(
    text: String,
    bare: Boolean,
) {
    if (bare) {
        val context = LocalContext.current
        val views = RemoteViews(context.packageName, R.layout.widget_label_bare).apply { setTextViewText(R.id.label, text) }
        AndroidRemoteViews(views)
    } else {
        Text(text, style = TextStyle(color = ColorProvider(DARK_TEXT), fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

/** The boxed style's label: a small coloured badge and the short name (design/Prototip.dc.html). */
@Composable
private fun BoxedLabel(asset: AssetSnapshot) {
    val (background, foreground, symbol) = labelBadge(asset.badge)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(bottom = 2.dp)) {
        Box(
            modifier = GlanceModifier.size(12.dp).background(background).cornerRadius(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = TextStyle(color = ColorProvider(foreground), fontSize = 6.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(3.dp))
        Text(asset.short, style = TextStyle(color = ColorProvider(DARK_TEXT), fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

/** Badge colours of the boxed widget's labels; assets the design does not show get a neutral grey. */
private fun labelBadge(badge: String): Triple<Color, Color, String> =
    when (badge) {
        "au" -> Triple(Color(0xFFF2C230), Color(0xFF5A4300), "Au")
        "usd" -> Triple(Color(0xFF2E7D4F), Color.White, "$")
        "ag" -> Triple(Color(0xFFC7CCD3), Color(0xFF3A4048), "Ag")
        "btc" -> Triple(Color(0xFFF7931A), Color.White, "₿")
        "eur" -> Triple(Color(0xFF003399), Color(0xFFFFCC00), "€")
        else -> Triple(Color(0xFFE5E5EA), DARK_TEXT, badge.take(2).uppercase())
    }

/** "Damla: USD sabit, Altın yükselişte, Bitcoin düşüşte" (docs/06). */
private fun description(
    context: Context,
    assets: List<AssetSnapshot>,
    settings: AppSettings,
): String {
    val items =
        assets.mapNotNull { asset ->
            val drop = asset.drop ?: return@mapNotNull null
            val word = context.getString(stateWordLower(drop.shape(), asset.closed && settings.grayWhenClosed))
            context.getString(R.string.widget_accessibility_item, asset.short, word)
        }
    return context.getString(R.string.widget_accessibility, items.joinToString(context.getString(R.string.widget_accessibility_separator)))
}

private val DARK_TEXT = Color(0xFF1C1C1E)
