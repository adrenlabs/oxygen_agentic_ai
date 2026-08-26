package com.oxygen.ai.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oxygen.ai.OxygenApplication
import com.oxygen.ai.core.logging.OxygenLog
import java.io.File

class DocumentIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: File(path).name
        val mime = inputData.getString(KEY_MIME) ?: "application/octet-stream"
        val graph = (applicationContext as OxygenApplication).graph
        return try {
            graph.rag.indexFile(File(path), name, mime, path)
            Result.success()
        } catch (t: Throwable) {
            OxygenLog.e("work", "index failed", t)
            Result.retry()
        }
    }

    companion object {
        const val KEY_PATH = "path"
        const val KEY_NAME = "name"
        const val KEY_MIME = "mime"
    }
}
