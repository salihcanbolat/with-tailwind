package com.damla.wear

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.damla.api.AssetSnapshot
import com.damla.core.Formats
import com.damla.core.closed
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.Shape
import com.damla.gauge.android.GaugeBitmaps
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The tile (design/Saat.dc.html, "Kutucuk"): "Damla" at the top, three assets with their 50 drops, short
 * names and state words, and the freshness pill. Positions follow the 300 px design, scaled to the screen.
 */
class DamlaTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        future {
            val shown = WatchData.shown()
            val width = requestParams.deviceConfiguration.screenWidthDp.toFloat()
            TileBuilders.Tile
                .Builder()
                .setResourcesVersion(version(shown))
                // The freshness pill counts seconds and minutes; a minute keeps it close enough.
                .setFreshnessIntervalMillis(60_000)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(this, shown, width / WatchStyle.DESIGN_SIZE)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        future {
            val shown = WatchData.shown()
            val density = requestParams.deviceConfiguration.screenDensity
            val u = requestParams.deviceConfiguration.screenWidthDp / WatchStyle.DESIGN_SIZE
            val resources =
                ResourceBuilders.Resources
                    .Builder()
                    .setVersion(version(shown))
                    .addIdToImageMapping(
                        DROP_ICON,
                        ResourceBuilders.ImageResource
                            .Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId
                                    .Builder()
                                    .setResourceId(R.drawable.ic_drop)
                                    .build(),
                            ).build(),
                    )
            shown.assets.forEachIndexed { i, asset ->
                val visual = asset.visual(shown.settings) ?: return@forEachIndexed
                val bitmap = GaugeBitmaps.render(visual, (GAUGE * u * density).roundToInt(), asset.logo())
                resources.addIdToImageMapping(gaugeId(i), png(bitmap))
            }
            resources.build()
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            val job =
                scope.launch {
                    try {
                        completer.set(block())
                    } catch (e: Throwable) {
                        completer.setException(e)
                    }
                }
            completer.addCancellationListener({ job.cancel() }, Runnable::run)
            "damla-tile"
        }

    private fun layout(
        context: Context,
        shown: WatchData.Shown,
        u: Float,
    ): LayoutElementBuilders.LayoutElement {
        val column =
            LayoutElementBuilders.Column
                .Builder()
                .setWidth(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(spacer(u * HEADER_TOP))
                .addContent(header(u))
                .addContent(spacer(u * (GRID_TOP - HEADER_TOP - HEADER_HEIGHT)))
                .addContent(grid(context, shown, u))
                .addContent(spacer(u * (PILL_TOP - GRID_TOP - GRID_HEIGHT)))
                .addContent(pill(context, shown, u))
                .build()
        return LayoutElementBuilders.Box
            .Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .addContent(column)
            .build()
    }

    /** The drop icon (10 x 12) and "Damla" (13, bold), 5 apart. */
    private fun header(u: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row
            .Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image
                    .Builder()
                    .setResourceId(DROP_ICON)
                    .setWidth(dp(10 * u))
                    .setHeight(dp(12 * u))
                    .build(),
            ).addContent(
                LayoutElementBuilders.Spacer
                    .Builder()
                    .setWidth(dp(5 * u))
                    .build(),
            ).addContent(text(getString(R.string.app_name), 13 * u, LayoutElementBuilders.FONT_WEIGHT_BOLD, WHITE))
            .build()

    /** Three equal columns 30 in from each side: drop (50 x 61), short name (12 bold), word (11, coloured). */
    private fun grid(
        context: Context,
        shown: WatchData.Shown,
        u: Float,
    ): LayoutElementBuilders.LayoutElement {
        val row =
            LayoutElementBuilders.Row
                .Builder()
                .setWidth(dp(u * (WatchStyle.DESIGN_SIZE - 60)))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
        shown.assets.forEachIndexed { i, asset ->
            val grey = asset.closed && shown.settings.grayWhenClosed
            val shape = asset.drop?.shape() ?: Shape.FLAT
            val name = shown.names[asset.id] ?: asset.name
            val description =
                context.getString(
                    R.string.watch_open_detail,
                    context.getString(com.damla.core.R.string.gauge_description, name, context.getString(stateWordLower(shape, grey))),
                )
            row.addContent(
                LayoutElementBuilders.Column
                    .Builder()
                    .setWidth(dp(u * (WatchStyle.DESIGN_SIZE - 60) / 3))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setModifiers(
                        ModifiersBuilders.Modifiers
                            .Builder()
                            .setClickable(open(context, i))
                            .setSemantics(
                                ModifiersBuilders.Semantics
                                    .Builder()
                                    .setContentDescription(description)
                                    .build(),
                            ).build(),
                    ).addContent(
                        LayoutElementBuilders.Image
                            .Builder()
                            .setResourceId(gaugeId(i))
                            .setWidth(dp(GAUGE * u))
                            .setHeight(dp(GAUGE_HEIGHT * u))
                            .build(),
                    ).addContent(spacer(2 * u))
                    .addContent(text(asset.short, 12 * u, LayoutElementBuilders.FONT_WEIGHT_BOLD, WHITE))
                    .addContent(spacer(2 * u))
                    .addContent(
                        text(
                            context.getString(stateWord(shape, grey)),
                            11 * u,
                            LayoutElementBuilders.FONT_WEIGHT_MEDIUM,
                            WatchStyle.bright(shape, grey).toArgb(),
                        ),
                    ).build(),
            )
        }
        return row.build()
    }

    /** The pill (120 x 30, #1F2329, round): a 5 px dot, green while live, grey when partly closed. */
    private fun pill(
        context: Context,
        shown: WatchData.Shown,
        u: Float,
    ): LayoutElementBuilders.LayoutElement {
        val anyClosed = shown.assets.any { it.closed }
        val dot = if (anyClosed) CLOSED_DOT else LIVE_DOT
        return LayoutElementBuilders.Box
            .Builder()
            .setWidth(dp(120 * u))
            .setHeight(dp(30 * u))
            .setModifiers(
                ModifiersBuilders.Modifiers
                    .Builder()
                    .setBackground(
                        ModifiersBuilders.Background
                            .Builder()
                            .setColor(argb(WatchStyle.button.toArgb()))
                            .setCorner(
                                ModifiersBuilders.Corner
                                    .Builder()
                                    .setRadius(dp(15 * u))
                                    .build(),
                            ).build(),
                    ).build(),
            ).addContent(
                LayoutElementBuilders.Row
                    .Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Box
                            .Builder()
                            .setWidth(dp(5 * u))
                            .setHeight(dp(5 * u))
                            .setModifiers(
                                ModifiersBuilders.Modifiers
                                    .Builder()
                                    .setBackground(
                                        ModifiersBuilders.Background
                                            .Builder()
                                            .setColor(argb(dot))
                                            .setCorner(
                                                ModifiersBuilders.Corner
                                                    .Builder()
                                                    .setRadius(dp(2.5f * u))
                                                    .build(),
                                            ).build(),
                                    ).build(),
                            ).build(),
                    ).addContent(
                        LayoutElementBuilders.Spacer
                            .Builder()
                            .setWidth(dp(6 * u))
                            .build(),
                    ).addContent(
                        text(freshness(context, shown.assets, anyClosed), 11 * u, LayoutElementBuilders.FONT_WEIGHT_NORMAL, PILL_TEXT),
                    ).build(),
            ).build()
    }

    /** "şimdi", "N sn önce", "N dk önce", or "kısmen kapalı" while some market is closed. */
    private fun freshness(
        context: Context,
        assets: List<AssetSnapshot>,
        anyClosed: Boolean,
    ): String {
        if (anyClosed) return context.getString(R.string.tile_partly_closed)
        val latest = assets.mapNotNull { it.updatedAt?.let(Instant::parse) }.maxOrNull() ?: return context.getString(R.string.watch_waiting)
        val age = Duration.between(latest, Instant.now()).coerceAtLeast(Duration.ZERO)
        if (age >= Formats.STALE_AFTER) return context.getString(com.damla.core.R.string.fresh_stale)
        return when {
            age.seconds < 2 -> context.getString(R.string.tile_now)
            age.seconds < 60 -> context.getString(R.string.tile_seconds, age.seconds.toInt())
            else -> context.getString(R.string.tile_minutes, age.toMinutes().toInt())
        }
    }

    private fun open(
        context: Context,
        index: Int,
    ): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable
            .Builder()
            .setId("asset$index")
            .setOnClick(
                ActionBuilders.LaunchAction
                    .Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity
                            .Builder()
                            .setPackageName(context.packageName)
                            .setClassName(WatchActivity::class.java.name)
                            .addKeyToExtraMapping(
                                WatchActivity.EXTRA_INDEX,
                                ActionBuilders.AndroidIntExtra
                                    .Builder()
                                    .setValue(index)
                                    .build(),
                            ).build(),
                    ).build(),
            ).build()

    private fun text(
        value: String,
        size: Float,
        weight: Int,
        color: Int,
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text
            .Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle
                    .Builder()
                    .setSize(sp(size))
                    .setWeight(weight)
                    .setColor(argb(color))
                    .build(),
            ).build()

    private fun spacer(height: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer
            .Builder()
            .setHeight(dp(height.coerceAtLeast(0f)))
            .build()

    /** Images are inline PNGs; the version changes with what they show, so the watch fetches new ones. */
    private fun version(shown: WatchData.Shown): String =
        shown.assets
            .joinToString("|") { asset -> asset.id + ":" + asset.visual(shown.settings) }
            .hashCode()
            .toString()

    private fun png(bitmap: Bitmap): ResourceBuilders.ImageResource {
        val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out).let { out.toByteArray() } }
        return ResourceBuilders.ImageResource
            .Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource
                    .Builder()
                    .setData(bytes)
                    .setWidthPx(bitmap.width)
                    .setHeightPx(bitmap.height)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                    .build(),
            ).build()
    }

    private fun gaugeId(index: Int) = "gauge$index"

    private companion object {
        const val DROP_ICON = "drop"
        const val GAUGE = 50f
        const val GAUGE_HEIGHT = 61f
        const val HEADER_TOP = 44f
        const val HEADER_HEIGHT = 16f
        const val GRID_TOP = 80f

        // Drop 61, gap 2, name 15, gap 2, word 14.
        const val GRID_HEIGHT = 94f
        const val PILL_TOP = 224f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val PILL_TEXT = 0xB3FFFFFF.toInt()
        const val LIVE_DOT = 0xFF30D158.toInt()
        const val CLOSED_DOT = 0xFFAEAEB2.toInt()
    }
}
