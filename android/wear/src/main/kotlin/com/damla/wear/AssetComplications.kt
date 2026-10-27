package com.damla.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.damla.core.closed
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Shape
import com.damla.gauge.android.GaugeBitmaps
import kotlin.math.roundToInt

/**
 * A watch-face complication for one of the watch's three assets (docs/08, "Kadran komplikasyonu"). The
 * first is the important one: the design puts it in the middle at 48, the other two beside it at 36.
 * SMALL_IMAGE shows the gauge itself with the plain white shape for ambient mode; faces that only take
 * MONOCHROMATIC_IMAGE get the shape (docs/kararlar.md, "Platform kararları"). A tap opens the asset.
 */
abstract class AssetComplication(
    private val index: Int,
    private val sizeDp: Float,
) : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val shown = WatchData.shown()
        val asset = shown.assets.getOrNull(index) ?: return NoDataComplicationData()
        val visual = asset.visual(shown.settings) ?: return NoDataComplicationData()
        val grey = asset.closed && shown.settings.grayWhenClosed
        val shape = asset.drop?.shape() ?: Shape.FLAT
        val name = shown.names[asset.id] ?: asset.name
        val description =
            PlainComplicationText
                .Builder(getString(com.damla.core.R.string.gauge_description, name, getString(stateWordLower(shape, grey))))
                .build()
        val tap =
            PendingIntent.getActivity(
                this,
                index,
                Intent(this, WatchActivity::class.java)
                    .putExtra(WatchActivity.EXTRA_INDEX, index)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val shapeIcon = Icon.createWithResource(this, WatchAlerts.icon(shape))
        return when (request.complicationType) {
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData
                    .Builder(MonochromaticImage.Builder(shapeIcon).build(), description)
                    .setTapAction(tap)
                    .build()
            }

            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData
                    .Builder(image(visual, asset.logo(), shapeIcon), description)
                    .setTapAction(tap)
                    .build()
            }

            else -> {
                NoDataComplicationData()
            }
        }
    }

    /** The face's editor shows a still drop with no data in it: no logo, no fabricated asset. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.app_name)).build()
        val icon = Icon.createWithResource(this, R.drawable.ic_status_up)
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(MonochromaticImage.Builder(icon).build(), description).build()
            }

            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData
                    .Builder(image(GaugeVisual(Shape.UP, Shape.UP, Shape.UP), null, icon), description)
                    .build()
            }

            else -> {
                null
            }
        }
    }

    private fun image(
        visual: GaugeVisual,
        logo: com.damla.gauge.Logo?,
        ambient: Icon,
    ): SmallImage {
        val px = (sizeDp * resources.displayMetrics.density).roundToInt()
        return SmallImage
            .Builder(Icon.createWithBitmap(GaugeBitmaps.render(visual, px, logo)), SmallImageType.PHOTO)
            .setAmbientImage(ambient)
            .build()
    }
}

class FirstAssetComplication : AssetComplication(0, 48f)

class SecondAssetComplication : AssetComplication(1, 36f)

class ThirdAssetComplication : AssetComplication(2, 36f)
