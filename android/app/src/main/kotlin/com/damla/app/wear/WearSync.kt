package com.damla.app.wear

import android.content.Context
import android.util.Log
import com.damla.core.WearContract
import com.damla.core.WearEvent
import com.damla.core.WearState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * The phone's side of the watch link (docs/08, "Veri akışı"): the state as a data item, which Google Play
 * services delivers whenever the watch is reachable, and events as messages to watches with the app.
 * Without a watch or Wear OS app every call fails quietly.
 */
object WearSync {
    private const val TAG = "DamlaWear"

    suspend fun publish(
        context: Context,
        state: WearState,
    ) {
        try {
            val bytes = WearContract.json.encodeToString(WearState.serializer(), state).encodeToByteArray()
            Wearable.getDataClient(context).putDataItem(PutDataRequest.create(WearContract.STATE_PATH).setData(bytes)).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "No watch to update", e)
        }
    }

    suspend fun send(
        context: Context,
        event: WearEvent,
    ) {
        try {
            val bytes = WearContract.json.encodeToString(WearEvent.serializer(), event).encodeToByteArray()
            val nodes =
                Wearable
                    .getCapabilityClient(context)
                    .getCapability(WearContract.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
            val messages = Wearable.getMessageClient(context)
            for (node in nodes) messages.sendMessage(node.id, WearContract.EVENT_PATH, bytes).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "No watch to alert", e)
        }
    }
}
