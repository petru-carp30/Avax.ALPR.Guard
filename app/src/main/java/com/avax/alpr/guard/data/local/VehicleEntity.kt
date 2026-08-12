package com.avax.alpr.guard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val normalizedLicensePlate: String,
    val sourceVehicleId: Int,
    val displayLicensePlate: String,
    val countryId: Int,
    val brand: String?,
    val model: String?,
    val color: String?,
    val personId: Int?,
    val departmentId: Int?,
    val hasParkingLotAccess: Boolean,
    val parkingLotAccessStart: String?,
    val parkingLotAccessEnd: String?,
    val hasSiteAccess: Boolean,
    val siteAccessStart: String?,
    val siteAccessEnd: String?,
    val hasCampAccess: Boolean,
    val campAccessStart: String?,
    val campAccessEnd: String?,
    val isTemporaryPlate: Boolean,
    val isPrivate: Boolean,
    val isRentalCar: Boolean?,
    val accessNotes: String?
)