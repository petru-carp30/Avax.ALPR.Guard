package com.avax.alpr.guard.data.network.dto

data class VehicleSyncResponseDto(
    val contractVersion: Int? = null,
    val snapshotGeneratedAtUtc: String? = null,
    val vehicleCount: Int? = null,
    val vehicles: List<VehicleSyncItemDto>? = null
)