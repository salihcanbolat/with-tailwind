package com.damla.app.main

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.damla.api.Alarm
import com.damla.api.AssetSettings
import com.damla.api.AssetSnapshot
import com.damla.api.AssetsResponse
import com.damla.api.PairingCode
import com.damla.api.SnapshotResponse
import com.damla.core.AppSettings
import com.damla.core.DamlaRepository
import com.damla.core.FiredAlarm
import com.damla.core.StatusConfig
import com.damla.core.StoredDigest
import com.damla.core.WidgetConfig
import com.damla.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the app screens show and change (docs/07). Local data comes from the device store at once; every
 * change is saved locally first and then sent to the backend, so the screens work offline.
 */
class AppModel(
    private val context: Context,
    val repository: DamlaRepository,
    private val scope: CoroutineScope,
) {
    private val store = repository.store

    val snapshot: StateFlow<SnapshotResponse?> = store.snapshot.stateIn(scope, SharingStarted.Eagerly, null)
    val watchlist: StateFlow<List<AssetSettings>> = store.watchlist.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val alarms: StateFlow<List<Alarm>> = store.alarms.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<FiredAlarm>> = store.alarmHistory.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> = store.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val widget: StateFlow<WidgetConfig> = store.widgetConfig.stateIn(scope, SharingStarted.Eagerly, WidgetConfig())
    val status: StateFlow<StatusConfig> = store.status.stateIn(scope, SharingStarted.Eagerly, StatusConfig())
    val onboarded: StateFlow<Boolean?> = store.onboarded.stateIn(scope, SharingStarted.Eagerly, null)

    /** False after a failed sync: the screens say "İnternet yok" and keep showing the last data. */
    var online by mutableStateOf(true)
        private set

    /** Bumped when a sync finishes, for "Kaydedildi" style feedback that needs the backend. */
    var syncing by mutableStateOf(false)
        private set

    /** What can be added (`/v1/assets`) and their current gauges, loaded for "Varlık ekle". */
    var catalog by mutableStateOf<AssetsResponse?>(null)
        private set
    var catalogGauges by mutableStateOf<Map<String, AssetSnapshot>>(emptyMap())
        private set

    private var pendingSync: Job? = null

    fun asset(id: String): AssetSnapshot? = snapshot.value?.assets?.firstOrNull { it.id == id } ?: catalogGauges[id]

    /** Pulls a fresh snapshot (and registers the device); used on start and on pull. */
    fun refresh() {
        scope.launch {
            syncing = true
            online = repository.sync()
            syncing = false
            WidgetUpdates.redraw(context)
        }
    }

    /** A fresh snapshot alone, each minute while the app is on screen (prices are 1 minute candles). */
    fun poll() {
        scope.launch {
            online = runCatching { repository.refresh() }.isSuccess
            WidgetUpdates.redraw(context)
        }
    }

    /** Loads the catalogue and a snapshot of everything in it with the default settings, for the gauges. */
    fun loadCatalog(extra: List<String> = emptyList()) {
        scope.launch {
            runCatching {
                val assets = catalog ?: repository.api.assets().also { catalog = it }
                val ids = (assets.assets.map { it.id } + extra).distinct()
                repository.api.snapshot(ids, deviceId = null)
            }.onSuccess { response ->
                catalogGauges = catalogGauges + response.assets.associateBy { it.id }
                online = true
            }.onFailure { online = false }
        }
    }

    /** One cross rate's gauge before it is added (design/CaprazKur.dc.html). */
    fun loadGauge(id: String) = loadCatalog(listOf(id))

    fun isWatched(id: String): Boolean = watchlist.value.any { it.id == id }

    /** Adds an asset with the intervals chosen for new assets (design/Ayarlar.dc.html). */
    fun add(
        id: String,
        name: String? = null,
    ) {
        if (isWatched(id)) return
        val (drop, upper, lower) = settings.value.newAssetIntervals
        saveWatchlist(watchlist.value + AssetSettings(id, drop, upper, lower, name = name))
    }

    /** Removes an asset, its alarms and its place in the widget. */
    fun remove(id: String) {
        saveWatchlist(watchlist.value.filterNot { it.id == id })
        val left = alarms.value.filterNot { it.assetId == id }
        if (left.size != alarms.value.size) scope.launch { store.setAlarms(left) }
        val config = widget.value
        if (id in config.order) saveWidget(config.copy(order = config.order - id))
    }

    fun updateAsset(settings: AssetSettings) {
        saveWatchlist(watchlist.value.map { if (it.id == settings.id) settings else it })
    }

    private fun saveWatchlist(list: List<AssetSettings>) {
        scope.launch {
            store.setWatchlist(list)
            syncSoon()
        }
    }

    fun saveAlarm(alarm: Alarm) {
        val current = alarms.value
        val next = if (current.any { it.id == alarm.id }) current.map { if (it.id == alarm.id) alarm else it } else current + alarm
        scope.launch {
            store.setAlarms(next)
            syncSoon()
        }
    }

    fun deleteAlarm(id: String) {
        scope.launch {
            store.setAlarms(alarms.value.filterNot { it.id == id })
            syncSoon()
        }
    }

    fun setAlarmEnabled(
        id: String,
        enabled: Boolean,
    ) {
        alarms.value.firstOrNull { it.id == id }?.let { saveAlarm(it.copy(enabled = enabled)) }
    }

    fun saveSettings(settings: AppSettings) {
        val before = this.settings.value
        scope.launch {
            store.setSettings(settings)
            WidgetUpdates.redraw(context)
            // The backend sends the evening summary, so it hears about its switch and time.
            if (settings.digest != before.digest || settings.digestAt != before.digestAt) syncSoon()
        }
    }

    /** The latest evening summary, for its replay screen. */
    val digest: StateFlow<StoredDigest?> = store.digest.stateIn(scope, SharingStarted.Eagerly, null)

    fun saveWidget(config: WidgetConfig) {
        scope.launch {
            store.setWidgetConfig(config)
            WidgetUpdates.redraw(context)
            syncSoon()
        }
    }

    /** A code for "Bilgisayarla eşitle"; null when the backend cannot be reached. */
    suspend fun pairingCode(): PairingCode? =
        try {
            repository.pairingCode().also { online = true }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            online = false
            null
        }

    fun saveStatus(config: StatusConfig) {
        scope.launch { store.setStatus(config) }
    }

    fun finishOnboarding() {
        scope.launch { store.setOnboarded() }
    }

    /** Several quick changes (a slider) are sent together. */
    private fun syncSoon() {
        pendingSync?.cancel()
        pendingSync =
            scope.launch {
                delay(SYNC_DELAY)
                online = repository.sync()
                WidgetUpdates.redraw(context)
            }
    }

    suspend fun alarmById(id: String): Alarm? = store.alarms.first().firstOrNull { it.id == id }

    private companion object {
        const val SYNC_DELAY = 600L
    }
}
