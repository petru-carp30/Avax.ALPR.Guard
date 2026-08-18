package com.avax.alpr.guard.ui.guard

import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus

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
    val syncMessage: String? = null,
    val localLogMessage: String? = null,
    val recentAccessLogs: List<RecentAccessLogUiItem> = emptyList()
)

data class RecentAccessLogUiItem(
    val localLogId: String,
    val eventTimestampUtc: String,
    val licensePlate: String,
    val accessArea: AccessArea,
    val decisionStatus: AccessDecisionStatus,
    val syncState: AccessLogSyncState
)