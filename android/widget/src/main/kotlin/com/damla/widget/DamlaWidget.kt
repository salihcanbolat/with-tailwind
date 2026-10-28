package com.damla.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
import com.damla.core.labelBadge
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.shownAssets
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.Geometry
import com.damla.gauge.OverlayGeometry
import com.damla.gauge.WidgetArrangement
import com.damla.gauge.android.GaugeBitmaps
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The home-screen widget (docs/06): still drops drawn from the latest snapshot. All motion happens in the
 * overlay that opens on a tap, from the widget's position (`Intent.getSourceBounds`). Glance's own click
 * actions make immutable PendingIntents, which the launcher cannot add that position to, so the widget
 * sets its tap on a plain view ([clickArea]).
 */
class DamlaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ONE_CELL, TWO_CELLS, THREE_CELLS))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val store = DamlaServices.repository.store
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = store.widget(widgetId)
        val snapshot = store.snapshot.first()
        val settings = store.settings.first()
        // Followed inside the composition as well: while a session runs, an update only recomposes it, so the
        // values read once above would stay as they were (a saved style, a new snapshot).
        provideContent {
            WidgetContent(
                widgetId,
                store.widgetConfig.collectAsState(config).value,
                store.snapshot.collectAsState(snapshot).value,
                store.settings.collectAsState(settings).value,
            )
        }
    }

    companion object {
        val ONE_CELL = DpSize(60.dp, 60.dp)
        val TWO_CELLS = DpSize(WidgetArrangement.TWO_CELLS_WIDTH.toFloat().dp, 60.dp)
        val THREE_CELLS = DpSize(WidgetArrangement.THREE_CELLS_WIDTH.toFloat().dp, 60.dp)

        /** The overlay lives in the app module; it reads this extra to find the widget's settings. */
        const val OVERLAY_ACTIVITY = "com.damla.app.overlay.OverlayActivity"
        const val EXTRA_WIDGET_ID = "com.damla.widget.WIDGET_ID"
    }
}

@Composable
private fun WidgetContent(
    widgetId: Int,
    config: WidgetConfig,
    snapshot: SnapshotResponse?,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val assets = snapshot.shownAssets(config.inWidget, settings)
    val bare = config.style == WidgetStyle.BARE
    val arrangement =
        WidgetArrangement.of(
            LocalSize.current.width.value
                .toDouble(),
            bare,
            single = config.size == WidgetSize.SINGLE,
            available = assets.size,
        )
    val shown = assets.take(arrangement.drops)
    val area = clickArea(context, widgetId, description(context, shown, settings))
    Box(GlanceModifier.fillMaxSize()) {
        AndroidRemoteViews(area, R.id.click_area, GlanceModifier.fillMaxSize()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!bare) Card(arrangement)
                if (shown.isEmpty()) {
                    Label(context.getString(R.string.widget_waiting), bare)
                } else {
                    Block(arrangement, shown, settings)
                }
            }
        }
    }
}

/**
 * The boxed style's white card, as in the prototype: as tall as the block (76) and 10 in from the cell's
 * sides, centred in the widget. The overlay starts from the same rectangle (OverlayGeometry.forWidget).
 */
@Composable
private fun Card(arrangement: WidgetArrangement) {
    val inset = OverlayGeometry.CARD_INSET.toFloat().dp
    Box(GlanceModifier.fillMaxWidth().height(arrangement.blockHeight.toFloat().dp).padding(horizontal = inset)) {
        Box(GlanceModifier.fillMaxSize().background(Color.White).cornerRadius(BOX_RADIUS.dp)) {}
    }
}

/**
 * A view over the whole widget that opens the overlay. Its PendingIntent is mutable so the launcher's
 * fill-in reaches the overlay with the widget's bounds; it names the overlay, so nothing else can be started
 * with it. One request code per widget keeps each widget's id in its own PendingIntent.
 */
