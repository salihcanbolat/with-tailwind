package com.damla.core

import com.damla.api.AssetSettings
import com.damla.api.SnapshotResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the phone tells the watch over the Data Layer API (docs/08, "Veri akışı"). The watch keeps the same
 * device store as the phone, so it can also fetch snapshots from the backend on its own over Wi-Fi or LTE.
 */
object WearContract {
    /** The phone's current view: a data item, synced whenever the watch is reachable. */
    const val STATE_PATH = "/damla/state"

    /** Something happened: a message, delivered only to a reachable watch. */
    const val EVENT_PATH = "/damla/event"

    /** Declared by the watch app (res/values/wear.xml), so the phone sends events only to watches with it. */
    const val CAPABILITY = "damla_watch"

    /** The phone's alarm notifications carry this tag; the watch app keeps them off the watch (own alert). */
    const val ALARM_BRIDGE_TAG = "damla_alarm"

    val json = Json { ignoreUnknownKeys = true }
}

/**
 * The phone's state for the watch. [assets] are the three the watch shows (complications, tile, app): the
 * ongoing notification's choice, which falls back to the widget's first three.
 */
@Serializable
data class WearState(
    val watchlist: List<AssetSettings>,
    val settings: AppSettings,
    val snapshot: SnapshotResponse?,
    val assets: List<String>,
)

/**
 * An event for the watch's alert screen (design/Saat.dc.html): a drop turned ([shape] is its new shape), or
 * an alarm rang ([text] is the phone's sentence, [haptic] its rhythm, [shape] the state it waited for).
 */
@Serializable
data class WearEvent(
    val kind: Kind,
    val asset: String,
    val shape: String? = null,
    val text: String? = null,
    val haptic: String? = null,
    val at: String,
) {
    @Serializable
    enum class Kind { STATE, ALARM }
}
