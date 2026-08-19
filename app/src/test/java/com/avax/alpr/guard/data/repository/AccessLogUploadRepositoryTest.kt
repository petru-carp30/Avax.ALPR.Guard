package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogDao
import com.avax.alpr.guard.data.local.AccessLogEntity
import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.data.network.AccessLogApi
import com.avax.alpr.guard.data.network.dto.AccessLogUploadRequestDto
import com.avax.alpr.guard.data.network.dto.AccessLogUploadResponseDto
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert
import org.junit.Test
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AccessLogUploadRepositoryTest {

    @Test
    fun `201 Stored marks event Synced`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { request -> stored(request) }

        val result = AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertTrue(result is AccessLogUploadBatchResult.Completed)
        Assert.assertEquals(AccessLogSyncState.Synced, dao.requireEvent(event.localLogId).syncState)
    }

    @Test
    fun `200 AlreadyStored marks event Synced`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { request -> alreadyStored(request) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(AccessLogSyncState.Synced, dao.requireEvent(event.localLogId).syncState)
    }

    @Test
    fun `AlreadyStored changes only synchronization state`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { request -> alreadyStored(request) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(
            event.copy(syncState = AccessLogSyncState.Synced),
            dao.requireEvent(event.localLogId)
        )
    }

    @Test
    fun `no network keeps event Pending`() = runBlocking {
        verifyTransientFailure(UnknownHostException("No network"))
    }

    @Test
    fun `connection failure keeps event Pending`() = runBlocking {
        verifyTransientFailure(ConnectException("Connection failed"))
    }

    @Test
    fun `timeout keeps event Pending`() = runBlocking {
        verifyTransientFailure(SocketTimeoutException("Timed out"))
    }

    @Test
    fun `HTTP 5xx keeps event Pending`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { error(503) }

        val result = AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertTrue(result is AccessLogUploadBatchResult.RetryRequired)
        Assert.assertEquals(
            AccessLogSyncState.Pending,
            dao.requireEvent(event.localLogId).syncState
        )
    }

    @Test
    fun `HTTP 409 marks event Conflict`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { error(409) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(
            AccessLogSyncState.Conflict,
            dao.requireEvent(event.localLogId).syncState
        )
    }

    @Test
    fun `Conflict event is not retried as Pending`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { error(409) }
        val repository = AccessLogUploadRepository(api, dao)

        repository.uploadPending()
        repository.uploadPending()

        Assert.assertEquals(1, api.requests.size)
    }

    @Test
    fun `HTTP 400 marks event Rejected`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { error(400) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(
            AccessLogSyncState.Rejected,
            dao.requireEvent(event.localLogId).syncState
        )
    }

    @Test
    fun `Rejected event remains locally persisted`() = runBlocking {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { error(400) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(1, dao.count())
        Assert.assertEquals(event.localLogId, dao.requireEvent(event.localLogId).localLogId)
    }

    @Test
    fun `worker repository retrieves Pending logs`() = runBlocking {
        val pending = event()
        val dao = FakeAccessLogDao(listOf(pending))
        val api = FakeAccessLogApi { request -> stored(request) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(listOf(pending.localLogId), api.requests.map { it.mobileEventId })
    }

    @Test
    fun `Synced logs are not uploaded again`() = runBlocking {
        verifyStateIsNotUploaded(AccessLogSyncState.Synced)
    }

    @Test
    fun `Conflict logs are not uploaded again`() = runBlocking {
        verifyStateIsNotUploaded(AccessLogSyncState.Conflict)
    }

    @Test
    fun `Rejected logs are not uploaded again`() = runBlocking {
        verifyStateIsNotUploaded(AccessLogSyncState.Rejected)
    }

    @Test
    fun `multiple Pending events are uploaded oldest first`() = runBlocking {
        val first = event(
            id = "11111111-1111-1111-1111-111111111111",
            timestamp = "2026-08-19T08:00:00.000Z"
        )

        val second = event(
            id = "22222222-2222-2222-2222-222222222222",
            timestamp = "2026-08-19T08:01:00.000Z"
        )

        val dao = FakeAccessLogDao(listOf(second, first))
        val api = FakeAccessLogApi { request -> stored(request) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(
            listOf(first.localLogId, second.localLogId),
            api.requests.map { it.mobileEventId })
        Assert.assertEquals(AccessLogSyncState.Synced, dao.requireEvent(first.localLogId).syncState)
        Assert.assertEquals(
            AccessLogSyncState.Synced,
            dao.requireEvent(second.localLogId).syncState
        )
    }

    @Test
    fun `success before later transient failure remains Synced`() = runBlocking {
        val first = event(
            id = "11111111-1111-1111-1111-111111111111",
            timestamp = "2026-08-19T08:00:00.000Z"
        )

        val second = event(
            id = "22222222-2222-2222-2222-222222222222",
            timestamp = "2026-08-19T08:01:00.000Z"
        )

        val dao = FakeAccessLogDao(listOf(first, second))

        val api = FakeAccessLogApi { request ->
            if (request.mobileEventId == first.localLogId) stored(request) else throw ConnectException(
                "Connection lost"
            )
        }

        val result = AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertTrue(result is AccessLogUploadBatchResult.RetryRequired)
        Assert.assertEquals(AccessLogSyncState.Synced, dao.requireEvent(first.localLogId).syncState)
        Assert.assertEquals(
            AccessLogSyncState.Pending,
            dao.requireEvent(second.localLogId).syncState
        )
    }

    private suspend fun verifyTransientFailure(exception: Exception) {
        val event = event()
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { throw exception }

        val result = AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertTrue(result is AccessLogUploadBatchResult.RetryRequired)
        Assert.assertEquals(
            AccessLogSyncState.Pending,
            dao.requireEvent(event.localLogId).syncState
        )
    }

    private suspend fun verifyStateIsNotUploaded(syncState: AccessLogSyncState) {
        val event = event().copy(syncState = syncState)
        val dao = FakeAccessLogDao(listOf(event))
        val api = FakeAccessLogApi { request -> stored(request) }

        AccessLogUploadRepository(api, dao).uploadPending()

        Assert.assertEquals(0, api.requests.size)
    }

    private fun event(
        id: String = "11111111-1111-1111-1111-111111111111",
        timestamp: String = "2026-08-19T08:00:00.123Z"
    ): AccessLogEntity {
        return AccessLogEntity(
            localLogId = id,
            eventTimestampUtc = timestamp,
            inputLicensePlate = "CJ 12-ABC",
            normalizedLicensePlate = "CJ12ABC",
            sourceVehicleId = 42,
            accessArea = AccessArea.Site,
            decisionStatus = AccessDecisionStatus.Granted,
            areaAccessEnabled = true,
            areaValidityStart = null,
            areaValidityEnd = null,
            accessNotes = null,
            syncState = AccessLogSyncState.Pending
        )
    }

    private fun stored(request: AccessLogUploadRequestDto): Response<AccessLogUploadResponseDto> {
        return Response.success(
            201,
            AccessLogUploadResponseDto(
                mobileEventId = request.mobileEventId,
                status = "Stored"
            )
        )
    }

    private fun alreadyStored(request: AccessLogUploadRequestDto): Response<AccessLogUploadResponseDto> {
        return Response.success(
            200,
            AccessLogUploadResponseDto(
                mobileEventId = request.mobileEventId,
                status = "AlreadyStored"
            )
        )
    }

    private fun error(statusCode: Int): Response<AccessLogUploadResponseDto> {
        return Response.error(
            statusCode,
            "{}".toResponseBody("application/json".toMediaType())
        )
    }

    private class FakeAccessLogApi(
        private val handler: suspend (AccessLogUploadRequestDto) -> Response<AccessLogUploadResponseDto>
    ) : AccessLogApi {

        val requests = mutableListOf<AccessLogUploadRequestDto>()

        override suspend fun uploadAccessLog(request: AccessLogUploadRequestDto): Response<AccessLogUploadResponseDto> {
            requests += request
            return handler(request)
        }
    }

    private class FakeAccessLogDao(accessLogs: List<AccessLogEntity>) : AccessLogDao {

        private val items = accessLogs.associateBy { it.localLogId }.toMutableMap()

        override suspend fun insert(accessLog: AccessLogEntity) {
            check(!items.containsKey(accessLog.localLogId))
            items[accessLog.localLogId] = accessLog
        }

        override fun observeRecent(limit: Int): Flow<List<AccessLogEntity>> {
            return flowOf(items.values.take(limit))
        }

        override suspend fun getRecent(limit: Int): List<AccessLogEntity> {
            return items.values.take(limit)
        }

        override suspend fun getBySyncState(syncState: AccessLogSyncState): List<AccessLogEntity> {
            return items.values.filter { it.syncState == syncState }
        }

        override suspend fun updateSyncState(localLogId: String, syncState: AccessLogSyncState): Int {
            val current = items[localLogId] ?: return 0
            items[localLogId] = current.copy(syncState = syncState)
            return 1
        }

        override suspend fun findByLocalLogId(localLogId: String): AccessLogEntity? {
            return items[localLogId]
        }

        override suspend fun count(): Int {
            return items.size
        }

        fun requireEvent(localLogId: String): AccessLogEntity {
            return requireNotNull(items[localLogId])
        }
    }
}