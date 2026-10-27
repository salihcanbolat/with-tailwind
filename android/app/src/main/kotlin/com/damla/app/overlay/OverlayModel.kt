package com.damla.app.overlay

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.damla.api.AssetSnapshot
import com.damla.api.HistoryResponse
import com.damla.api.PartSnapshot
import com.damla.api.PriceEvent
import com.damla.core.AppSettings
import com.damla.core.DamlaRepository
import com.damla.core.WidgetConfig
import com.damla.core.WidgetOpening
import com.damla.core.WidgetStyle
import com.damla.core.closed
import com.damla.core.shape
import com.damla.core.shownAssets
import com.damla.core.visual
import com.damla.gauge.GaugeVisual
import com.damla.gauge.OverlayChart
import com.damla.gauge.OverlayGeometry
import com.damla.gauge.OverlayScene
import com.damla.gauge.OverlayStage
import com.damla.gauge.WidgetArrangement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** What the overlay shows, read from the device store when it opens. */
class OverlayData(
    val config: WidgetConfig,
    val settings: AppSettings,
    /** Up to four assets of the flow in the widget's order; the widget's own drops come first. */
    val assets: List<AssetSnapshot>,
    /** How many of [assets] the widget shows. */
    val inWidget: Int,
) {
    companion object {
        suspend fun load(
            repository: DamlaRepository,
            widgetId: Int,
        ): OverlayData {
            val store = repository.store
            val config = store.widget(widgetId)
            val settings = store.settings.first()
            val snapshot = store.snapshot.first()
            val assets = snapshot.shownAssets(config.order, settings).take(WidgetConfig.MAX_ASSETS)
            return OverlayData(config, settings, assets, snapshot.shownAssets(config.inWidget, settings).size)
        }
    }
}

/**
 * The overlay's state (docs/06): the [OverlayScene] that times every movement, the assets with their live
 * prices and pulses, and the chart history. UI events come in through the methods; the composables only read.
 */
