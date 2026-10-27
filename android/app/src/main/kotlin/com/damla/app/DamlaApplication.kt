package com.damla.app

import android.app.Application
import com.damla.app.push.Push
import com.damla.core.DamlaServices
import com.damla.widget.WidgetUpdates

class DamlaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DamlaServices.init(this, BuildConfig.API_BASE_URL)
        Push.init(this)
        WidgetUpdates.schedulePeriodic(this)
    }
}
