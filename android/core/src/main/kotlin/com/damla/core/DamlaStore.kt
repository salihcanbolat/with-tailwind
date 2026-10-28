package com.damla.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.damla.api.Alarm
import com.damla.api.AssetSettings
import com.damla.api.SnapshotResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.damlaData: DataStore<Preferences> by preferencesDataStore(name = "damla")

/** Everything the app keeps on the device: identity, watch list, alarms, settings, widget and last snapshot. */
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

    val watchlist: Flow<List<AssetSettings>> = data.data.map { decode(it[WATCHLIST]) ?: DEFAULT_WATCHLIST }

    suspend fun setWatchlist(assets: List<AssetSettings>) {
        data.edit { it[WATCHLIST] = json.encodeToString(assets) }
    }

    val alarms: Flow<List<Alarm>> = data.data.map { decode(it[ALARMS]) ?: emptyList() }

    suspend fun setAlarms(alarms: List<Alarm>) {
        data.edit { it[ALARMS] = json.encodeToString(alarms) }
    }

    /** Alarms that rang, newest first, at most [HISTORY_SIZE]. */
    val alarmHistory: Flow<List<FiredAlarm>> = data.data.map { decode(it[ALARM_HISTORY]) ?: emptyList() }

    suspend fun addFiredAlarm(fired: FiredAlarm) {
        data.edit { prefs ->
            val current = decode<List<FiredAlarm>>(prefs[ALARM_HISTORY]) ?: emptyList()
            prefs[ALARM_HISTORY] = json.encodeToString((listOf(fired) + current).take(HISTORY_SIZE))
        }
    }

    val settings: Flow<AppSettings> = data.data.map { decode(it[SETTINGS]) ?: AppSettings() }

    suspend fun setSettings(settings: AppSettings) {
        data.edit { it[SETTINGS] = json.encodeToString(settings) }
    }

    val snapshot: Flow<SnapshotResponse?> = data.data.map { decode(it[SNAPSHOT]) }

    suspend fun setSnapshot(snapshot: SnapshotResponse) {
        data.edit { it[SNAPSHOT] = json.encodeToString(snapshot) }
    }

    /**
     * The home-screen widget's settings (design/KlasorIcerik.dc.html). One set for every placed widget:
     * the app shows a single "Ana ekran widget'ı" and its draft/save flow edits it as a whole.
     */
    val widgetConfig: Flow<WidgetConfig> = data.data.map { decode(it[WIDGET]) ?: WidgetConfig() }

    @Suppress("UNUSED_PARAMETER")
    suspend fun widget(id: Int): WidgetConfig = widgetConfig.first()

    suspend fun setWidgetConfig(config: WidgetConfig) {
        data.edit { it[WIDGET] = json.encodeToString(config) }
    }

    val status: Flow<StatusConfig> = data.data.map { decode(it[STATUS]) ?: StatusConfig() }

    suspend fun setStatus(config: StatusConfig) {
        data.edit { it[STATUS] = json.encodeToString(config) }
    }

    /** Changes the ongoing notification's settings in one step (its buttons run while the app is closed). */
    suspend fun updateStatus(change: (StatusConfig) -> StatusConfig) {
        data.edit { prefs -> prefs[STATUS] = json.encodeToString(change(decode(prefs[STATUS]) ?: StatusConfig())) }
    }

    /** The latest evening summary, for its replay screen. */
    val digest: Flow<StoredDigest?> = data.data.map { decode(it[DIGEST]) }

    suspend fun setDigest(digest: StoredDigest) {
        data.edit { it[DIGEST] = json.encodeToString(digest) }
    }

    /** First launch shows the introduction (docs/07, "İlk açılış tanıtımı"). */
    val onboarded: Flow<Boolean> = data.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded() {
        data.edit { it[ONBOARDED] = true }
    }

    private inline fun <reified T> decode(text: String?): T? = text?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    companion object {
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val WATCHLIST = stringPreferencesKey("watchlist")
        private val ALARMS = stringPreferencesKey("alarms")
        private val ALARM_HISTORY = stringPreferencesKey("alarm_history")
        private val SETTINGS = stringPreferencesKey("settings")
        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val WIDGET = stringPreferencesKey("widget")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val STATUS = stringPreferencesKey("status_notification")
        private val DIGEST = stringPreferencesKey("digest")
        private const val HISTORY_SIZE = 50

        /** New installs start with the assets of the designs; intervals and thresholds use the defaults. */
        val DEFAULT_WATCHLIST: List<AssetSettings> = WidgetConfig.DEFAULT_ORDER.map { AssetSettings(id = it) }
    }
}
