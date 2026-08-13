package com.avax.alpr.guard.data.network

import com.avax.alpr.guard.data.network.dto.VehicleSyncResponseDto
import retrofit2.Response
import retrofit2.http.GET

interface VehicleSyncApi {

    @GET("api/sync/vehicles")
    suspend fun getVehicleSnapshot(): Response<VehicleSyncResponseDto>
}