package com.damla.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.damla.app.preview.GaugePreviewScreen

// The gauge preview is the only screen until the app screens arrive in phase 5.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GaugePreviewScreen()
        }
    }
}
