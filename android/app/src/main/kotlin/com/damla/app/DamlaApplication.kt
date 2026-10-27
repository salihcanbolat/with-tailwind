package com.damla.app

import android.app.Application
import com.damla.app.alarms.AlarmNotifications
import com.damla.app.main.AppModel
import com.damla.app.main.Navigator
import com.damla.app.push.Push
import com.damla.core.DamlaServices
import com.damla.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        Push.init(this)
        WidgetUpdates.schedulePeriodic(this)
    }
}
