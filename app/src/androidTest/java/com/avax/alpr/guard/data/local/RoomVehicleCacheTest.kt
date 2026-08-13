package com.avax.alpr.guard.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomVehicleCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun vehicleCanBeStoredAndQueriedByNormalizedPlate() = runBlocking {
        val database = createDatabase()

        try {
            database.vehicleDao().insertAll(listOf(testVehicle()))

            val result = database.vehicleDao().findByNormalizedPlate("TEST123")

            assertNotNull(result)
            assertEquals("TEST123", result?.normalizedLicensePlate)
            assertEquals("TEST 123", result?.displayLicensePlate)
            assertEquals("TestBrand", result?.brand)
        } finally {
            database.close()
        }
    }

    @Test
    fun missingPlateReturnsNull() = runBlocking {
        val database = createDatabase()

        try {
            database.vehicleDao().insertAll(listOf(testVehicle()))

            val result = database.vehicleDao().findByNormalizedPlate("MISSING123")

            assertNull(result)
        } finally {
            database.close()
        }
    }

    @Test
    fun cacheSurvivesDatabaseReopen() = runBlocking {
        var database = createDatabase()

        database.vehicleDao().insertAll(listOf(testVehicle()))
        database.syncMetadataDao().upsert(testMetadata())
        database.close()

        database = createDatabase()

        try {
            val vehicle = database.vehicleDao().findByNormalizedPlate("TEST123")
            val metadata = database.syncMetadataDao().get()

            assertNotNull(vehicle)
            assertEquals("TEST123", vehicle?.normalizedLicensePlate)
            assertNotNull(metadata)
            assertEquals(1, metadata?.vehicleCount)
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): GuardDatabase {
        return Room.databaseBuilder(context, GuardDatabase::class.java, TEST_DATABASE_NAME).build()
    }

    private fun testVehicle() = VehicleEntity(
        normalizedLicensePlate = "TEST123",
        sourceVehicleId = 1,
        displayLicensePlate = "TEST 123",
        countryId = 0,
        brand = "TestBrand",
        model = "TestModel",
        color = "Blue",
        personId = null,
        departmentId = null,
        hasParkingLotAccess = false,
        parkingLotAccessStart = null,
        parkingLotAccessEnd = null,
        hasSiteAccess = true,
        siteAccessStart = null,
        siteAccessEnd = null,
        hasCampAccess = false,
        campAccessStart = null,
        campAccessEnd = null,
        isTemporaryPlate = false,
        isPrivate = false,
        isRentalCar = false,
        accessNotes = "Synthetic test vehicle"
    )

    private fun testMetadata() = SyncMetadataEntity(
        contractVersion = 1,
        snapshotGeneratedAtUtc = "2026-08-12T10:00:00Z",
        importedAtUtc = Instant.parse("2026-08-12T10:01:00Z").toString(),
        vehicleCount = 1
    )

    companion object {
        private const val TEST_DATABASE_NAME = "room_vehicle_cache_test.db"
    }
}