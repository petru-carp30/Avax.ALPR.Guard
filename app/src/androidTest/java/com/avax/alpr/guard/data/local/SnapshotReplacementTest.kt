package com.avax.alpr.guard.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SnapshotReplacementTest {

    private lateinit var context: Context
    private lateinit var database: GuardDatabase
    private lateinit var cacheStore: VehicleCacheStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(context, GuardDatabase::class.java).build()
        cacheStore = VehicleCacheStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validSnapshotReplacesPreviousCache() = runBlocking {
        cacheStore.replaceSnapshot(
            vehicles = listOf(vehicle("OLD123", 1)),
            metadata = metadata("2026-08-12T10:00:00Z", 1)
        )

        cacheStore.replaceSnapshot(
            vehicles = listOf(vehicle("NEW123", 2), vehicle("NEW456", 3)),
            metadata = metadata("2026-08-12T11:00:00Z", 2)
        )

        assertNull(database.vehicleDao().findByNormalizedPlate("OLD123"))
        assertNotNull(database.vehicleDao().findByNormalizedPlate("NEW123"))
        assertNotNull(database.vehicleDao().findByNormalizedPlate("NEW456"))
        assertEquals(2, database.vehicleDao().count())

        val metadata = database.syncMetadataDao().get()

        assertNotNull(metadata)
        assertEquals(2, metadata?.vehicleCount)
        assertEquals("2026-08-12T11:00:00Z", metadata?.snapshotGeneratedAtUtc)
    }

    @Test
    fun failedSnapshotReplacementPreservesPreviousCache() = runBlocking {
        cacheStore.replaceSnapshot(
            vehicles = listOf(vehicle("OLD123", 1)),
            metadata = metadata("2026-08-12T10:00:00Z", 1)
        )

        val invalidSnapshot = listOf(
            vehicle("DUP123", 2),
            vehicle("DUP123", 3)
        )

        var importFailed = false

        try {
            cacheStore.replaceSnapshot(
                vehicles = invalidSnapshot,
                metadata = metadata("2026-08-12T11:00:00Z", 2)
            )
        } catch (_: Exception) {
            importFailed = true
        }

        assertTrue(importFailed)

        val oldVehicle = database.vehicleDao().findByNormalizedPlate("OLD123")
        val duplicateVehicle = database.vehicleDao().findByNormalizedPlate("DUP123")
        val metadata = database.syncMetadataDao().get()

        assertNotNull(oldVehicle)
        assertNull(duplicateVehicle)
        assertEquals(1, database.vehicleDao().count())

        assertNotNull(metadata)
        assertEquals(1, metadata?.vehicleCount)
        assertEquals("2026-08-12T10:00:00Z", metadata?.snapshotGeneratedAtUtc)
    }

    private fun vehicle(normalizedPlate: String, sourceVehicleId: Int) = VehicleEntity(
        normalizedLicensePlate = normalizedPlate,
        sourceVehicleId = sourceVehicleId,
        displayLicensePlate = normalizedPlate,
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
        accessNotes = null
    )

    private fun metadata(snapshotGeneratedAtUtc: String, vehicleCount: Int) = SyncMetadataEntity(
        contractVersion = 1,
        snapshotGeneratedAtUtc = snapshotGeneratedAtUtc,
        importedAtUtc = "2026-08-12T12:00:00Z",
        vehicleCount = vehicleCount
    )
}