package com.avax.alpr.guard.data.local

import androidx.room.withTransaction

class VehicleCacheStore(private val database: GuardDatabase) {

    suspend fun replaceSnapshot(vehicles: List<VehicleEntity>, metadata: SyncMetadataEntity) {
        database.withTransaction {
            database.vehicleDao().deleteAll()
            database.vehicleDao().insertAll(vehicles)
            database.syncMetadataDao().upsert(metadata)
        }
    }
}