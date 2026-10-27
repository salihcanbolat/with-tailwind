package com.damla.wear

import android.app.Application
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import com.damla.core.DamlaServices
import com.damla.core.WearContract

class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DamlaServices.init(this, BuildConfig.API_BASE_URL)
        WatchAlerts.createChannel(this)
        // Alarms come with the watch's own alert screen, so the phone's copy is not bridged (docs/08).
        BridgingManager
            .fromContext(this)
            .setConfig(BridgingConfig.Builder(this, true).addExcludedTag(WearContract.ALARM_BRIDGE_TAG).build())
        WatchSync.schedule(this)
    }
}
