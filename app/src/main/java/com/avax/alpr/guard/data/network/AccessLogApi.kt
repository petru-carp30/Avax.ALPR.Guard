package com.avax.alpr.guard.data.network

import com.avax.alpr.guard.data.network.dto.AccessLogUploadRequestDto
import com.avax.alpr.guard.data.network.dto.AccessLogUploadResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AccessLogApi {

    @POST("api/access-logs")
    suspend fun uploadAccessLog(@Body request: AccessLogUploadRequestDto): Response<AccessLogUploadResponseDto>
}