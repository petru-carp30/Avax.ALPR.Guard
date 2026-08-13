package com.avax.alpr.guard.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.avax.alpr.guard.data.local.GuardDatabase
import com.avax.alpr.guard.data.local.SyncMetadataEntity
import com.avax.alpr.guard.data.local.VehicleCacheStore
import com.avax.alpr.guard.data.local.VehicleEntity
import com.avax.alpr.guard.data.network.NetworkStatusProvider
import com.avax.alpr.guard.data.network.VehicleSyncApi
import com.avax.alpr.guard.data.network.dto.VehicleSyncItemDto
import com.avax.alpr.guard.data.network.dto.VehicleSyncResponseDto
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class VehicleSyncRepositoryTest {

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
    fun validSnapshotImportsSuccessfully() = runBlocking {
        val repository = repository(api = FakeApi { Response.success(validResponse()) })

        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.Success)
        Assert.assertEquals(1, database.vehicleDao().count())
        Assert.assertNotNull(database.vehicleDao().findByNormalizedPlate("TEST123"))

        val metadata = database.syncMetadataDao().get()
        Assert.assertNotNull(metadata)
        Assert.assertEquals(1, metadata?.contractVersion)
        Assert.assertEquals(1, metadata?.vehicleCount)
        Assert.assertEquals("2026-08-13T08:00:00Z", metadata?.snapshotGeneratedAtUtc)
    }

    @Test
    fun unsupportedContractVersionPreservesPreviousCache() = runBlocking {
        seedOldCache()

        val response = validResponse().copy(
            contractVersion = 2,
            vehicles = listOf(vehicle("NEW123", "NEW 123"))
        )

        val repository = repository(api = FakeApi { Response.success(response) })
        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.UnsupportedContractVersion)
        assertOldCachePreserved()
    }

    @Test
    fun vehicleCountMismatchPreservesPreviousCache() = runBlocking {
        seedOldCache()

        val response = validResponse().copy(
            vehicleCount = 2,
            vehicles = listOf(vehicle("NEW123", "NEW 123"))
        )

        val repository = repository(api = FakeApi { Response.success(response) })
        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.MalformedSnapshot)
        assertOldCachePreserved()
    }

    @Test
    fun conflictPreservesPreviousCache() = runBlocking {
        seedOldCache()

        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        val repository = repository(api = FakeApi { Response.error(409, errorBody) })

        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.Conflict)
        assertOldCachePreserved()
    }

    @Test
    fun backendUnavailablePreservesPreviousCache() = runBlocking {
        seedOldCache()

        val repository =
            repository(api = FakeApi { throw IOException("Synthetic backend failure") })
        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.BackendUnavailable)
        assertOldCachePreserved()
    }

    @Test
    fun noNetworkDoesNotCallBackend() = runBlocking {
        var apiCallCount = 0

        val api = FakeApi {
            apiCallCount++
            Response.success(validResponse())
        }

        val repository = repository(api = api, networkAvailable = false)
        val result = repository.synchronize()

        Assert.assertTrue(result is SyncResult.NoNetwork)
        Assert.assertEquals(0, apiCallCount)
        Assert.assertEquals(0, database.vehicleDao().count())
    }

    private fun repository(api: VehicleSyncApi, networkAvailable: Boolean = true) = VehicleSyncRepository(
        api = api,
        networkStatusProvider = FakeNetworkStatusProvider(networkAvailable),
        snapshotValidator = SnapshotValidator(),
        vehicleCacheStore = cacheStore
    )

    private suspend fun seedOldCache() {
        cacheStore.replaceSnapshot(
            vehicles = listOf(oldVehicle()),
            metadata = SyncMetadataEntity(
                contractVersion = 1,
                snapshotGeneratedAtUtc = "2026-08-12T08:00:00Z",
                importedAtUtc = "2026-08-12T08:01:00Z",
                vehicleCount = 1
            )
        )
    }

    private suspend fun assertOldCachePreserved() {
        Assert.assertNotNull(database.vehicleDao().findByNormalizedPlate("OLD123"))
        Assert.assertNull(database.vehicleDao().findByNormalizedPlate("NEW123"))
        Assert.assertEquals(1, database.vehicleDao().count())

        val metadata = database.syncMetadataDao().get()
        Assert.assertNotNull(metadata)
        Assert.assertEquals("2026-08-12T08:00:00Z", metadata?.snapshotGeneratedAtUtc)
        Assert.assertEquals(1, metadata?.vehicleCount)
    }

    private fun validResponse() = VehicleSyncResponseDto(
        contractVersion = 1,
        snapshotGeneratedAtUtc = "2026-08-13T08:00:00Z",
        vehicleCount = 1,
        vehicles = listOf(vehicle())
    )

    private fun vehicle(
        normalizedPlate: String = "TEST123",
        displayPlate: String = "TEST 123"
    ) = VehicleSyncItemDto(
        sourceVehicleId = 1,
        normalizedLicensePlate = normalizedPlate,
        displayLicensePlate = displayPlate,
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

    private fun oldVehicle() = VehicleEntity(
        normalizedLicensePlate = "OLD123",
        sourceVehicleId = 100,
        displayLicensePlate = "OLD 123",
        countryId = 0,
        brand = "OldBrand",
        model = "OldModel",
        color = "Gray",
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

    private class FakeNetworkStatusProvider(private val available: Boolean) :
        NetworkStatusProvider {
        override fun isNetworkAvailable() = available
    }

    private class FakeApi(private val responseProvider: suspend () -> Response<VehicleSyncResponseDto>) :
        VehicleSyncApi {
        override suspend fun getVehicleSnapshot() = responseProvider()
    }
}