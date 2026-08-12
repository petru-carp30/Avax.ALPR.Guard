package com.avax.alpr.guard.domain

import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.LocalDateTime

class AccessChecker {

    fun evaluate(
        normalizedPlate: String,
        requestedArea: AccessArea,
        vehicle: VehicleRecord?,
        hasLocalSnapshot: Boolean,
        now: LocalDateTime
    ): AccessDecision {
        if (normalizedPlate.isBlank()) return AccessDecision(AccessDecisionStatus.InvalidInput, requestedArea, normalizedPlate)
        if (!hasLocalSnapshot) return AccessDecision(AccessDecisionStatus.DataUnavailable, requestedArea, normalizedPlate)
        if (vehicle == null) return AccessDecision(AccessDecisionStatus.VehicleNotFound, requestedArea, normalizedPlate)

        val areaAccess = when (requestedArea) {
            AccessArea.ParkingLot -> AreaAccess(vehicle.hasParkingLotAccess, vehicle.parkingLotValidity)
            AccessArea.Site -> AreaAccess(vehicle.hasSiteAccess, vehicle.siteValidity)
            AccessArea.Camp -> AreaAccess(vehicle.hasCampAccess, vehicle.campValidity)
        }

        if (!areaAccess.enabled) return AccessDecision(AccessDecisionStatus.Denied, requestedArea, normalizedPlate, vehicle)

        val start = areaAccess.validity.start
        if (start != null && now.isBefore(start)) return AccessDecision(AccessDecisionStatus.NotYetValid, requestedArea, normalizedPlate, vehicle)

        val end = areaAccess.validity.end
        if (end != null && now.isAfter(end)) return AccessDecision(AccessDecisionStatus.Expired, requestedArea, normalizedPlate, vehicle)

        return AccessDecision(AccessDecisionStatus.Granted, requestedArea, normalizedPlate, vehicle)
    }

    private data class AreaAccess(val enabled: Boolean, val validity: ValidityWindow)
}