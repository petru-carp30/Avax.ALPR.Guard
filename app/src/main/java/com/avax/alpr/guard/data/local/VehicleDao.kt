package com.avax.alpr.guard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE normalizedLicensePlate = :normalizedPlate LIMIT 1")
    suspend fun findByNormalizedPlate(normalizedPlate: String): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}