package com.damla.core

import android.content.Context

/**
 * The app's few long-lived objects, created once by the Application. Kept as a plain holder rather than a
 * DI framework while the app is this small (docs/kararlar.md).
 */
object DamlaServices {
    @Volatile private var instance: DamlaRepository? = null

    fun init(
        context: Context,
        apiBaseUrl: String,
    ) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) instance = DamlaRepository(DamlaApi(apiBaseUrl), DamlaStore(context))
            }
        }
    }

    val repository: DamlaRepository
        get() = checkNotNull(instance) { "DamlaServices.init() was not called" }
}
