package com.avax.alpr.guard.domain

import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessCheckerTest {

    private val checker = AccessChecker()
    private val now = LocalDateTime.of(2026, 8, 12, 12, 0)

    @Test
    fun grantedWhenPermissionIsEnabledAndCurrentlyValid() {
        val result = checker.evaluate("TEST123", AccessArea.Site, vehicle(siteAccess = true), true, now)
        assertEquals(AccessDecisionStatus.Granted, result.status)
    }

    @Test
    fun deniedWhenPermissionIsDisabled() {
        val result = checker.evaluate("TEST123", AccessArea.Site, vehicle(siteAccess = false), true, now)
        assertEquals(AccessDecisionStatus.Denied, result.status)
    }

    @Test
    fun notYetValidWhenStartIsInFuture() {
        val vehicle = vehicle(siteAccess = true, siteStart = now.plusDays(1))
        val result = checker.evaluate("TEST123", AccessArea.Site, vehicle, true, now)
        assertEquals(AccessDecisionStatus.NotYetValid, result.status)
    }

    @Test
    fun expiredWhenEndIsInPast() {
        val vehicle = vehicle(siteAccess = true, siteEnd = now.minusDays(1))
        val result = checker.evaluate("TEST123", AccessArea.Site, vehicle, true, now)
        assertEquals(AccessDecisionStatus.Expired, result.status)
    }

    @Test
    fun vehicleNotFoundWhenLookupReturnsNothing() {
        val result = checker.evaluate("TEST123", AccessArea.Site, null, true, now)
        assertEquals(AccessDecisionStatus.VehicleNotFound, result.status)
    }

    @Test
    fun invalidInputWhenPlateIsEmpty() {
        val result = checker.evaluate("", AccessArea.Site, null, true, now)
        assertEquals(AccessDecisionStatus.InvalidInput, result.status)
    }

    @Test
    fun dataUnavailableWhenNoSnapshotExists() {
        val result = checker.evaluate("TEST123", AccessArea.Site, null, false, now)
        assertEquals(AccessDecisionStatus.DataUnavailable, result.status)
    }

    @Test
    fun nullValidityBoundariesAreCurrentlyTreatedAsOpenEnded() {
        val result = checker.evaluate("TEST123", AccessArea.Site, vehicle(siteAccess = true), true, now)
        assertEquals(AccessDecisionStatus.Granted, result.status)
    }

    @Test
    fun requestedAreaIsEvaluatedExplicitly() {
        val vehicle = vehicle(siteAccess = false, campAccess = true)
        val siteResult = checker.evaluate("TEST123", AccessArea.Site, vehicle, true, now)
        val campResult = checker.evaluate("TEST123", AccessArea.Camp, vehicle, true, now)

        assertEquals(AccessDecisionStatus.Denied, siteResult.status)
        assertEquals(AccessDecisionStatus.Granted, campResult.status)
    }

    private fun vehicle(
        siteAccess: Boolean = true,
        campAccess: Boolean = false,
        siteStart: LocalDateTime? = null,
        siteEnd: LocalDateTime? = null
    ) = VehicleRecord(
        sourceVehicleId = 1,
        normalizedLicensePlate = "TEST123",
        displayLicensePlate = "TEST 123",
        countryId = 0,
        brand = "TestBrand",
        model = "TestModel",
        color = "Blue",
        personId = null,
        departmentId = null,
        hasParkingLotAccess = false,
        parkingLotValidity = ValidityWindow(null, null),
        hasSiteAccess = siteAccess,
        siteValidity = ValidityWindow(siteStart, siteEnd),
        hasCampAccess = campAccess,
        campValidity = ValidityWindow(null, null),
        isTemporaryPlate = false,
        isPrivate = false,
        isRentalCar = false,
        accessNotes = null
    )
}