package com.damla.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.damla.api.AssetSettings
import com.damla.api.SnapshotResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.damlaData: DataStore<Preferences> by preferencesDataStore(name = "damla")

/** Everything the app keeps on the device: identity, watch list, widget settings and the last snapshot. */
class DamlaStore(
    context: Context,
) {
    private val data = context.applicationContext.damlaData
    private val json = Json { ignoreUnknownKeys = true }

    /** Anonymous device id, created on first use (docs/kararlar.md, Faz 2). */
    suspend fun deviceId(): String {
        data.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        data.edit { it[DEVICE_ID] = id }
        return id
    }

    suspend fun fcmToken(): String? = data.data.first()[FCM_TOKEN]

    suspend fun setFcmToken(token: String) {
        data.edit { it[FCM_TOKEN] = token }
    }

    val watchlist: Flow<List<AssetSettings>> =
        data.data.map { prefs ->
            prefs[WATCHLIST]?.let { json.decodeFromString<List<AssetSettings>>(it) } ?: DEFAULT_WATCHLIST
        }

    suspend fun setWatchlist(assets: List<AssetSettings>) {
        data.edit { it[WATCHLIST] = json.encodeToString(assets) }
    }

    val settings: Flow<AppSettings> =
        data.data.map { prefs -> prefs[SETTINGS]?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings() }

    suspend fun setSettings(settings: AppSettings) {
        data.edit { it[SETTINGS] = json.encodeToString(settings) }
    }

    val snapshot: Flow<SnapshotResponse?> =
        data.data.map { prefs -> prefs[SNAPSHOT]?.let { json.decodeFromString<SnapshotResponse>(it) } }

    suspend fun setSnapshot(snapshot: SnapshotResponse) {
        data.edit { it[SNAPSHOT] = json.encodeToString(snapshot) }
    }

    val widgets: Flow<Map<Int, WidgetConfig>> =
        data.data.map { prefs -> prefs[WIDGETS]?.let { json.decodeFromString<Map<Int, WidgetConfig>>(it) } ?: emptyMap() }

    suspend fun widget(id: Int): WidgetConfig = widgets.first()[id] ?: WidgetConfig()

    suspend fun setWidget(
        id: Int,
        config: WidgetConfig,
    ) {
        data.edit { prefs ->
            val current = prefs[WIDGETS]?.let { json.decodeFromString<Map<Int, WidgetConfig>>(it) } ?: emptyMap()
            prefs[WIDGETS] = json.encodeToString(current + (id to config))
        }
    }

    suspend fun removeWidgets(ids: IntArray) {
        data.edit { prefs ->
            val current = prefs[WIDGETS]?.let { json.decodeFromString<Map<Int, WidgetConfig>>(it) } ?: return@edit
            prefs[WIDGETS] = json.encodeToString(current - ids.toSet())
        }
    }

    companion object {
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val WATCHLIST = stringPreferencesKey("watchlist")
        private val SETTINGS = stringPreferencesKey("settings")
        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val WIDGETS = stringPreferencesKey("widgets")

        /** New installs start with the assets of the designs; intervals and thresholds use the defaults. */
        val DEFAULT_WATCHLIST: List<AssetSettings> = WidgetConfig.DEFAULT_ORDER.map { AssetSettings(id = it) }
    }
}
