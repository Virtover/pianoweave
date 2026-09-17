package com.example.ytpiano.api

import com.google.gson.annotations.SerializedName

data class CreateTranscriptionRequest(
    @SerializedName("source_url")
    val source_url: String
)

data class CreateTranscriptionResponse(
    val job_id: String,
    val status: String
)

data class TranscriptionStatusResponse(
    val job_id: String,
    val status: String,
    val progress: Float,
    val error: String?,
    val metadata: VideoMetadata? = null
)

data class VideoMetadata(
    val title: String,
    val author: String,
    val channel: String,
    val channel_id: String,
    val channel_url: String,
    val upload_date: String,
    val duration: Float,
    val thumbnail: String,
    val webpage_url: String,
    val view_count: Long,
    val like_count: Long
)