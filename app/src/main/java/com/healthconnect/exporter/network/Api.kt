package com.healthconnect.exporter.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface HcgatewayApi {

    @POST("/v1/ingest/batch")
    suspend fun ingestBatch(
        @Header("Authorization") authorization: String,
        @Header("X-Device-ID") deviceId: String,
        @Body request: BatchIngestRequest
    ): Response<BatchIngestResponse>

    @GET("/v1/health")
    suspend fun healthCheck(
        @Header("Authorization") authorization: String
    ): Response<HealthResponse>
}
