package com.avax.alpr.guard.domain.model

data class VehicleRecord(
    val sourceVehicleId: Int,
    val normalizedLicensePlate: String,
    val displayLicensePlate: String,
    val countryId: Int,
    val brand: String?,
    val model: String?,
    val color: String?,
    val personId: Int?,
    val departmentId: Int?,
    val hasParkingLotAccess: Boolean,
    val parkingLotValidity: ValidityWindow,
    val hasSiteAccess: Boolean,
    val siteValidity: ValidityWindow,
    val hasCampAccess: Boolean,
    val campValidity: ValidityWindow,
    val isTemporaryPlate: Boolean,
    val isPrivate: Boolean,
    val isRentalCar: Boolean?,
    val accessNotes: String?
)