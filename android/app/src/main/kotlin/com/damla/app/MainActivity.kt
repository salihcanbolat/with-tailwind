package com.damla.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.damla.app.preview.GaugePreviewScreen

// The gauge preview is the only screen until the app screens arrive in phase 5.
class MainActivity : ComponentActivity() {
    companion object {
        /** Opened from the overlay's buttons (docs/06): an asset's page, a new alarm, a widget's settings. */
        const val EXTRA_ASSET = "com.damla.app.ASSET"
        const val EXTRA_ALARM_ASSET = "com.damla.app.ALARM_ASSET"
        const val EXTRA_WIDGET_ID = "com.damla.app.WIDGET_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GaugePreviewScreen()
        }
    }
}
