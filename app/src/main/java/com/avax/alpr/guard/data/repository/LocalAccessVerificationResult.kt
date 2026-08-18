package com.avax.alpr.guard.data.repository

import com.avax.alpr.guard.data.local.AccessLogPersistenceStatus
import com.avax.alpr.guard.domain.model.AccessDecision

data class LocalAccessVerificationResult(
    val decision: AccessDecision,
    val logPersistenceStatus: AccessLogPersistenceStatus
)