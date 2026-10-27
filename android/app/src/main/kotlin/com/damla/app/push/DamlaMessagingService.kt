package com.damla.app.push

import com.damla.app.alarms.AlarmNotifications
import com.damla.widget.WidgetUpdates
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/** Receives the backend's data messages (docs/03): state changes redraw the widgets, alarms notify. */
class DamlaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.register(token)

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "state_change", "refresh" -> WidgetUpdates.refreshSoon(this)

            // Called on a background thread with a few seconds to finish; the store reads are local.
            "alarm" -> runBlocking { AlarmNotifications.receive(applicationContext, message.data) }

            else -> Unit
        }
    }
}
