package com.shirhen.babytracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shirhen.babytracker.widget.WidgetRefresher
import java.util.concurrent.TimeUnit

/** Every ~15 minutes: pull the other parent's entries and refresh the widget's "time ago" text. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { FamilySync.pullOnce() }
        WidgetRefresher.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
