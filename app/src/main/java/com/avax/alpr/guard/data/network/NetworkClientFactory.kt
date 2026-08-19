package com.avax.alpr.guard.data.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClientFactory {

    fun createVehicleSyncApi(baseUrl: String): VehicleSyncApi {
        return createRetrofit(baseUrl).create(VehicleSyncApi::class.java)
    }

    fun createAccessLogApi(baseUrl: String): AccessLogApi {
        return createRetrofit(baseUrl).create(AccessLogApi::class.java)
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        require(baseUrl.endsWith("/")) { "API base URL must end with '/'." }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}