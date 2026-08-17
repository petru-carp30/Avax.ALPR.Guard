package com.avax.alpr.guard

import android.content.Context
import com.avax.alpr.guard.data.local.GuardDatabase
import com.avax.alpr.guard.data.local.VehicleCacheStore
import com.avax.alpr.guard.data.network.AndroidNetworkStatusProvider
import com.avax.alpr.guard.data.network.NetworkClientFactory
import com.avax.alpr.guard.data.repository.SnapshotValidator
import com.avax.alpr.guard.data.repository.VehicleAccessRepository
import com.avax.alpr.guard.data.repository.VehicleSyncRepository
import com.avax.alpr.guard.domain.AccessChecker

class AppContainer(context: Context) {

    private val database = GuardDatabase.create(context)

    private val vehicleSyncApi = NetworkClientFactory.createVehicleSyncApi(BuildConfig.API_BASE_URL)

    val vehicleSyncRepository = VehicleSyncRepository(
        api = vehicleSyncApi,
        networkStatusProvider = AndroidNetworkStatusProvider(context),
        snapshotValidator = SnapshotValidator(),
        vehicleCacheStore = VehicleCacheStore(database)
    )

    val vehicleAccessRepository = VehicleAccessRepository(
        vehicleDao = database.vehicleDao(),
        syncMetadataDao = database.syncMetadataDao(),
        accessChecker = AccessChecker()
    )
}