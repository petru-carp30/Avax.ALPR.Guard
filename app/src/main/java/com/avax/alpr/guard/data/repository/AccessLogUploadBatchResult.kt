package com.avax.alpr.guard.data.repository

sealed interface AccessLogUploadBatchResult {

    data class Completed(
        val syncedCount: Int,
        val conflictCount: Int,
        val rejectedCount: Int
    ) : AccessLogUploadBatchResult

    data class RetryRequired(
        val syncedCount: Int,
        val conflictCount: Int,
        val rejectedCount: Int
    ) : AccessLogUploadBatchResult
}