private fun clickArea(
    context: Context,
    widgetId: Int,
    description: String,
): RemoteViews {
    val open =
        Intent()
            .setClassName(context.packageName, DamlaWidget.OVERLAY_ACTIVITY)
            .putExtra(DamlaWidget.EXTRA_WIDGET_ID, widgetId)
    val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    val intent = PendingIntent.getActivity(context, widgetId, open, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
    return RemoteViews(context.packageName, R.layout.widget_click_area).apply {
        setOnClickPendingIntent(R.id.click_area, intent)
        setContentDescription(R.id.click_area, description)
    }
}

/**
 * The prototype's fixed block of drops and labels ([WidgetArrangement]), centred in the widget. It has
 * [BLOCK_PADDING] above and below so the drops' shadows fit; the overlay computes the same positions.
 */
@Composable
private fun Block(
    arrangement: WidgetArrangement,
    assets: List<AssetSnapshot>,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bare = arrangement.bare
    val size = arrangement.dropSize
    val heightDp = size * Geometry.HEIGHT / Geometry.WIDTH
    val margin = if (bare) GaugeBitmaps.shadowMarginPx(density) / density else 0f
    val dropTop = (BLOCK_PADDING + arrangement.dropCentreY - heightDp / 2 - margin).toFloat()
    val labelTop = (BLOCK_PADDING + arrangement.labelTop).toFloat()
    Box(GlanceModifier.fillMaxWidth().height((arrangement.blockHeight + 2 * BLOCK_PADDING).toFloat().dp)) {
        Columns(arrangement, GlanceModifier.padding(top = labelTop.dp)) { k ->
            when {
                arrangement.kind == WidgetArrangement.Kind.MINI -> Label(context.getString(R.string.widget_mini_label), bare)
                bare -> Label(assets[k].short, bare = true)
                else -> BoxedLabel(assets[k])
            }
        }
        Columns(arrangement, GlanceModifier.padding(top = dropTop.dp)) { k ->
            if (arrangement.kind == WidgetArrangement.Kind.MINI) {
                MiniDrops(assets, settings, size.toFloat(), arrangement.miniStep.toFloat(), bare)
            } else if (k < assets.size) {
                Drop(assets[k], settings, size.toFloat(), bare)
            }
        }
    }
}

/** One centred slot for mini and single widgets, equal columns for wide ones. */
@Composable
private fun Columns(
    arrangement: WidgetArrangement,
    modifier: GlanceModifier,
    content: @Composable (Int) -> Unit,
) {
    if (arrangement.kind == WidgetArrangement.Kind.WIDE) {
        Row(modifier.fillMaxWidth()) {
            for (k in 0 until arrangement.columns) {
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.TopCenter) { content(k) }
            }
        }
    } else {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content(0) }
    }
}

/**
 * The mini widget's overlapping drops, [stepDp] apart, as one image. Glance cannot overlap views, and boxes
 * padded apart do not fit one cell: the launcher squeezes them off centre. Later drops lie on earlier ones and
 * all have the simple badge, as in the overlay (OverlayScene).
 */
@Composable
private fun MiniDrops(
    assets: List<AssetSnapshot>,
    settings: AppSettings,
    sizeDp: Float,
    stepDp: Float,
    bare: Boolean,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).roundToInt()
    val stepPx = (stepDp * density).roundToInt()
    val drops =
        assets.mapNotNull { asset ->
            val visual = asset.visual(settings) ?: return@mapNotNull null
            if (bare) {
                GaugeBitmaps.renderWithShadow(visual, sizePx, asset.logo(), density, smallBadge = true).bitmap
            } else {
                GaugeBitmaps.render(visual, sizePx, asset.logo(), smallBadge = true)
            }
        }
    if (drops.isEmpty()) return
    val image = Bitmap.createBitmap(drops[0].width + (drops.size - 1) * stepPx, drops[0].height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    drops.forEachIndexed { index, drop ->
        canvas.drawBitmap(drop, (index * stepPx).toFloat(), 0f, null)
        drop.recycle()
    }
    Image(
        provider = ImageProvider(image),
        contentDescription = null,
        modifier = GlanceModifier.size((image.width / density).dp, (image.height / density).dp),
    )
}

@Composable
private fun Drop(
    asset: AssetSnapshot,
    settings: AppSettings,
    sizeDp: Float,
    bare: Boolean,
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
            modifier = GlanceModifier.size((sizeDp + 2 * margin).dp, (heightDp + 2 * margin).dp),
        )
    } else {
        Image(
            provider = ImageProvider(GaugeBitmaps.render(visual, sizePx, asset.logo())),
            contentDescription = null,
            modifier = GlanceModifier.size(sizeDp.dp, heightDp.dp),
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
    val badge = labelBadge(asset.badge)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(bottom = 2.dp)) {
        Box(
            modifier = GlanceModifier.size(12.dp).background(Color(badge.background)).cornerRadius(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge.symbol,
                style = TextStyle(color = ColorProvider(Color(badge.foreground)), fontSize = 6.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.width(3.dp))
        Text(asset.short, style = TextStyle(color = ColorProvider(DARK_TEXT), fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
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

/** Corner radius of the white box (design/Prototip.dc.html). */
private const val BOX_RADIUS = 22f

/** Room above and below the block for the drops' shadows. */
private const val BLOCK_PADDING = 10.0
