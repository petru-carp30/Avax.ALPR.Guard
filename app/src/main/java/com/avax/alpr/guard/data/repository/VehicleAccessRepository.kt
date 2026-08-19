package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogStore
import com.avax.alpr.guard.data.local.SyncMetadataDao
import com.avax.alpr.guard.data.local.VehicleDao
import com.avax.alpr.guard.data.local.VehicleEntity
import com.avax.alpr.guard.domain.AccessChecker
import com.avax.alpr.guard.domain.PlateNormalizer
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.LocalDateTime
import com.avax.alpr.guard.background.AccessLogUploadScheduler
import com.avax.alpr.guard.data.local.AccessLogPersistenceStatus

class VehicleAccessRepository(
    private val vehicleDao: VehicleDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val accessChecker: AccessChecker,
    private val accessLogStore: AccessLogStore,
    private val accessLogUploadScheduler: AccessLogUploadScheduler
) {

    fun observeSyncMetadata() = syncMetadataDao.observe()

    fun observeRecentAccessLogs() =
        accessLogStore.observeRecent()

    suspend fun verify(
        inputPlate: String,
        requestedArea: AccessArea,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalAccessVerificationResult {

        val normalizedPlate = PlateNormalizer.normalize(inputPlate)

        val decision = if (normalizedPlate.isBlank()) {
            accessChecker.evaluate(
                normalizedPlate = normalizedPlate,
                requestedArea = requestedArea,
                vehicle = null,
                hasLocalSnapshot = false,
                now = now
            )
        } else {
            val hasLocalSnapshot =
                syncMetadataDao.get() != null

            val vehicle = if (hasLocalSnapshot) {
                vehicleDao
                    .findByNormalizedPlate(normalizedPlate)
                    ?.toDomain()
            } else {
                null
            }

            accessChecker.evaluate(
                normalizedPlate = normalizedPlate,
                requestedArea = requestedArea,
                vehicle = vehicle,
                hasLocalSnapshot = hasLocalSnapshot,
                now = now
            )
        }

        val logPersistenceStatus = accessLogStore.recordIfRequired(
            inputPlate = inputPlate,
            decision = decision
        )

        if (logPersistenceStatus == AccessLogPersistenceStatus.Persisted) {
            runCatching {
                accessLogUploadScheduler.scheduleAfterLocalLog()
            }
        }

        return LocalAccessVerificationResult(
            decision = decision,
            logPersistenceStatus = logPersistenceStatus
        )
    }

    private fun VehicleEntity.toDomain() = VehicleRecord(
        sourceVehicleId = sourceVehicleId,
        normalizedLicensePlate = normalizedLicensePlate,
        displayLicensePlate = displayLicensePlate,
        countryId = countryId,
        brand = brand,
        model = model,
        color = color,
        personId = personId,
        departmentId = departmentId,
        hasParkingLotAccess = hasParkingLotAccess,
        parkingLotValidity = ValidityWindow(
            parkingLotAccessStart.toLocalDateTime(),
            parkingLotAccessEnd.toLocalDateTime()
        ),
        hasSiteAccess = hasSiteAccess,
        siteValidity = ValidityWindow(
            siteAccessStart.toLocalDateTime(),
            siteAccessEnd.toLocalDateTime()
        ),
        hasCampAccess = hasCampAccess,
        campValidity = ValidityWindow(
            campAccessStart.toLocalDateTime(),
            campAccessEnd.toLocalDateTime()
        ),
        isTemporaryPlate = isTemporaryPlate,
        isPrivate = isPrivate,
        isRentalCar = isRentalCar,
        accessNotes = accessNotes
    )

    private fun String?.toLocalDateTime(): LocalDateTime? =
        this?.let(LocalDateTime::parse)
}