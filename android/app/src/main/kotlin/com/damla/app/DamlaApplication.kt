package com.damla.app

import android.app.Application
import com.damla.app.alarms.AlarmNotifications
import com.damla.app.digest.DigestNotifications
import com.damla.app.main.AppModel
import com.damla.app.main.Navigator
import com.damla.app.push.Push
import com.damla.app.status.StatusNotification
import com.damla.app.wear.WearSync
import com.damla.core.DamlaServices
import com.damla.core.WearState
import com.damla.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DamlaApplication : Application() {
    /** The app screens' model: its saves finish even when the screen is closed mid-way. */
    val model: AppModel by lazy {
        AppModel(this, DamlaServices.repository, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    val navigator: Navigator by lazy { Navigator() }

    override fun onCreate() {
        super.onCreate()
        DamlaServices.init(this, BuildConfig.API_BASE_URL)
        AlarmNotifications.createChannels(this)
        StatusNotification.createChannel(this)
        DigestNotifications.createChannel(this)
        Push.init(this)
        WidgetUpdates.schedulePeriodic(this)
        followStatus()
    }

    /**
     * Redraws the ongoing notification and updates the watch whenever what they show changes on the
     * device: a push or the periodic sync stores a new snapshot, or the user changes a setting.
     */
    private fun followStatus() {
        val store = DamlaServices.repository.store
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(
                store.snapshot,
                store.settings,
                store.status,
                store.widgetConfig,
                store.watchlist,
            ) { snapshot, settings, status, widget, watchlist ->
                StatusNotification.Input(snapshot, settings, status, widget, watchlist)
            }.distinctUntilChanged()
                .collect { input ->
                    StatusNotification.update(this@DamlaApplication, input)
                    val assets = input.status.shown(input.widget)
                    WearSync.publish(this@DamlaApplication, WearState(input.watchlist, input.settings, input.snapshot, assets))
                }
        }
    }
}