@Stable
class OverlayModel(
    val data: OverlayData,
    val geometry: OverlayGeometry,
    val arrangement: WidgetArrangement,
    val reduceMotion: Boolean,
    private val repository: DamlaRepository,
    private val scope: CoroutineScope,
) {
    val flow: Boolean = data.config.opening == WidgetOpening.FLOW
    val bare: Boolean = data.config.style == WidgetStyle.BARE
    val settings: AppSettings get() = data.settings

    var assets: List<AssetSnapshot> by mutableStateOf(data.assets)
        private set

    val scene: OverlayScene =
        OverlayScene(
            geometry = geometry,
            assetCount = assets.size,
            flow = flow,
            mini = arrangement.kind == WidgetArrangement.Kind.MINI,
            bare = bare,
            speed = if (reduceMotion) 0.0 else 1.0,
            initialChart = OverlayChart.flat,
            initialChartColor = chartColor(assets.first()),
            initialWords = wordColors(assets.first()),
        )

    /** Bumped on every change of the scene, so the frame loop runs until it settles again. */
    var changes: Int by mutableIntStateOf(0)
        private set

    /** True once the overlay is on its way back into the widget; it finishes when the scene settles. */
    var closing: Boolean = false
        private set

    /** Where the finger is on the chart (0..260), or null. */
    var scrubX: Double? by mutableStateOf(null)
        private set

    /** The chart's target points; the scene morphs towards them. */
    var chartTarget: List<Double> = OverlayChart.flat
        private set

    private val pulses = mutableStateMapOf<String, Int>()
    private val lastPulse = HashMap<String, Long>()
    private val histories = mutableStateMapOf<String, HistoryResponse>()
    private val loading = HashSet<String>()

    val selected: AssetSnapshot get() = assets[scene.selected]

    fun visual(asset: AssetSnapshot): GaugeVisual? = asset.visual(settings, pulses[asset.id] ?: 0)

    /** The history behind the chart right now, if it has arrived. */
    val history: HistoryResponse? get() = histories[key(selected.id, scene.range)]

    fun open() {
        scene.show(OverlayStage.FLOW, now())
        changed()
        for (asset in assets) load(asset.id, scene.range)
        scope.launch { runCatching { repository.refresh() }.onSuccess { fresh -> merge(fresh.assets) } }
    }

    fun openDetail(index: Int) {
        if (scene.stage != OverlayStage.FLOW) return
        scrubX = null
        scene.show(OverlayStage.DETAIL, now(), index)
        updateChart()
        changed()
    }

    fun select(index: Int) {
        if (index == scene.selected) return
        scrubX = null
        scene.select(index, now())
        updateChart()
        changed()
    }

    /** A horizontal swipe over the detail of [dx] dp: the next or previous asset, or a tap that steps back. */
    fun swipe(dx: Double) {
        when {
            abs(dx) > SWIPE && assets.size > 1 -> select((scene.selected + (if (dx < 0) 1 else -1) + assets.size) % assets.size)
            abs(dx) < TAP -> back()
        }
    }

    fun pickRange(index: Int) {
        if (index == scene.range) return
        scrubX = null
        scene.pickRange(index, now())
        updateChart()
        changed()
    }

    fun scrub(x: Double?) {
        scrubX = x?.coerceIn(0.0, OverlayScene.CHART_WIDTH)
    }

    fun share(open: Boolean) {
        scene.share(open, now())
        changed()
    }

    /** One step back (docs/06, tapping empty space): share card, detail, flow, then the widget. */
    fun back() {
        when {
            closing -> {}

            scene.sharing -> {
                share(false)
            }

            scene.stage == OverlayStage.DETAIL -> {
                scrubX = null
                scene.show(OverlayStage.FLOW, now())
                changed()
            }

            else -> {
                close()
            }
        }
    }

    /** Back into the widget: from the detail through the flow, 440 ms apart, like the prototype's close button. */
    fun closeAll() {
        if (closing) return
        if (scene.stage == OverlayStage.DETAIL) {
            scene.share(false, now())
            scene.show(OverlayStage.FLOW, now())
            changed()
            scope.launch {
                delay(if (reduceMotion) 0 else CLOSE_STEP)
                close()
            }
        } else {
            close()
        }
    }

    private fun close() {
        closing = true
        scrubX = null
        scene.show(OverlayStage.WIDGET, now())
        changed()
    }

    /** A live price (docs/06, "Nabız"): the price updates, and the light turns unless pulses are off or too soon. */
    fun onPrice(event: PriceEvent) {
        val index = assets.indexOfFirst { it.id == event.asset }
        if (index < 0) return
        assets = assets.toMutableList().also { it[index] = it[index].copy(price = event.price, updatedAt = event.at) }
        val gap = settings.pulse.minIntervalMillis ?: return
        if (reduceMotion || event.direction == 0) return
        val now = SystemClock.elapsedRealtime()
        val last = lastPulse[event.asset]
        if (last != null && now - last < gap) return
        lastPulse[event.asset] = now
        pulses[event.asset] = (pulses[event.asset] ?: 0) + event.direction.sign
    }

    fun ids(): List<String> = assets.map { it.id }

    private fun merge(fresh: List<AssetSnapshot>) {
        val byId = fresh.associateBy { it.id }
        assets = assets.map { byId[it.id]?.takeIf { asset -> asset.visual(settings) != null } ?: it }
        if (scene.stage == OverlayStage.DETAIL) {
            updateChart()
            changed()
        }
    }

    /** New colours at once; new points as soon as the history is there (the scene morphs them in 560 ms). */
    private fun updateChart() {
        val asset = selected
        val range = scene.range
        histories[key(asset.id, range)]?.let { history -> chartTarget = OverlayChart.points(history.points.map { it.p }) }
        scene.setChart(chartTarget, chartColor(asset), wordColors(asset), now())
        load(asset.id, range)
    }

    private fun load(
        id: String,
        range: Int,
    ) {
        val key = key(id, range)
        if (key in histories || !loading.add(key)) return
        scope.launch {
            runCatching { repository.api.history(id, RANGES[range]) }
                .onSuccess { history ->
                    histories[key] = history
                    if (scene.stage == OverlayStage.DETAIL && selected.id == id && scene.range == range) {
                        updateChart()
                        changed()
                    }
                }
            loading.remove(key)
        }
    }

    private fun changed() {
        changes++
    }

    companion object {
        /** The range picker's choices (`/v1/history`), in the order of the design: 1 gün, 1 hafta, 1 ay, 1 yıl. */
        val RANGES = listOf("1d", "1w", "1mo", "1y")

        /** A swipe needs 40 dp; under 10 dp it is a tap (design/Prototip.dc.html). */
        private const val SWIPE = 40.0
        private const val TAP = 10.0
        private const val CLOSE_STEP = 440L

        fun now(): Double = System.nanoTime() / 1_000_000.0

        private fun key(
            id: String,
            range: Int,
        ) = "$id|$range"

        fun parts(asset: AssetSnapshot): List<PartSnapshot> = listOfNotNull(asset.drop, asset.upperRing, asset.lowerRing)
    }

    /** Grey only while the market is closed and the user wants grey ("Piyasa kapalıyken gri göster"). */
    fun grey(asset: AssetSnapshot): Boolean = asset.closed && settings.grayWhenClosed

    fun chartColor(asset: AssetSnapshot): Int {
        val drop = asset.drop ?: return OverlayColors.CLOSED.toArgb()
        return OverlayColors.chart(drop.shape(), grey(asset)).toArgb()
    }

    /** The three annotation words' colours: drop, upper ring, lower ring. */
    fun wordColors(asset: AssetSnapshot): List<Int> = parts(asset).map { part -> OverlayColors.bright(part.shape(), grey(asset)).toArgb() }
}
