package com.avax.alpr.guard.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avax.alpr.guard.GuardApplication
import com.avax.alpr.guard.data.repository.AccessLogUploadBatchResult

class AccessLogUploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? GuardApplication ?: return Result.failure()
        val result = application.container.accessLogUploadRepository.uploadPending()

        return when (result) {
            is AccessLogUploadBatchResult.Completed -> {
                Log.d(
                    TAG,
                    "Access log upload completed: synced=${result.syncedCount}, conflicts=${result.conflictCount}, rejected=${result.rejectedCount}"
                )
                Result.success()
            }

            is AccessLogUploadBatchResult.RetryRequired -> {
                Log.d(
                    TAG,
                    "Access log upload will retry: synced=${result.syncedCount}, conflicts=${result.conflictCount}, rejected=${result.rejectedCount}"
                )
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "AccessLogUploadWorker"
    }
}