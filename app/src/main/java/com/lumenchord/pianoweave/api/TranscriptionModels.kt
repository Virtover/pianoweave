package com.lumenchord.pianoweave.api

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
    val metadata: VideoMetadata? = null,
    val minutes: Int? = null
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

data class ServerOffer(
    @SerializedName("product_id") val productId: String,
    @SerializedName("transcription_minutes") val transcriptionMinutes: Int
)

data class ServerInfoResponse(
    @SerializedName("billing_provider") val billingProvider: String? = "none",
    val offers: List<ServerOffer>? = emptyList(),
    @SerializedName("cleanup_interval_seconds") val cleanupIntervalSeconds: Long? = null,
    @SerializedName("max_video_length_minutes") val maxVideoLengthMinutes: Int? = null,
    @SerializedName("free_minutes") val freeMinutes: Int? = null,
    @SerializedName("free_minutes_period") val freeMinutesPeriod: String? = null,
    @SerializedName("support_me_url") val supportMeUrl: String? = null
)

data class UserBalanceResponse(
    @SerializedName("user_id") val userId: String,
    val minutes: Int,
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
    val minutes: Int
)