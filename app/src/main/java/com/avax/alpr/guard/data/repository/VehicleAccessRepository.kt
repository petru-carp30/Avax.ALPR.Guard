package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.SyncMetadataDao
import com.avax.alpr.guard.data.local.VehicleDao
import com.avax.alpr.guard.data.local.VehicleEntity
import com.avax.alpr.guard.domain.AccessChecker
import com.avax.alpr.guard.domain.PlateNormalizer
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.LocalDateTime

class VehicleAccessRepository(
    private val vehicleDao: VehicleDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val accessChecker: AccessChecker
) {

    fun observeSyncMetadata() = syncMetadataDao.observe()

    suspend fun verify(inputPlate: String, requestedArea: AccessArea, now: LocalDateTime = LocalDateTime.now()): AccessDecision {
        val normalizedPlate = PlateNormalizer.normalize(inputPlate)

        if (normalizedPlate.isBlank()) {
            return accessChecker.evaluate(normalizedPlate, requestedArea, null, false, now)
        }

        val hasLocalSnapshot = syncMetadataDao.get() != null
        val vehicle = if (hasLocalSnapshot) vehicleDao.findByNormalizedPlate(normalizedPlate)?.toDomain() else null

        return accessChecker.evaluate(normalizedPlate, requestedArea, vehicle, hasLocalSnapshot, now)
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
        parkingLotValidity = ValidityWindow(parkingLotAccessStart.toLocalDateTime(), parkingLotAccessEnd.toLocalDateTime()),
        hasSiteAccess = hasSiteAccess,
        siteValidity = ValidityWindow(siteAccessStart.toLocalDateTime(), siteAccessEnd.toLocalDateTime()),
        hasCampAccess = hasCampAccess,
        campValidity = ValidityWindow(campAccessStart.toLocalDateTime(), campAccessEnd.toLocalDateTime()),
        isTemporaryPlate = isTemporaryPlate,
        isPrivate = isPrivate,
        isRentalCar = isRentalCar,
        accessNotes = accessNotes
    )

    private fun String?.toLocalDateTime(): LocalDateTime? = this?.let(LocalDateTime::parse)
}