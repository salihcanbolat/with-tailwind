package com.damla.app.status

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.damla.api.AssetSettings
import com.damla.api.AssetSnapshot
import com.damla.api.SnapshotResponse
import com.damla.app.MainActivity
import com.damla.app.R
import com.damla.app.main.grey
import com.damla.app.overlay.OverlayColors
import com.damla.core.AppSettings
import com.damla.core.Formats
import com.damla.core.StatusConfig
import com.damla.core.WidgetConfig
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.shownAssets
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.Shape
import com.damla.gauge.android.GaugeBitmaps
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The ongoing notification (docs/08, design/Bildirim.dc.html). No foreground service: an ongoing
 * notification redrawn whenever the stored snapshot changes, which pushes and the periodic sync keep fresh
 * (docs/kararlar.md, "Platform kararları"). The system draws the header (icon, "Damla", freshness, time,
 * expand arrow) and the buttons; the content is ours.
 */
object StatusNotification {
    private const val CHANNEL = "status"
    private const val ID = 0x0da1a

    const val ACTION_PAUSE = "com.damla.app.status.PAUSE"
    const val ACTION_DISMISSED = "com.damla.app.status.DISMISSED"

    /** Everything the notification shows; a change of any redraws it. */
    data class Input(
        val snapshot: SnapshotResponse?,
        val settings: AppSettings,
        val status: StatusConfig,
        val widget: WidgetConfig,
        val watchlist: List<AssetSettings>,
    )

    /** What is on screen; while paused the notification stays as it was until the settings change. */
    private var shown: StatusConfig? = null

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_status_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(
        context: Context,
        input: Input,
    ) {
        val status = input.status
        val manager = NotificationManagerCompat.from(context)
        if (!status.enabled) {
            manager.cancel(ID)
            shown = null
            return
        }
        if (status.paused && status == shown) return
        if (!allowed(context)) return
        try {
            manager.notify(ID, build(context, input))
            shown = status
        } catch (_: SecurityException) {
            // The permission was taken back in between; the switch in Ayarlar still says what the user wants.
        }
    }

