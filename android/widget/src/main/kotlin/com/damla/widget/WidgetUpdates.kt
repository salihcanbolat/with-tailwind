package com.damla.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.damla.core.DamlaServices
import java.util.concurrent.TimeUnit

/**
 * Keeps widgets current. Pushes ask for a refresh when a state changes (docs/03); a periodic sync every
 * 15 minutes is the safety net for missed pushes (docs/kararlar.md, platform decisions).
 */
object WidgetUpdates {
    private const val PERIODIC = "damla-sync"
    private const val NOW = "damla-refresh"

    /** Fetches a new snapshot and redraws every widget. Widgets redraw from the cache even when offline. */
    suspend fun refresh(context: Context): Boolean {
        val fetched = runCatching { DamlaServices.repository.refresh() }.isSuccess
        DamlaWidget().updateAll(context)
        return fetched
    }

    /** Redraws from the cached snapshot, e.g. after a settings change. */
    suspend fun redraw(context: Context) = DamlaWidget().updateAll(context)

    fun schedulePeriodic(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Refreshes as soon as possible, e.g. for a push; expedited so it runs within the push's time budget. */
    fun refreshSoon(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(online)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (WidgetUpdates.refresh(applicationContext)) Result.success() else Result.retry()
}
