package com.lumenchord.pianoweave.api

import okhttp3.RequestBody
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
        @Header("Authorization") authHeader: String? = null,
        @Header("X-User-Id") userId: String? = null
    ): UserBalanceResponse

    /**
     * Cost of a video link, or of a file of the given length before it is uploaded. Pass exactly one. A
     * billed server answers only signed-in users (401 without [authHeader]).
     */
    @GET("api/billing/cost")
    suspend fun getBillingCost(
        @Query("source_url") sourceUrl: String? = null,
        @Query("duration_seconds") durationSeconds: Float? = null,
        @Header("Authorization") authHeader: String? = null
    ): BillingCostResponse

    @POST("api/billing/google-play/verify")
    suspend fun verifyPurchase(
        @Header("Authorization") authHeader: String? = null,
        @Header("X-User-Id") userId: String? = null,
        @Body request: VerifyPurchaseRequest
    ): VerifyPurchaseResponse

    @POST("api/transcriptions")
    suspend fun createTranscription(
        @Body request: CreateTranscriptionRequest,
        @Header("Authorization") authHeader: String? = null,
        @Header("X-User-Id") userId: String? = null
    ): CreateTranscriptionResponse

    /** Transcribes an audio or video file sent as the raw body; [filename] becomes the song's title. */
    @POST("api/transcriptions/upload")
    suspend fun uploadTranscription(
        @Query("filename") filename: String,
        @Body file: RequestBody,
        @Header("Authorization") authHeader: String? = null,
        @Header("X-User-Id") userId: String? = null
    ): CreateTranscriptionResponse

    @GET("api/transcriptions/{jobId}")
    suspend fun getTranscription(
        @Path("jobId") jobId: String,
        @Header("Authorization") authHeader: String? = null
    ): TranscriptionStatusResponse

    @DELETE("api/transcriptions/{jobId}")
    suspend fun deleteTranscription(
        @Path("jobId") jobId: String,
        @Header("Authorization") authHeader: String? = null,
        @Header("X-User-Id") userId: String? = null
    ): TranscriptionStatusResponse

    @Streaming
    @GET("api/transcriptions/{jobId}/midi")
    suspend fun downloadMidi(
        @Path("jobId") jobId: String,
        @Header("Authorization") authHeader: String? = null
    ): ResponseBody
}
