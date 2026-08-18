package com.avax.alpr.guard.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecisionStatus

@Entity(
    tableName = "access_logs",
    indices = [
        Index(value = ["syncState", "eventTimestampUtc"]),
        Index(value = ["eventTimestampUtc"])
    ]
)
data class AccessLogEntity(
    @PrimaryKey
    val localLogId: String,

    val eventTimestampUtc: String,

    val inputLicensePlate: String,

    val normalizedLicensePlate: String?,

    val sourceVehicleId: Int?,

    val accessArea: AccessArea,

    val decisionStatus: AccessDecisionStatus,

    val areaAccessEnabled: Boolean?,

    val areaValidityStart: String?,

    val areaValidityEnd: String?,

    val accessNotes: String?,

    val syncState: AccessLogSyncState
)