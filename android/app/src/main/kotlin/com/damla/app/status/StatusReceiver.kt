package com.damla.app.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.damla.core.DamlaServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The ongoing notification's "Duraklat / Sürdür" button, and its dismissal: from Android 14 users can swipe
 * ongoing notifications away, which turns the setting off so Ayarlar tells the truth (docs/kararlar.md).
 */
class StatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = DamlaServices.repository.store
                when (intent.action) {
                    StatusNotification.ACTION_PAUSE -> store.updateStatus { it.copy(paused = !it.paused) }
                    StatusNotification.ACTION_DISMISSED -> store.updateStatus { it.copy(enabled = false) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
