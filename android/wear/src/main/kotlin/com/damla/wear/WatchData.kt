package com.damla.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.damla.api.AssetSnapshot
import com.damla.core.AppSettings
import com.damla.core.DamlaServices
import com.damla.core.WearState
import com.damla.core.WidgetConfig
import com.damla.core.shownAssets
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The watch's data. It lives in the same device store as the phone's; the three assets the watch shows are
 * kept in the store's widget order, as the watch has no widget of its own.
 */
object WatchData {
    const val SHOWN = 3

    /** Stores what the phone sent and redraws the tile and complications. */
    suspend fun save(
        context: Context,
        state: WearState,
    ) {
        val store = DamlaServices.repository.store
        store.setWatchlist(state.watchlist)
        store.setSettings(state.settings)
        state.snapshot?.let { store.setSnapshot(it) }
        store.setWidgetConfig(WidgetConfig(order = state.assets))
        refreshSurfaces(context)
    }

    /** Without the phone: the snapshot straight from the backend over Wi-Fi or LTE (docs/08). */
    suspend fun fetch(context: Context) {
        val repository = DamlaServices.repository
        repository.store.setSnapshot(repository.api.snapshot(repository.targets(), deviceId = null))
        refreshSurfaces(context)
    }

    /** The assets the watch shows, in order, with their names from the watch list. */
    suspend fun shown(): Shown {
        val store = DamlaServices.repository.store
        val settings = store.settings.first()
        val order =
            store.widgetConfig
                .first()
                .order
                .take(SHOWN)
        val watchlist = store.watchlist.first()
        val assets = store.snapshot.first().shownAssets(order, settings)
        return Shown(
            assets,
            settings,
            assets.associate { asset ->
                asset.id to
                    (watchlist.firstOrNull { it.id == asset.id }?.name ?: asset.name)
            },
        )
    }

    data class Shown(
        val assets: List<AssetSnapshot>,
        val settings: AppSettings,
        val names: Map<String, String>,
    )

    fun refreshSurfaces(context: Context) {
        TileService.getUpdater(context).requestUpdate(DamlaTileService::class.java)
        for (source in COMPLICATIONS) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, source)).requestUpdateAll()
        }
    }

    private val COMPLICATIONS =
        listOf(FirstAssetComplication::class.java, SecondAssetComplication::class.java, ThirdAssetComplication::class.java)
}

/** A fetch every 15 minutes while the watch has a network, so it stays current away from the phone. */
object WatchSync {
    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<WatchSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("watch-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class WatchSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            WatchData.fetch(applicationContext)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
}
