package com.damla.app.push

import android.content.Context
import android.util.Log
import com.damla.app.BuildConfig
import com.damla.core.DamlaServices
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging set up from build properties instead of google-services.json, so the app
 * builds and runs without a Firebase project (pushes are then simply off).
 */
object Push {
    private const val TAG = "DamlaPush"
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val enabled: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    fun init(context: Context) {
        if (!enabled) {
            Log.i(TAG, "Firebase is not configured; pushes are off")
            return
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options =
                FirebaseOptions
                    .Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
            FirebaseApp.initializeApp(context, options)
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener(::register)
    }

    /** Stores the token and tells the backend (docs/03, `PUT /v1/devices/{id}`). */
    fun register(token: String) {
        scope.launch {
            val repository = DamlaServices.repository
            repository.store.setFcmToken(token)
            runCatching { repository.registerDevice() }.onFailure { Log.w(TAG, "Device registration failed", it) }
        }
    }
}
