package com.example.ytpiano.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PianoApi {

    @POST("api/transcriptions")
    suspend fun createTranscription(
        @Body request: CreateTranscriptionRequest
    ): CreateTranscriptionResponse

    @GET("api/transcriptions/{jobId}")
    suspend fun getTranscription(
        @Path("jobId") jobId: String
    ): TranscriptionStatusResponse
}