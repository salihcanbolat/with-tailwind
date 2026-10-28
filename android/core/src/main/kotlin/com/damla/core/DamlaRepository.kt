package com.damla.core

import com.damla.api.Alarm
import com.damla.api.AssetSettings
import com.damla.api.DeviceSettings
import com.damla.api.DigestSettings
import com.damla.api.PairingCode
import com.damla.api.SnapshotResponse
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Keeps the local data in step with the backend and tells the backend who this device is. */
class DamlaRepository(
    val api: DamlaApi,
    val store: DamlaStore,
) {
    /** Every target the app shows: the watch list plus whatever the widget holds. */
    suspend fun targets(): List<String> {
        val watched = store.watchlist.first().map { it.id }
        val inWidget = store.widgetConfig.first().order
        return (watched + inWidget).distinct().ifEmpty { WidgetConfig.DEFAULT_ORDER }
    }

    /** Fetches a fresh snapshot with this device's settings and stores it for the widgets. */
    suspend fun refresh(): SnapshotResponse {
        val snapshot = api.snapshot(targets(), store.deviceId())
        store.setSnapshot(snapshot)
        return snapshot
    }

    /**
     * Sends the push token, watch list, alarms and when the evening summary should come, in this phone's time
     * zone (docs/03, `PUT /v1/devices/{id}`).
     */
    suspend fun registerDevice() {
        val app = store.settings.first()
        val digest = DigestSettings(app.digest, app.digestAt, ZoneId.systemDefault().id)
        val settings =
            DeviceSettings(store.fcmToken(), store.watchlist.first(), store.alarms.first(), store.widgetConfig.first().order, digest)
        api.putDevice(store.deviceId(), settings)
    }

    /** Saves the watch list locally, then tells the backend and refreshes; offline, the next sync sends it. */
    suspend fun saveWatchlist(assets: List<AssetSettings>) {
        store.setWatchlist(assets)
        sync()
    }

    suspend fun saveAlarms(alarms: List<Alarm>) {
        store.setAlarms(alarms)
        sync()
    }

    /** A code a computer types to follow this phone (docs/08); registers first so the backend knows it. */
    suspend fun pairingCode(): PairingCode {
        registerDevice()
        return api.pairing(store.deviceId())
    }

    /** Best effort: registration and a fresh snapshot. Returns false when the backend could not be reached. */
    suspend fun sync(): Boolean =
        runCatching {
            registerDevice()
            refresh()
        }.isSuccess
}
