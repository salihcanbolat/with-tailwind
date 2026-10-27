package com.damla.core

import com.damla.api.DeviceSettings
import com.damla.api.SnapshotResponse
import kotlinx.coroutines.flow.first

/** Keeps the local snapshot in step with the backend and tells the backend who this device is. */
class DamlaRepository(
    val api: DamlaApi,
    val store: DamlaStore,
) {
    /** Every target the app shows: the watch list plus whatever the widgets hold. */
    suspend fun targets(): List<String> {
        val watched = store.watchlist.first().map { it.id }
        val inWidgets =
            store.widgets
                .first()
                .values
                .flatMap { it.order }
        return (watched + inWidgets + WidgetConfig.DEFAULT_ORDER).distinct()
    }

    /** Fetches a fresh snapshot with this device's settings and stores it for the widgets. */
    suspend fun refresh(): SnapshotResponse {
        val snapshot = api.snapshot(targets(), store.deviceId())
        store.setSnapshot(snapshot)
        return snapshot
    }

    /** Sends the push token and watch list (docs/03, `PUT /v1/devices/{id}`). */
    suspend fun registerDevice() {
        api.putDevice(store.deviceId(), DeviceSettings(store.fcmToken(), store.watchlist.first()))
    }
}
