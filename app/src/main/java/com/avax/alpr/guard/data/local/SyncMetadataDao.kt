package com.avax.alpr.guard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun get(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAll()
}