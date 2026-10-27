package com.damla.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.damla.app.main.DamlaApp
import com.damla.app.main.Route

/** The app (docs/07). Its model and back stack live in [DamlaApplication], so they outlive a rotation. */
class MainActivity : ComponentActivity() {
    companion object {
        /** Opened from the overlay's buttons (docs/06): an asset's page, a new alarm, a widget's settings. */
        const val EXTRA_ASSET = "com.damla.app.ASSET"
        const val EXTRA_ALARM_ASSET = "com.damla.app.ALARM_ASSET"
        const val EXTRA_WIDGET_ID = "com.damla.app.WIDGET_ID"

        /** Opened from an alarm notification: the alarms tab, where the history lists it. */
        const val EXTRA_OPEN_ALARMS = "com.damla.app.OPEN_ALARMS"

        /** The ongoing notification's "Düzenle": Ayarlar, where its assets are chosen (docs/08). */
        const val EXTRA_OPEN_STATUS_SETTINGS = "com.damla.app.OPEN_STATUS_SETTINGS"
    }

    private val app get() = application as DamlaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The designs are dark only (docs/kararlar.md), so the system bars carry light icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (savedInstanceState == null) {
            app.navigator.selectTab(Route.Panel)
            open(intent)
        }
        setContent { DamlaApp(app.model, app.navigator) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent) {
        val navigator = app.navigator
        val asset = intent.getStringExtra(EXTRA_ASSET)
        val alarmAsset = intent.getStringExtra(EXTRA_ALARM_ASSET)
        // "Telefonda aç" on the watch: damla://asset/{id} or damla://alarms.
        val link = intent.data?.takeIf { it.scheme == "damla" }
        if (link?.host == "alarms") {
            navigator.selectTab(Route.Alarms)
            return
        }
        val linkAsset = link?.takeIf { it.host == "asset" }?.lastPathSegment
        if (linkAsset != null) {
            navigator.selectTab(Route.Panel)
            navigator.push(Route.Asset(linkAsset))
            return
        }
        val fromWidget = asset != null || alarmAsset != null || intent.hasExtra(EXTRA_WIDGET_ID)
        // Whoever comes from the widget has placed it already; the first-start guide would only be in the way.
        if (fromWidget) app.model.finishOnboarding()
        when {
            asset != null -> {
                navigator.selectTab(Route.Panel)
                navigator.push(Route.Asset(asset))
            }

            alarmAsset != null -> {
                navigator.selectTab(Route.Alarms)
                navigator.push(Route.AlarmEdit(assetId = alarmAsset))
            }

            intent.hasExtra(EXTRA_WIDGET_ID) -> {
                navigator.selectTab(Route.Panel)
                navigator.push(Route.Widget)
            }

            intent.getBooleanExtra(EXTRA_OPEN_ALARMS, false) -> {
                navigator.selectTab(Route.Alarms)
            }

            intent.getBooleanExtra(EXTRA_OPEN_STATUS_SETTINGS, false) -> {
                navigator.selectTab(Route.Settings)
            }
        }
    }
}
