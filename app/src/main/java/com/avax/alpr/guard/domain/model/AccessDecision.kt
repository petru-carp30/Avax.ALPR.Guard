package com.avax.alpr.guard.domain.model

data class AccessDecision(
    val status: AccessDecisionStatus,
    val requestedArea: AccessArea,
    val normalizedLicensePlate: String,
    val vehicle: VehicleRecord? = null
)