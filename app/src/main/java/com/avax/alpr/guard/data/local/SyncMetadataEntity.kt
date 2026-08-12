package com.avax.alpr.guard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val contractVersion: Int,
    val snapshotGeneratedAtUtc: String,
    val importedAtUtc: String,
    val vehicleCount: Int
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}