package com.lumenchord.pianoweave.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface PianoApi {

    @GET(".")
    suspend fun checkHealth(): Response<ResponseBody>

    @GET("api/server-info")
    suspend fun getServerInfo(): ServerInfoResponse

    @GET("api/billing/balance")
    suspend fun getUserBalance(
        @Header("X-User-Id") userId: String
    ): UserBalanceResponse

    @GET("api/billing/cost")
    suspend fun getBillingCost(
        @Query("source_url") sourceUrl: String
    ): BillingCostResponse

    @POST("api/billing/google-play/verify")
    suspend fun verifyPurchase(
        @Header("X-User-Id") userId: String,
        @Body request: VerifyPurchaseRequest
    ): VerifyPurchaseResponse

    @POST("api/transcriptions")
    suspend fun createTranscription(
        @Body request: CreateTranscriptionRequest,
        @Header("X-User-Id") userId: String? = null
    ): CreateTranscriptionResponse

    @GET("api/transcriptions/{jobId}")
    suspend fun getTranscription(
        @Path("jobId") jobId: String
    ): TranscriptionStatusResponse

    @DELETE("api/transcriptions/{jobId}")
    suspend fun deleteTranscription(
        @Path("jobId") jobId: String,
        @Header("X-User-Id") userId: String? = null
    ): TranscriptionStatusResponse

    @Streaming
    @GET("api/transcriptions/{jobId}/midi")
    suspend fun downloadMidi(
        @Path("jobId") jobId: String
    ): ResponseBody
}
