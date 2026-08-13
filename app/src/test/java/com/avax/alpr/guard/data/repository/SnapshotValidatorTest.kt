package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.network.dto.VehicleSyncItemDto
import com.avax.alpr.guard.data.network.dto.VehicleSyncResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotValidatorTest {

    private val validator = SnapshotValidator()

    @Test
    fun validSnapshotIsAccepted() {
        val result = validator.validate(response())

        assertTrue(result is SnapshotValidator.Result.Valid)

        val valid = result as SnapshotValidator.Result.Valid
        assertEquals(1, valid.contractVersion)
        assertEquals(1, valid.vehicles.size)
        assertEquals("TEST123", valid.vehicles.first().normalizedLicensePlate)
    }

    @Test
    fun unsupportedContractVersionIsRejected() {
        val result = validator.validate(response(contractVersion = 2))
        assertTrue(result is SnapshotValidator.Result.UnsupportedVersion)
    }

    @Test
    fun vehicleCountMismatchIsRejected() {
        val result = validator.validate(response(vehicleCount = 2))
        assertTrue(result is SnapshotValidator.Result.Malformed)
    }

    @Test
    fun duplicateNormalizedPlatesAreRejected() {
        val vehicles = listOf(vehicle("TEST123", "TEST 123"), vehicle("TEST123", "TEST-123"))
        val result = validator.validate(response(vehicles = vehicles, vehicleCount = 2))

        assertTrue(result is SnapshotValidator.Result.Malformed)
    }

    @Test
    fun displayPlateMustMatchNormalizedPlate() {
        val vehicles = listOf(vehicle("TEST123", "OTHER123"))
        val result = validator.validate(response(vehicles = vehicles))

        assertTrue(result is SnapshotValidator.Result.Malformed)
    }

    @Test
    fun malformedAccessTimestampIsRejected() {
        val vehicles = listOf(vehicle(siteAccessStart = "not-a-date"))
        val result = validator.validate(response(vehicles = vehicles))

        assertTrue(result is SnapshotValidator.Result.Malformed)
    }

    private fun response(
        contractVersion: Int? = 1,
        vehicleCount: Int? = 1,
        vehicles: List<VehicleSyncItemDto>? = listOf(vehicle())
    ) = VehicleSyncResponseDto(
        contractVersion = contractVersion,
        snapshotGeneratedAtUtc = "2026-08-13T08:00:00Z",
        vehicleCount = vehicleCount,
        vehicles = vehicles
    )

    private fun vehicle(
        normalizedPlate: String = "TEST123",
        displayPlate: String = "TEST 123",
        siteAccessStart: String? = null
    ) = VehicleSyncItemDto(
        sourceVehicleId = 1,
        normalizedLicensePlate = normalizedPlate,
        displayLicensePlate = displayPlate,
        countryId = 0,
        brand = "TestBrand",
        model = "TestModel",
        color = "Blue",
        personId = null,
        departmentId = null,
        hasParkingLotAccess = false,
        parkingLotAccessStart = null,
        parkingLotAccessEnd = null,
        hasSiteAccess = true,
        siteAccessStart = siteAccessStart,
        siteAccessEnd = null,
        hasCampAccess = false,
        campAccessStart = null,
        campAccessEnd = null,
        isTemporaryPlate = false,
        isPrivate = false,
        isRentalCar = false,
        accessNotes = null
    )
}