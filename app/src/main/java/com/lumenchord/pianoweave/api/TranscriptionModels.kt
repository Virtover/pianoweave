package com.lumenchord.pianoweave.api

import com.google.gson.annotations.SerializedName

data class CreateTranscriptionRequest(
    @SerializedName("source_url") val source_url: String
)

data class CreateTranscriptionResponse(
    @SerializedName("job_id") val job_id: String,
    @SerializedName("status") val status: String
)

data class TranscriptionStatusResponse(
    @SerializedName("job_id") val job_id: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Float,
    @SerializedName("error") val error: String?,
    @SerializedName("metadata") val metadata: VideoMetadata? = null,
    @SerializedName("minutes") val minutes: Int? = null
)

data class VideoMetadata(
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("channel") val channel: String,
    @SerializedName("channel_id") val channel_id: String,
    @SerializedName("channel_url") val channel_url: String,
    @SerializedName("upload_date") val upload_date: String,
    @SerializedName("duration") val duration: Float,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("webpage_url") val webpage_url: String,
    @SerializedName("view_count") val view_count: Long,
    @SerializedName("like_count") val like_count: Long
)

data class ServerOffer(
    @SerializedName("product_id") val productId: String,
    @SerializedName("transcription_minutes") val transcriptionMinutes: Int
)

data class ServerInfoResponse(
    @SerializedName("billing_provider") val billingProvider: String? = "none",
    @SerializedName("offers") val offers: List<ServerOffer>? = emptyList(),
    @SerializedName("cleanup_interval_seconds") val cleanupIntervalSeconds: Long? = null,
    @SerializedName("max_video_length_minutes") val maxVideoLengthMinutes: Int? = null,
    @SerializedName("free_minutes") val freeMinutes: Int? = null,
    @SerializedName("free_minutes_period") val freeMinutesPeriod: String? = null,
    @SerializedName("google_oauth_client_id", alternate = ["google_client_id", "oauth_client_id", "client_id", "google_oauth_client"]) val googleClientId: String? = null,
)

data class UserBalanceResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("minutes") val minutes: Int,
    @SerializedName("free_minutes_seconds_until_next_grant") val freeMinutesSecondsUntilNextGrant: Long? = null,
    @SerializedName("free_minutes_next_grant_at") val freeMinutesNextGrantAt: Long? = null
)

data class BillingCostResponse(
    @SerializedName("duration_seconds") val durationSeconds: Float,
    @SerializedName("cost_minutes") val costMinutes: Int
)

data class VerifyPurchaseRequest(
    @SerializedName("product_id") val productId: String,
    @SerializedName("purchase_token") val purchaseToken: String
)

data class VerifyPurchaseResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("credited_minutes") val creditedMinutes: Int,
    @SerializedName("minutes") val minutes: Int
)
