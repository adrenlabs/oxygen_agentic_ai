package com.oxygen.ai.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.oxygen.ai.OxygenApplication
import com.oxygen.ai.core.logging.OxygenLog
import java.util.concurrent.TimeUnit

class DriveSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as OxygenApplication).graph
        return try {
            graph.sync.flushQueue()
            Result.success()
        } catch (t: Throwable) {
            OxygenLog.w("work", "Drive sync worker failed", t)
            Result.retry()
        }
    }
}

class CleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cache = applicationContext.cacheDir
        cache.listFiles()?.forEach { f ->
            if (System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000) {
                f.deleteRecursively()
            }
        }
        return Result.success()
    }
}

object OxygenWork {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "oxygen-drive-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "oxygen-cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS).build(),
        )
    }
}
