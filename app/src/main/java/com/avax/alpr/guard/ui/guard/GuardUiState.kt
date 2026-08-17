package com.avax.alpr.guard.ui.guard

import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision

data class GuardUiState(
    val plateInput: String = "",
    val selectedArea: AccessArea = AccessArea.Site,
    val accessDecision: AccessDecision? = null,
    val isVerifying: Boolean = false,
    val isSyncing: Boolean = false,
    val hasLocalSnapshot: Boolean = false,
    val cachedVehicleCount: Int = 0,
    val snapshotGeneratedAtUtc: String? = null,
    val importedAtUtc: String? = null,
    val syncMessage: String? = null
)