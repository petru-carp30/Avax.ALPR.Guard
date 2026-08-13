package com.avax.alpr.guard.data.network.dto

data class VehicleSyncItemDto(
    val sourceVehicleId: Int? = null,
    val normalizedLicensePlate: String? = null,
    val displayLicensePlate: String? = null,
    val countryId: Int? = null,
    val brand: String? = null,
    val model: String? = null,
    val color: String? = null,
    val personId: Int? = null,
    val departmentId: Int? = null,
    val hasParkingLotAccess: Boolean? = null,
    val parkingLotAccessStart: String? = null,
    val parkingLotAccessEnd: String? = null,
    val hasSiteAccess: Boolean? = null,
    val siteAccessStart: String? = null,
    val siteAccessEnd: String? = null,
    val hasCampAccess: Boolean? = null,
    val campAccessStart: String? = null,
    val campAccessEnd: String? = null,
    val isTemporaryPlate: Boolean? = null,
    val isPrivate: Boolean? = null,
    val isRentalCar: Boolean? = null,
    val accessNotes: String? = null
)