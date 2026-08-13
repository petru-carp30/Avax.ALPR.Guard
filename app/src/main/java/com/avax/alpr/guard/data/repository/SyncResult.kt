package com.avax.alpr.guard.data.repository

sealed interface SyncResult {

    data class Success(
        val vehicleCount: Int,
        val snapshotGeneratedAtUtc: String,
        val importedAtUtc: String
    ) : SyncResult

    data object NoNetwork : SyncResult
    data object BackendUnavailable : SyncResult
    data object Conflict : SyncResult
    data class HttpError(val statusCode: Int) : SyncResult
    data class UnsupportedContractVersion(val receivedVersion: Int?) : SyncResult
    data object MalformedSnapshot : SyncResult
    data object DatabaseFailure : SyncResult
}