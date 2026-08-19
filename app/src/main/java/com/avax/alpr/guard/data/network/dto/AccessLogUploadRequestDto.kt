package com.avax.alpr.guard.data.network.dto

data class AccessLogUploadRequestDto(
    val mobileEventId: String,
    val eventTimestampUtc: String,
    val licensePlate: String,
    val normalizedPlate: String,
    val sourceVehicleId: Int?,
    val accessArea: String,
    val accessDecision: String
)