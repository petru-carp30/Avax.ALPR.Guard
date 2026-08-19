package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogDao
import com.avax.alpr.guard.data.local.AccessLogEntity
import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.data.network.AccessLogApi
import com.avax.alpr.guard.data.network.dto.AccessLogUploadRequestDto
import com.avax.alpr.guard.data.network.dto.AccessLogUploadResponseDto
import java.time.Instant
import kotlinx.coroutines.CancellationException
import retrofit2.Response

class AccessLogUploadRepository(
    private val api: AccessLogApi,
    private val accessLogDao: AccessLogDao
) {

    suspend fun uploadPending(): AccessLogUploadBatchResult {
        val pendingLogs = try {
            accessLogDao.getBySyncState(AccessLogSyncState.Pending)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return AccessLogUploadBatchResult.RetryRequired(0, 0, 0)
        }

        val orderedLogs = pendingLogs.sortedWith(
            compareBy<AccessLogEntity> { parseTimestampForOrdering(it.eventTimestampUtc) }
                .thenBy { it.localLogId }
        )

        var syncedCount = 0
        var conflictCount = 0
        var rejectedCount = 0

        for (accessLog in orderedLogs) {
            when (uploadSingle(accessLog)) {
                ItemResult.Synced -> syncedCount++
                ItemResult.Conflict -> conflictCount++
                ItemResult.Rejected -> rejectedCount++

                ItemResult.RetryRequired -> {
                    return AccessLogUploadBatchResult.RetryRequired(
                        syncedCount = syncedCount,
                        conflictCount = conflictCount,
                        rejectedCount = rejectedCount
                    )
                }
            }
        }

        return AccessLogUploadBatchResult.Completed(
            syncedCount = syncedCount,
            conflictCount = conflictCount,
            rejectedCount = rejectedCount
        )
    }

    private suspend fun uploadSingle(accessLog: AccessLogEntity): ItemResult {
        val request = AccessLogUploadMapper.toRequest(accessLog)

        if (request == null) {
            return changeState(accessLog.localLogId, AccessLogSyncState.Rejected, ItemResult.Rejected)
        }

        val response = try {
            api.uploadAccessLog(request)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return ItemResult.RetryRequired
        }

        return when {
            response.code() == 201 && isAcknowledgement(response, request, "Stored") -> {
                changeState(accessLog.localLogId, AccessLogSyncState.Synced, ItemResult.Synced)
            }

            response.code() == 200 && isAcknowledgement(response, request, "AlreadyStored") -> {
                changeState(accessLog.localLogId, AccessLogSyncState.Synced, ItemResult.Synced)
            }

            response.code() == 409 -> {
                changeState(accessLog.localLogId, AccessLogSyncState.Conflict, ItemResult.Conflict)
            }

            response.code() in 500..599 -> ItemResult.RetryRequired

            isTransientClientStatus(response.code()) -> ItemResult.RetryRequired

            response.code() in 400..499 -> {
                changeState(accessLog.localLogId, AccessLogSyncState.Rejected, ItemResult.Rejected)
            }

            else -> ItemResult.RetryRequired
        }
    }

    private suspend fun changeState(
        localLogId: String,
        syncState: AccessLogSyncState,
        successfulResult: ItemResult
    ): ItemResult {
        return try {
            if (accessLogDao.updateSyncState(localLogId, syncState) > 0) successfulResult else ItemResult.RetryRequired
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ItemResult.RetryRequired
        }
    }

    private fun isAcknowledgement(
        response: Response<AccessLogUploadResponseDto>,
        request: AccessLogUploadRequestDto,
        expectedStatus: String
    ): Boolean {
        val body = response.body() ?: return false
        return body.mobileEventId == request.mobileEventId && body.status == expectedStatus
    }

    private fun isTransientClientStatus(statusCode: Int): Boolean {
        return statusCode == 401 ||
                statusCode == 403 ||
                statusCode == 404 ||
                statusCode == 405 ||
                statusCode == 408 ||
                statusCode == 425 ||
                statusCode == 429
    }

    private fun parseTimestampForOrdering(value: String): Instant {
        return runCatching { Instant.parse(value) }.getOrDefault(Instant.MAX)
    }

    private enum class ItemResult {
        Synced,
        Conflict,
        Rejected,
        RetryRequired
    }
}