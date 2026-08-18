package com.avax.alpr.guard.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class GuardDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migrationFrom1To2PreservesVehicleAndMetadataAndCreatesAccessLogs() =
        runBlocking {

            createVersion1Database()

            val database = Room.databaseBuilder(
                context,
                GuardDatabase::class.java,
                TEST_DATABASE_NAME
            )
                .addMigrations(
                    GuardDatabaseMigrations.MIGRATION_1_2
                )
                .build()

            try {
                val vehicle = database.vehicleDao()
                    .findByNormalizedPlate("TEST123")

                val metadata = database.syncMetadataDao().get()

                assertNotNull(vehicle)
                assertEquals(1, vehicle?.sourceVehicleId)

                assertNotNull(metadata)
                assertEquals(1, metadata?.vehicleCount)

                assertEquals(
                    0,
                    database.accessLogDao().count()
                )
            } finally {
                database.close()
            }
        }

    private fun createVersion1Database() {
        val databaseFile =
            context.getDatabasePath(TEST_DATABASE_NAME)

        databaseFile.parentFile?.mkdirs()

        val database = SQLiteDatabase.openOrCreateDatabase(
            databaseFile,
            null
        )

        try {
            database.execSQL(
                """
                CREATE TABLE `vehicles` (
                    `normalizedLicensePlate` TEXT NOT NULL,
                    `sourceVehicleId` INTEGER NOT NULL,
                    `displayLicensePlate` TEXT NOT NULL,
                    `countryId` INTEGER NOT NULL,
                    `brand` TEXT,
                    `model` TEXT,
                    `color` TEXT,
                    `personId` INTEGER,
                    `departmentId` INTEGER,
                    `hasParkingLotAccess` INTEGER NOT NULL,
                    `parkingLotAccessStart` TEXT,
                    `parkingLotAccessEnd` TEXT,
                    `hasSiteAccess` INTEGER NOT NULL,
                    `siteAccessStart` TEXT,
                    `siteAccessEnd` TEXT,
                    `hasCampAccess` INTEGER NOT NULL,
                    `campAccessStart` TEXT,
                    `campAccessEnd` TEXT,
                    `isTemporaryPlate` INTEGER NOT NULL,
                    `isPrivate` INTEGER NOT NULL,
                    `isRentalCar` INTEGER,
                    `accessNotes` TEXT,
                    PRIMARY KEY(`normalizedLicensePlate`)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE `sync_metadata` (
                    `id` INTEGER NOT NULL,
                    `contractVersion` INTEGER NOT NULL,
                    `snapshotGeneratedAtUtc` TEXT NOT NULL,
                    `importedAtUtc` TEXT NOT NULL,
                    `vehicleCount` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO `vehicles` (
                    normalizedLicensePlate,
                    sourceVehicleId,
                    displayLicensePlate,
                    countryId,
                    hasParkingLotAccess,
                    hasSiteAccess,
                    hasCampAccess,
                    isTemporaryPlate,
                    isPrivate
                )
                VALUES (
                    'TEST123',
                    1,
                    'TEST 123',
                    0,
                    0,
                    1,
                    0,
                    0,
                    0
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO `sync_metadata` (
                    id,
                    contractVersion,
                    snapshotGeneratedAtUtc,
                    importedAtUtc,
                    vehicleCount
                )
                VALUES (
                    1,
                    1,
                    '2026-08-18T10:00:00Z',
                    '2026-08-18T10:01:00Z',
                    1
                )
                """.trimIndent()
            )

            database.version = 1
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TEST_DATABASE_NAME =
            "guard_database_migration_test.db"
    }
}