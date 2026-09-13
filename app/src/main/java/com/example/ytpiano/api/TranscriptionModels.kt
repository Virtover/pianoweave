package com.example.ytpiano.api

data class CreateTranscriptionRequest(
    val youtube_url: String
)

data class CreateTranscriptionResponse(
    val job_id: String,
    val status: String
)

data class TranscriptionStatusResponse(
    val job_id: String,
    val status: String,
    val progress: Float,
    val error: String?
)