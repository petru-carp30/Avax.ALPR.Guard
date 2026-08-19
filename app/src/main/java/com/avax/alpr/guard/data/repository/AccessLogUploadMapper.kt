package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogEntity
import com.avax.alpr.guard.data.network.dto.AccessLogUploadRequestDto
import com.avax.alpr.guard.domain.PlateNormalizer
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import java.time.Instant
import java.util.UUID

internal object AccessLogUploadMapper {

    fun toRequest(accessLog: AccessLogEntity): AccessLogUploadRequestDto? {
        val normalizedPlate = accessLog.normalizedLicensePlate?.takeIf { it.isNotBlank() } ?: return null

        if (accessLog.inputLicensePlate.isBlank()) return null
        if (PlateNormalizer.normalize(accessLog.inputLicensePlate) != normalizedPlate) return null
        if (runCatching { UUID.fromString(accessLog.localLogId) }.isFailure) return null
        if (runCatching { Instant.parse(accessLog.eventTimestampUtc) }.isFailure) return null

        val accessArea = when (accessLog.accessArea) {
            AccessArea.ParkingLot -> "ParkingLot"
            AccessArea.Site -> "Site"
            AccessArea.Camp -> "Camp"
        }

        val accessDecision = when (accessLog.decisionStatus) {
            AccessDecisionStatus.Granted -> "Granted"
            AccessDecisionStatus.Denied -> "Denied"
            AccessDecisionStatus.NotYetValid -> "NotYetValid"
            AccessDecisionStatus.Expired -> "Expired"
            AccessDecisionStatus.VehicleNotFound -> "VehicleNotFound"
            AccessDecisionStatus.InvalidInput,
            AccessDecisionStatus.DataUnavailable -> return null
        }

        return AccessLogUploadRequestDto(
            mobileEventId = accessLog.localLogId,
            eventTimestampUtc = accessLog.eventTimestampUtc,
            licensePlate = accessLog.inputLicensePlate,
            normalizedPlate = normalizedPlate,
            sourceVehicleId = accessLog.sourceVehicleId,
            accessArea = accessArea,
            accessDecision = accessDecision
        )
    }
}