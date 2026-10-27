package com.damla.app.push

import com.damla.widget.WidgetUpdates
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives the backend's data messages (docs/03): state changes and refreshes redraw the widgets. */
class DamlaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.register(token)

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "state_change", "refresh" -> WidgetUpdates.refreshSoon(this)

            // Alarms arrive in phase 5 together with their notifications.
            else -> Unit
        }
    }
}
