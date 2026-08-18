package com.avax.alpr.guard.data.local

import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow

enum class AccessLogPersistenceStatus {
    Persisted,
    NotRequired,
    Failed
}

class AccessLogStore(
    private val accessLogDao: AccessLogDao,
    private val clock: Clock = Clock.systemUTC(),
    private val idProvider: () -> String = {
        UUID.randomUUID().toString()
    }
) {

    fun observeRecent(
        limit: Int = 20
    ): Flow<List<AccessLogEntity>> {
        return accessLogDao.observeRecent(limit)
    }

    suspend fun getPending(): List<AccessLogEntity> {
        return accessLogDao.getBySyncState(
            AccessLogSyncState.Pending
        )
    }

    suspend fun markSynchronized(
        localLogId: String
    ): Boolean {
        return accessLogDao.updateSyncState(
            localLogId = localLogId,
            syncState = AccessLogSyncState.Synced
        ) > 0
    }

    suspend fun recordIfRequired(
        inputPlate: String,
        decision: AccessDecision
    ): AccessLogPersistenceStatus {

        if (!shouldPersist(decision.status)) {
            return AccessLogPersistenceStatus.NotRequired
        }

        val vehicle = decision.vehicle
        val areaSnapshot = vehicle?.accessSnapshot(
            decision.requestedArea
        )

        val accessLog = AccessLogEntity(
            localLogId = idProvider(),
            eventTimestampUtc = Instant.now(clock).toString(),
            inputLicensePlate = inputPlate,
            normalizedLicensePlate = decision.normalizedLicensePlate
                .takeIf { it.isNotBlank() },
            sourceVehicleId = vehicle?.sourceVehicleId,
            accessArea = decision.requestedArea,
            decisionStatus = decision.status,
            areaAccessEnabled = areaSnapshot?.enabled,
            areaValidityStart = areaSnapshot
                ?.validity
                ?.start
                ?.toString(),
            areaValidityEnd = areaSnapshot
                ?.validity
                ?.end
                ?.toString(),
            accessNotes = vehicle
                ?.accessNotes
                ?.takeIf { it.isNotBlank() },
            syncState = AccessLogSyncState.Pending
        )

        return try {
            accessLogDao.insert(accessLog)
            AccessLogPersistenceStatus.Persisted
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AccessLogPersistenceStatus.Failed
        }
    }

    private fun shouldPersist(
        status: AccessDecisionStatus
    ): Boolean {
        return when (status) {
            AccessDecisionStatus.Granted,
            AccessDecisionStatus.Denied,
            AccessDecisionStatus.NotYetValid,
            AccessDecisionStatus.Expired,
            AccessDecisionStatus.VehicleNotFound -> true

            AccessDecisionStatus.InvalidInput,
            AccessDecisionStatus.DataUnavailable -> false
        }
    }

    private fun VehicleRecord.accessSnapshot(
        area: AccessArea
    ): AreaAccessSnapshot {
        return when (area) {
            AccessArea.ParkingLot -> AreaAccessSnapshot(
                enabled = hasParkingLotAccess,
                validity = parkingLotValidity
            )

            AccessArea.Site -> AreaAccessSnapshot(
                enabled = hasSiteAccess,
                validity = siteValidity
            )

            AccessArea.Camp -> AreaAccessSnapshot(
                enabled = hasCampAccess,
                validity = campValidity
            )
        }
    }

    private data class AreaAccessSnapshot(
        val enabled: Boolean,
        val validity: ValidityWindow
    )
}