package com.avax.alpr.guard.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AccessLogUploadScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun scheduleAfterLocalLog() {
        enqueue(ExistingWorkPolicy.REPLACE)
    }

    fun ensureRecoveryWork() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(existingWorkPolicy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AccessLogUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            existingWorkPolicy,
            request
        )
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "access-log-upload"
        internal const val WORK_TAG = "access-log-upload"
        internal const val BACKOFF_DELAY_SECONDS = 30L
        internal val NEW_LOG_POLICY = ExistingWorkPolicy.REPLACE
        internal val RECOVERY_POLICY = ExistingWorkPolicy.KEEP
        internal val REQUIRED_NETWORK_TYPE = NetworkType.CONNECTED
        internal val RETRY_BACKOFF_POLICY = BackoffPolicy.EXPONENTIAL
    }
}