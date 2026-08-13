package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.VehicleEntity
import com.avax.alpr.guard.data.network.dto.VehicleSyncItemDto
import com.avax.alpr.guard.data.network.dto.VehicleSyncResponseDto
import com.avax.alpr.guard.domain.PlateNormalizer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

class SnapshotValidator {

    sealed interface Result {
        data class Valid(
            val contractVersion: Int,
            val snapshotGeneratedAtUtc: String,
            val vehicles: List<VehicleEntity>
        ) : Result

        data class UnsupportedVersion(val receivedVersion: Int?) : Result
        data object Malformed : Result
    }

    fun validate(response: VehicleSyncResponseDto): Result {
        if (response.contractVersion != SUPPORTED_CONTRACT_VERSION) return Result.UnsupportedVersion(response.contractVersion)

        val snapshotGeneratedAtUtc = response.snapshotGeneratedAtUtc ?: return Result.Malformed
        if (!isUtcTimestamp(snapshotGeneratedAtUtc)) return Result.Malformed

        val vehicleCount = response.vehicleCount ?: return Result.Malformed
        val vehicles = response.vehicles ?: return Result.Malformed

        if (vehicleCount < 0 || vehicleCount != vehicles.size) return Result.Malformed

        val entities = ArrayList<VehicleEntity>(vehicles.size)
        val normalizedPlates = HashSet<String>(vehicles.size)

        for (vehicle in vehicles) {
            val entity = vehicle.toValidatedEntity() ?: return Result.Malformed
            if (!normalizedPlates.add(entity.normalizedLicensePlate)) return Result.Malformed
            entities += entity
        }

        return Result.Valid(
            contractVersion = SUPPORTED_CONTRACT_VERSION,
            snapshotGeneratedAtUtc = snapshotGeneratedAtUtc,
            vehicles = entities
        )
    }

    private fun VehicleSyncItemDto.toValidatedEntity(): VehicleEntity? {
        val sourceVehicleId = sourceVehicleId ?: return null
        val normalizedPlate = normalizedLicensePlate ?: return null
        val displayPlate = displayLicensePlate ?: return null
        val countryId = countryId ?: return null
        val parkingAccess = hasParkingLotAccess ?: return null
        val siteAccess = hasSiteAccess ?: return null
        val campAccess = hasCampAccess ?: return null
        val temporaryPlate = isTemporaryPlate ?: return null
        val privateVehicle = isPrivate ?: return null

        if (normalizedPlate.isBlank()) return null
        if (PlateNormalizer.normalize(normalizedPlate) != normalizedPlate) return null
        if (PlateNormalizer.normalize(displayPlate) != normalizedPlate) return null

        if (!isNullableLocalDateTime(parkingLotAccessStart)) return null
        if (!isNullableLocalDateTime(parkingLotAccessEnd)) return null
        if (!isNullableLocalDateTime(siteAccessStart)) return null
        if (!isNullableLocalDateTime(siteAccessEnd)) return null
        if (!isNullableLocalDateTime(campAccessStart)) return null
        if (!isNullableLocalDateTime(campAccessEnd)) return null

        return VehicleEntity(
            normalizedLicensePlate = normalizedPlate,
            sourceVehicleId = sourceVehicleId,
            displayLicensePlate = displayPlate,
            countryId = countryId,
            brand = brand,
            model = model,
            color = color,
            personId = personId,
            departmentId = departmentId,
            hasParkingLotAccess = parkingAccess,
            parkingLotAccessStart = parkingLotAccessStart,
            parkingLotAccessEnd = parkingLotAccessEnd,
            hasSiteAccess = siteAccess,
            siteAccessStart = siteAccessStart,
            siteAccessEnd = siteAccessEnd,
            hasCampAccess = campAccess,
            campAccessStart = campAccessStart,
            campAccessEnd = campAccessEnd,
            isTemporaryPlate = temporaryPlate,
            isPrivate = privateVehicle,
            isRentalCar = isRentalCar,
            accessNotes = accessNotes
        )
    }

    private fun isUtcTimestamp(value: String): Boolean {
        return try {
            OffsetDateTime.parse(value).offset == ZoneOffset.UTC
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun isNullableLocalDateTime(value: String?): Boolean {
        if (value == null) return true

        return try {
            LocalDateTime.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    companion object {
        const val SUPPORTED_CONTRACT_VERSION = 1
    }
}