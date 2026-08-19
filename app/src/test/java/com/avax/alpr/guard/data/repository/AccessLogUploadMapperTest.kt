package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogEntity
import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.google.gson.Gson
import org.junit.Assert
import org.junit.Test

class AccessLogUploadMapperTest {

    @Test
    fun `Granted maps correctly`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event(AccessDecisionStatus.Granted)))
        Assert.assertEquals("Granted", request.accessDecision)
    }

    @Test
    fun `Denied maps correctly`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event(AccessDecisionStatus.Denied)))
        Assert.assertEquals("Denied", request.accessDecision)
    }

    @Test
    fun `NotYetValid maps correctly`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event(AccessDecisionStatus.NotYetValid)))
        Assert.assertEquals("NotYetValid", request.accessDecision)
    }

    @Test
    fun `Expired maps correctly`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event(AccessDecisionStatus.Expired)))
        Assert.assertEquals("Expired", request.accessDecision)
    }

    @Test
    fun `VehicleNotFound maps with null sourceVehicleId`() {
        val request = requireNotNull(
            AccessLogUploadMapper.toRequest(
                event(
                    status = AccessDecisionStatus.VehicleNotFound,
                    sourceVehicleId = null
                )
            )
        )

        Assert.assertEquals("VehicleNotFound", request.accessDecision)
        Assert.assertNull(request.sourceVehicleId)
    }

    @Test
    fun `original UUID is preserved`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event()))
        Assert.assertEquals(EVENT_ID, request.mobileEventId)
    }

    @Test
    fun `original event timestamp is preserved`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event()))
        Assert.assertEquals(EVENT_TIMESTAMP, request.eventTimestampUtc)
    }

    @Test
    fun `normalized plate is sent`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event()))
        Assert.assertEquals("CJ12ABC", request.normalizedPlate)
    }

    @Test
    fun `receivedAtUtc is not serialized`() {
        val request = requireNotNull(AccessLogUploadMapper.toRequest(event()))
        val json = Gson().toJson(request)
        Assert.assertFalse(json.contains("receivedAtUtc"))
    }

    private fun event(
        status: AccessDecisionStatus = AccessDecisionStatus.Granted,
        sourceVehicleId: Int? = 42
    ): AccessLogEntity {
        return AccessLogEntity(
            localLogId = EVENT_ID,
            eventTimestampUtc = EVENT_TIMESTAMP,
            inputLicensePlate = "CJ 12-ABC",
            normalizedLicensePlate = "CJ12ABC",
            sourceVehicleId = sourceVehicleId,
            accessArea = AccessArea.Site,
            decisionStatus = status,
            areaAccessEnabled = true,
            areaValidityStart = null,
            areaValidityEnd = null,
            accessNotes = null,
            syncState = AccessLogSyncState.Pending
        )
    }

    companion object {
        private const val EVENT_ID = "11111111-1111-1111-1111-111111111111"
        private const val EVENT_TIMESTAMP = "2026-08-19T08:00:00.123Z"
    }
}