    private fun build(
        context: Context,
        input: Input,
    ): android.app.Notification {
        val (snapshot, settings, status, widget, watchlist) = input
        val assets = snapshot.shownAssets(status.shown(widget), settings)
        val open = activity(context, 1, Intent(context, MainActivity::class.java))
        val edit = activity(context, 2, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_STATUS_SETTINGS, true))
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(icon(assets.firstOrNull()))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(summary(context, assets, settings))
                .setSubText(label(context, assets, status, Instant.now()))
                .setShowWhen(!status.paused && assets.isNotEmpty())
                .setWhen(latest(assets))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open)
                .setDeleteIntent(broadcast(context, 4, ACTION_DISMISSED))
                .addAction(0, context.getString(R.string.status_open), open)
                .addAction(0, context.getString(R.string.status_edit), edit)
                .addAction(
                    0,
                    context.getString(if (status.paused) R.string.status_resume else R.string.status_pause),
                    broadcast(context, 3, ACTION_PAUSE),
                )
        if (assets.isEmpty()) return builder.build()

        // Lock screen with hidden content: only the shapes, no names (docs/08).
        val public =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(icon(assets.first()))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed(context, assets, settings, watchlist, names = false))
                .setOngoing(true)
                .build()
        return builder
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed(context, assets, settings, watchlist, names = true))
            .setCustomBigContentView(expanded(context, assets, settings, watchlist))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
    }

    /** Collapsed: three assets side by side, 30 drop, short name and the coloured state word. */
    private fun collapsed(
        context: Context,
        assets: List<AssetSnapshot>,
        settings: AppSettings,
        watchlist: List<AssetSettings>,
        names: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.status_collapsed)
        assets.forEachIndexed { i, asset ->
            val shape = asset.drop?.shape() ?: Shape.FLAT
            val grey = asset.grey(settings)
            val cell = RemoteViews(context.packageName, R.layout.status_cell)
            cell.setImageViewBitmap(R.id.gauge, gauge(context, asset, settings, COLLAPSED_GAUGE, logo = names))
            if (i > 0) cell.setViewPadding(R.id.cell, px(context, CELL_GAP), 0, 0, 0)
            if (names) {
                cell.setTextViewText(R.id.name, asset.short)
                cell.setTextViewText(R.id.word, context.getString(stateWord(shape, grey)))
                cell.setTextColor(R.id.word, OverlayColors.bright(shape, grey).toArgb())
                cell.setContentDescription(R.id.cell, description(context, asset, settings, watchlist))
            } else {
                cell.setViewVisibility(R.id.texts, View.GONE)
                cell.setContentDescription(
                    R.id.cell,
                    context.getString(R.string.status_hidden, i + 1, context.getString(stateWordLower(shape, grey))),
                )
            }
            views.addView(R.id.cells, cell)
        }
        return views
    }

    /** Expanded: a row per asset with 40 drop, name, price · word and the three parts' colours. */
    private fun expanded(
        context: Context,
        assets: List<AssetSnapshot>,
        settings: AppSettings,
        watchlist: List<AssetSettings>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.status_expanded)
        for (asset in assets) {
            val shape = asset.drop?.shape() ?: Shape.FLAT
            val grey = asset.grey(settings)
            val word = context.getString(stateWord(shape, grey))
            val price = asset.price?.let { Formats.price(asset, it) } ?: "—"
            val line =
                SpannableStringBuilder(context.getString(R.string.status_line, price, "")).apply {
                    val start = length
                    append(word)
                    setSpan(
                        ForegroundColorSpan(OverlayColors.bright(shape, grey).toArgb()),
                        start,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            val row = RemoteViews(context.packageName, R.layout.status_row)
            row.setImageViewBitmap(R.id.gauge, gauge(context, asset, settings, EXPANDED_GAUGE, logo = true))
            row.setTextViewText(R.id.name, name(asset, watchlist))
            row.setTextViewText(R.id.line, line)
            row.setImageViewBitmap(R.id.parts, parts(context, asset, grey))
            row.setContentDescription(R.id.parts, partsDescription(context, asset, grey))
            row.setContentDescription(R.id.gauge, description(context, asset, settings, watchlist))
            views.addView(R.id.rows, row)
        }
        return views
    }

    private fun gauge(
        context: Context,
        asset: AssetSnapshot,
        settings: AppSettings,
        sizeDp: Float,
        logo: Boolean,
    ): Bitmap {
        val visual = requireNotNull(asset.visual(settings))
        return GaugeBitmaps.render(visual, px(context, sizeDp), if (logo) asset.logo() else null)
    }

    /** The three parts' short bars (22 x 6, 4 apart, rounded), each in its state's bright colour. */
    private fun parts(
        context: Context,
        asset: AssetSnapshot,
        grey: Boolean,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(px(context, PARTS_WIDTH), px(context, BAR_HEIGHT), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = BAR_HEIGHT / 2 * density
        listOf(asset.drop, asset.upperRing, asset.lowerRing).forEachIndexed { i, part ->
            paint.color = OverlayColors.bright(part?.shape() ?: Shape.FLAT, grey).toArgb()
            val left = i * (BAR_WIDTH + BAR_GAP) * density
            canvas.drawRoundRect(RectF(left, 0f, left + BAR_WIDTH * density, BAR_HEIGHT * density), radius, radius, paint)
        }
        return bitmap
    }

    private fun partsDescription(
        context: Context,
        asset: AssetSnapshot,
        grey: Boolean,
    ): String {
        val words =
            listOf(
                asset.drop,
                asset.upperRing,
                asset.lowerRing,
            ).map { context.getString(stateWordLower(it?.shape() ?: Shape.FLAT, grey)) }
        return context.getString(R.string.status_parts, words[0], words[1], words[2])
    }

    /** "Altın, yükselişte" (CLAUDE.md: every drop has a description). */
    private fun description(
        context: Context,
        asset: AssetSnapshot,
        settings: AppSettings,
        watchlist: List<AssetSettings>,
    ): String {
        val shape = asset.drop?.shape() ?: Shape.FLAT
        return context.getString(
            com.damla.core.R.string.gauge_description,
            name(asset, watchlist),
            context.getString(stateWordLower(shape, asset.grey(settings))),
        )
    }

    private fun summary(
        context: Context,
        assets: List<AssetSnapshot>,
        settings: AppSettings,
    ): String =
        if (assets.isEmpty()) {
            context.getString(R.string.state_no_data)
        } else {
            assets.joinToString(", ") { asset ->
                val shape = asset.drop?.shape() ?: Shape.FLAT
                context.getString(
                    com.damla.core.R.string.gauge_description,
                    asset.short,
                    context.getString(stateWordLower(shape, asset.grey(settings))),
                )
            }
        }

    /** The header's freshness: "canlı", "veri gecikiyor", "piyasa kapalı" or "duraklatıldı". */
    private fun label(
        context: Context,
        assets: List<AssetSnapshot>,
        status: StatusConfig,
        now: Instant,
    ): String {
        val open = assets.filter { it.marketOpen }
        val res =
            when {
                status.paused -> R.string.status_paused
                assets.isNotEmpty() && open.isEmpty() -> R.string.status_closed
                open.any { Formats.freshness(context, it, now).stale } -> R.string.status_late
                else -> R.string.status_live
            }
        return context.getString(res)
    }

    private fun latest(assets: List<AssetSnapshot>): Long =
        assets
            .mapNotNull { it.updatedAt?.let(Instant::parse)?.toEpochMilli() }
            .maxOrNull() ?: System.currentTimeMillis()

    private fun name(
        asset: AssetSnapshot,
        watchlist: List<AssetSettings>,
    ): String = watchlist.firstOrNull { it.id == asset.id }?.name ?: asset.name

    /** The status bar shows the first asset's drop shape in white: up, down, round or lying (docs/08). */
    private fun icon(asset: AssetSnapshot?): Int =
        when (asset?.drop?.shape()) {
            null -> R.drawable.ic_notification
            Shape.UP -> R.drawable.ic_status_up
            Shape.DOWN -> R.drawable.ic_status_down
            Shape.FLAT -> R.drawable.ic_status_flat
            Shape.WAVE -> R.drawable.ic_status_wave
        }

    private fun activity(
        context: Context,
        code: Int,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            code,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(
        context: Context,
        code: Int,
        action: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, StatusReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun allowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun px(
        context: Context,
        dp: Float,
    ): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    // The design's sizes (design/Bildirim.dc.html).
    private const val COLLAPSED_GAUGE = 30f
    private const val EXPANDED_GAUGE = 40f
    private const val CELL_GAP = 6f
    private const val BAR_WIDTH = 22f
    private const val BAR_HEIGHT = 6f
    private const val BAR_GAP = 4f
    private const val PARTS_WIDTH = BAR_WIDTH * 3 + BAR_GAP * 2
}
