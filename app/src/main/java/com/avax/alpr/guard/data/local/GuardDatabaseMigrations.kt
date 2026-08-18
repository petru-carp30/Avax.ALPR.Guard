package com.avax.alpr.guard.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GuardDatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {

        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `access_logs` (
                    `localLogId` TEXT NOT NULL,
                    `eventTimestampUtc` TEXT NOT NULL,
                    `inputLicensePlate` TEXT NOT NULL,
                    `normalizedLicensePlate` TEXT,
                    `sourceVehicleId` INTEGER,
                    `accessArea` TEXT NOT NULL,
                    `decisionStatus` TEXT NOT NULL,
                    `areaAccessEnabled` INTEGER,
                    `areaValidityStart` TEXT,
                    `areaValidityEnd` TEXT,
                    `accessNotes` TEXT,
                    `syncState` TEXT NOT NULL,
                    PRIMARY KEY(`localLogId`)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_access_logs_syncState_eventTimestampUtc`
                ON `access_logs` (`syncState`, `eventTimestampUtc`)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_access_logs_eventTimestampUtc`
                ON `access_logs` (`eventTimestampUtc`)
                """.trimIndent()
            )
        }
    }
}