package com.avax.alpr.guard.domain.model

enum class AccessDecisionStatus {
    Granted,
    Denied,
    NotYetValid,
    Expired,
    VehicleNotFound,
    InvalidInput,
    DataUnavailable
}