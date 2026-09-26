package com.example.pianoweave.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface PianoApi {

    @GET(".")
    suspend fun checkHealth(): Response<ResponseBody>

    @POST("api/transcriptions")
    suspend fun createTranscription(
        @Body request: CreateTranscriptionRequest
    ): CreateTranscriptionResponse

    @GET("api/transcriptions/{jobId}")
    suspend fun getTranscription(
        @Path("jobId") jobId: String
    ): TranscriptionStatusResponse

    @DELETE("api/transcriptions/{jobId}")
    suspend fun deleteTranscription(
        @Path("jobId") jobId: String
    ): Response<ResponseBody>

    @Streaming
    @GET("api/transcriptions/{jobId}/midi")
    suspend fun downloadMidi(
        @Path("jobId") jobId: String
    ): ResponseBody
}
