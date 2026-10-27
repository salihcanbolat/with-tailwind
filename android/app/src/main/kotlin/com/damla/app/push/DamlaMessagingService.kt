package com.damla.app.push

import com.damla.app.alarms.AlarmNotifications
import com.damla.app.wear.WearSync
import com.damla.core.Haptics
import com.damla.core.WearEvent
import com.damla.gauge.Haptic
import com.damla.gauge.Shape
import com.damla.widget.WidgetUpdates
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/** Receives the backend's data messages (docs/03): state changes redraw the widgets, alarms notify. */
class DamlaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.register(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "state_change" -> {
                WidgetUpdates.refreshSoon(this)
                // A turn of the drop is felt (docs/02 `DropStateChanged`, docs/05); rings only redraw.
                val shape = Shape.entries.firstOrNull { it.name == data["to"] }
                if (data["part"] == "drop" && shape != null) {
                    runBlocking {
                        Haptics.play(applicationContext, Haptic.turnedTo(shape))
                        // The watch alerts too (docs/02 `DropStateChanged` → saat uyarısı).
                        val event = WearEvent(WearEvent.Kind.STATE, data["asset"].orEmpty(), shape.name, at = data["at"].orEmpty())
                        WearSync.send(applicationContext, event)
                    }
                }
            }

            "refresh" -> {
                WidgetUpdates.refreshSoon(this)
                if (data["market"] == "closed") runBlocking { Haptics.play(applicationContext, Haptic.MARKET_CLOSED) }
            }

            // Called on a background thread with a few seconds to finish; the store reads are local.
            "alarm" -> {
                runBlocking { AlarmNotifications.receive(applicationContext, message.data) }
            }

            else -> {
                Unit
            }
        }
    }
}
