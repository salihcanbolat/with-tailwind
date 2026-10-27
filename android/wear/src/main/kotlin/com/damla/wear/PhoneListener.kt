package com.damla.wear

import com.damla.core.WearContract
import com.damla.core.WearEvent
import com.damla.core.WearState
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** The phone's state (a data item) and events (messages), docs/08 "Veri akışı" steps 2 to 4. */
class PhoneListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var latest: ByteArray? = null
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearContract.STATE_PATH) latest = event.dataItem.data
        }
        val bytes = latest ?: return
        val state = runCatching { WearContract.json.decodeFromString<WearState>(bytes.decodeToString()) }.getOrNull() ?: return
        runBlocking { WatchData.save(applicationContext, state) }
    }

    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != WearContract.EVENT_PATH) return
        val event = runCatching { WearContract.json.decodeFromString<WearEvent>(message.data.decodeToString()) }.getOrNull() ?: return
        runBlocking { WatchAlerts.show(applicationContext, event) }
    }
}
