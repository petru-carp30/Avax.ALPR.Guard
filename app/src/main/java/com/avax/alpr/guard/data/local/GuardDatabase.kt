package com.avax.alpr.guard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VehicleEntity::class, SyncMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GuardDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val DATABASE_NAME = "avax_alpr_guard.db"

        fun create(context: Context): GuardDatabase {
            return Room.databaseBuilder(context.applicationContext, GuardDatabase::class.java, DATABASE_NAME).build()
        }
    }
}