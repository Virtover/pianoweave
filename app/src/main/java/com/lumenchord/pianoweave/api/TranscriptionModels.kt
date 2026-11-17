package com.lumenchord.pianoweave.api

import com.google.gson.annotations.SerializedName

data class CreateTranscriptionRequest(
    @SerializedName("source_url") val source_url: String,
    /**
     * [TranscriptionMode.apiValue] of an arrangement; null (left out) for a transcription, the server's
     * default, so a transcription is the request servers without arrangements know.
     */
    @SerializedName("mode") val mode: String? = null,
    /** Only sent with an arrangement; the server refuses a style on a transcription. */
    @SerializedName("style") val style: String? = null
)

/**
 * What the server makes of a song: a [Transcription] of the piano that is played in it, or an
 * [Arrangement], a piano cover of the whole song (vocals and band included) written by AI.
 */
enum class TranscriptionMode(val apiValue: String) {
    Transcription("transcription"),
    Arrangement("arrangement");

    /** The server's arrangement style for this mode; the app offers a single one. */
    val style: String?
        get() = if (this == Arrangement) ARRANGEMENT_STYLE else null

    companion object {
        fun fromApiValue(value: String?): TranscriptionMode =
            entries.firstOrNull { it.apiValue == value } ?: Transcription
    }
}

/** The middle of the server's three styles (level1 sparse .. level3 dense): full, yet still playable. */
private const val ARRANGEMENT_STYLE = "level2"

/**
 * Marks an arrangement's library key. Songs are keyed by their source (a link, or upload://<job>), and an
 * arrangement of a link is a different song from its transcription, so its key gets this fragment. As a
 * URL fragment it keeps the key a working link that opens the same video.
 */
private const val ARRANGEMENT_KEY_SUFFIX = "#piano-arrangement"

/** The library key of [sourceKey]'s song in [mode]. */
fun libraryKey(sourceKey: String, mode: TranscriptionMode): String =
    if (mode == TranscriptionMode.Arrangement && !sourceKey.isArrangementKey) sourceKey + ARRANGEMENT_KEY_SUFFIX else sourceKey

/** True for a library key of an arrangement (see [libraryKey]). */
val String.isArrangementKey: Boolean
    get() = endsWith(ARRANGEMENT_KEY_SUFFIX)

/** The source a library key was made from: the link or upload key, without the arrangement mark. */
val String.sourceOfLibraryKey: String
    get() = removeSuffix(ARRANGEMENT_KEY_SUFFIX)

val String.libraryKeyMode: TranscriptionMode
    get() = if (isArrangementKey) TranscriptionMode.Arrangement else TranscriptionMode.Transcription

data class CreateTranscriptionResponse(
    @SerializedName("job_id") val job_id: String,
    @SerializedName("status") val status: String,
    /**
     * The balance after this job was charged; final, also for an upload, whose cost the server works out
     * from the decoded audio and may differ a little from the estimate. Null on unbilled servers.
     */
    @SerializedName("minutes") val minutes: Int? = null
)

data class TranscriptionStatusResponse(
    @SerializedName("job_id") val job_id: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Float,
    @SerializedName("error") val error: String?,
    /** Why a failed job failed, as a stable code (see ServerErrors); null otherwise and on older servers. */
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("metadata") val metadata: VideoMetadata? = null,
    @SerializedName("minutes") val minutes: Int? = null,
    @SerializedName("title") val title: String? = null,
    /** [TranscriptionMode.apiValue]; absent on servers without arrangements. */
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("maintenance") val maintenance: MaintenanceNotice? = null,
    /** Where the model runs: "local" or "remote" (RunPod). Absent on older servers. */
    @SerializedName("backend") val backend: String? = null,
    /** What happened to the job; only for a developer's account ([UserBalanceResponse.developer]). */
    @SerializedName("developer") val developer: DeveloperJobDetails? = null
)

/** A developer's view of one job, from the server's records. Times are epoch seconds. */
data class DeveloperJobDetails(
    @SerializedName("duration_seconds") val durationSeconds: Float? = null,
    /** What the job would have cost; a developer is never charged. */
    @SerializedName("cost_minutes") val costMinutes: Int? = null,
    @SerializedName("outsourced") val outsourced: Boolean? = null,
    /** Why a job moved from this server's GPUs to RunPod. */
    @SerializedName("requeue_reason") val requeueReason: String? = null,
    @SerializedName("queued_at") val queuedAt: Double? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("started_at") val startedAt: Long? = null,
    @SerializedName("finished_at") val finishedAt: Long? = null
)

/** The server's live state, from GET api/developer/server; only a developer's account may ask. */
data class DeveloperServerState(
    @SerializedName("active_jobs") val activeJobs: Int = 0,
    @SerializedName("max_active_jobs") val maxActiveJobs: Int? = null,
    /** "local" (this server's GPUs) and "remote" (RunPod). */
    @SerializedName("backends") val backends: Map<String, DeveloperBackendState>? = null,
    @SerializedName("outsourcing") val outsourcing: Boolean? = null,
    /** Null when no local worker has a live heartbeat. */
    @SerializedName("workers") val workers: DeveloperWorkersState? = null,
    @SerializedName("arrangement") val arrangement: Boolean? = null,
    @SerializedName("paused") val paused: Boolean? = null,
    @SerializedName("links_off") val linksOff: Boolean? = null,
    @SerializedName("maintenance") val maintenance: MaintenanceNotice? = null
)

data class DeveloperBackendState(
    @SerializedName("waiting") val waiting: Int = 0,
    @SerializedName("running") val running: Int = 0,
    @SerializedName("oldest_wait_seconds") val oldestWaitSeconds: Float = 0f
)

data class DeveloperWorkersState(
    @SerializedName("count") val count: Int = 0,
    /** Jobs all local workers run at once. */
    @SerializedName("capacity") val capacity: Int = 0,
    @SerializedName("arranging") val arranging: Int = 0,
    @SerializedName("average_job_seconds") val averageJobSeconds: Float? = null
)

/** Why a failed job failed. The server writes its "error" for users, so it is shown as it is. */
val TranscriptionStatusResponse.failureMessage: String
    get() {
        val fallback = if (TranscriptionMode.fromApiValue(mode) == TranscriptionMode.Arrangement) {
            "The server could not arrange this."
        } else "The server could not transcribe this."
        val message = error?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
        // Nothing was wrong with the song: it can simply be sent again, at no loss
        return if (errorCode == ServerErrors.TRANSCRIPTION_INTERRUPTED) {
            "${message.trimEnd('.')}. Its minutes were given back; tap Transcribe to try again."
        } else message
    }

data class VideoMetadata(
    @SerializedName("title") val title: String = PLACEHOLDER_TITLE,
    @SerializedName("author") val author: String = "Unknown",
    @SerializedName("channel") val channel: String = "Unknown",
    @SerializedName("channel_id") val channel_id: String = "",
    @SerializedName("channel_url") val channel_url: String = "",
    @SerializedName("upload_date") val upload_date: String = "",
    @SerializedName("duration") val duration: Float = 0f,
    @SerializedName("thumbnail") val thumbnail: String = "",
    @SerializedName("webpage_url") val webpage_url: String = "",
    @SerializedName("view_count") val view_count: Long = 0L,
    @SerializedName("like_count") val like_count: Long = 0L
)

private const val PLACEHOLDER_TITLE = "Piano Performance"
private const val UNKNOWN = "Unknown"

/** Stand-in for a song whose real metadata is unknown. */
fun placeholderMetadata(videoUrl: String): VideoMetadata = VideoMetadata(
    title = PLACEHOLDER_TITLE,
    author = UNKNOWN,
    channel = UNKNOWN,
    channel_id = "",
    channel_url = "",
    upload_date = "",
    duration = 0f,
    thumbnail = "",
    webpage_url = videoUrl,
    view_count = 0L,
    like_count = 0L
)

/** True for [placeholderMetadata] (also after sanitizing); real transcriptions always have a title or a duration. */
val VideoMetadata.isPlaceholder: Boolean
    get() = title == PLACEHOLDER_TITLE && author == UNKNOWN && channel == UNKNOWN && duration == 0f

/**
 * Gson can put null into these non-null fields (the server sends null for unknown values), so every
 * field is null-checked here.
 */
fun sanitizeVideoMetadata(meta: VideoMetadata?, fallbackUrl: String = ""): VideoMetadata {
    if (meta == null) return placeholderMetadata(fallbackUrl)
    val safeTitle = (meta.title as String?).orEmpty().ifBlank { PLACEHOLDER_TITLE }
    val safeAuthor = (meta.author as String?).orEmpty().ifBlank { UNKNOWN }
    val safeChannel = (meta.channel as String?).orEmpty().ifBlank { UNKNOWN }
    val safeChannelId = (meta.channel_id as String?).orEmpty()
    val safeChannelUrl = (meta.channel_url as String?).orEmpty()
    val safeUploadDate = (meta.upload_date as String?).orEmpty()
    val safeDuration = (meta.duration as Float?) ?: 0f
    val safeThumbnail = (meta.thumbnail as String?).orEmpty()
    val safeWebpageUrl = (meta.webpage_url as String?).orEmpty().ifBlank { fallbackUrl }
    val safeViewCount = (meta.view_count as Long?) ?: 0L
    val safeLikeCount = (meta.like_count as Long?) ?: 0L

    return VideoMetadata(
        title = safeTitle,
        author = safeAuthor,
        channel = safeChannel,
        channel_id = safeChannelId,
        channel_url = safeChannelUrl,
        upload_date = safeUploadDate,
        duration = safeDuration,
        thumbnail = safeThumbnail,
        webpage_url = safeWebpageUrl,
        view_count = safeViewCount,
        like_count = safeLikeCount
    )
}

data class ServerOffer(
    @SerializedName("product_id") val productId: String,
    @SerializedName("transcription_minutes") val transcriptionMinutes: Int
)

/**
 * A subscription plan the server sells, lowest [level] first. Its period's minutes replace the free credits while
 * it runs; [minutesPerPeriod] is null for an unlimited plan, which has a fair-use cap instead.
 */
data class ServerSubscriptionPlan(
    @SerializedName("product_id") val productId: String? = null,
    /** The Play base plan to buy; null means the product's first base plan. */
    @SerializedName("base_plan") val basePlan: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("minutes_per_period") val minutesPerPeriod: Int? = null,
    @SerializedName("unlimited") val unlimited: Boolean? = null,
    /** 1 is the lowest; a higher level is an upgrade. */
    @SerializedName("level") val level: Int? = null
)

data class ServerInfoResponse(
    @SerializedName("billing_provider") val billingProvider: String? = "none",
    @SerializedName("offers") val offers: List<ServerOffer>? = emptyList(),
    /** How long a finished job's status and MIDI stay retrievable, counted from when it ended. Absent on older servers. */
    @SerializedName("result_retention_seconds") val resultRetentionSeconds: Long? = null,
    @SerializedName("max_video_length_minutes") val maxVideoLengthMinutes: Float? = null,
    /** Absent on servers that cannot transcribe uploaded files. */
    @SerializedName("max_upload_size_mb") val maxUploadSizeMb: Float? = null,
    /** Whether the server makes piano arrangements; absent (false) on servers that can't. */
    @SerializedName("arrangement") val arrangement: Boolean? = null,
    @SerializedName("free_minutes") val freeMinutes: Int? = null,
    @SerializedName("free_minutes_period") val freeMinutesPeriod: String? = null,
    /** Subscription plans, lowest level first; absent on older servers and when billing is off. */
    @SerializedName("subscriptions") val subscriptions: List<ServerSubscriptionPlan>? = null,
    /** Minutes given once to each new account. */
    @SerializedName("welcome_minutes") val welcomeMinutes: Int? = null,
    @SerializedName("google_oauth_client_ids", alternate = ["google_client_ids", "oauth_client_ids", "client_ids", "google_oauth_clients"]) val googleClientIds: List<String>? = null,
    /** A planned restart or shutdown; null when nothing is planned or on older servers. */
    @SerializedName("maintenance") val maintenance: MaintenanceNotice? = null,
)

data class UserBalanceResponse(
    @SerializedName("user_id") val userId: String,
    /** Can be negative: a refunded purchase whose minutes were already used (see [com.lumenchord.pianoweave.billing.owedMinutes]). */
    @SerializedName("minutes") val minutes: Int,
    /**
     * Part of [minutes] renewed every free-minute period and spent first; unused ones expire at renewal.
     * Null on servers from before monthly minutes, where [minutes] is a single balance.
     */
    @SerializedName("monthly_minutes") val monthlyMinutes: Int? = null,
    /** Bought or given by hand, never expire; negative after a refund of used minutes. Null on older servers. */
    @SerializedName("paid_minutes") val paidMinutes: Int? = null,
    @SerializedName("free_minutes_seconds_until_next_grant") val freeMinutesSecondsUntilNextGrant: Long? = null,
    @SerializedName("free_minutes_next_grant_at") val freeMinutesNextGrantAt: Long? = null,
    /**
     * True for an account the server's administrator made a developer's: its jobs are never charged, so the app
     * shows unlimited credits and the developer extras. Null (false) on older servers.
     */
    @SerializedName("developer") val developer: Boolean? = null,
    /** The account's running subscription; null without one and on older servers. */
    @SerializedName("subscription") val subscription: ActiveSubscription? = null,
    /** True while an unlimited plan runs: jobs aren't charged. */
    @SerializedName("unlimited") val unlimited: Boolean? = null,
    /**
     * An unlimited account that started a lot of transcription in the last 24 hours: its jobs run one at a time for
     * now (never refused). Older servers sent a daily cap instead (`unlimited_minutes_left`), which is ignored.
     */
    @SerializedName("unlimited_heavy_use") val unlimitedHeavyUse: Boolean? = null
)

/** A subscription as the server knows it. While its period runs, its minutes are the balance's monthly_minutes. */
data class ActiveSubscription(
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("name") val name: String? = null,
    /** Null for an unlimited plan. */
    @SerializedName("minutes_per_period") val minutesPerPeriod: Int? = null,
    @SerializedName("level") val level: Int? = null,
    /** Epoch seconds: renewal, or the end of a canceled plan. */
    @SerializedName("period_ends_at") val periodEndsAt: Long? = null,
    @SerializedName("auto_renewing") val autoRenewing: Boolean? = null,
    /** "ACTIVE", "CANCELED" (ends at [periodEndsAt]) or "IN_GRACE_PERIOD" (a payment failed and is retried). */
    @SerializedName("state") val state: String? = null
)

data class BillingCostResponse(
    @SerializedName("duration_seconds") val durationSeconds: Float,
    @SerializedName("cost_minutes") val costMinutes: Int
)

data class VerifyPurchaseRequest(
    @SerializedName("product_id") val productId: String,
    @SerializedName("purchase_token") val purchaseToken: String
)

data class VerifySubscriptionResponse(
    @SerializedName("user_id") val userId: String? = null,
    /** "started" when a paid period began with this call; "recorded" when it was known, pending or ended. */
    @SerializedName("status") val status: String? = null,
    @SerializedName("granted_minutes") val grantedMinutes: Int? = null,
    @SerializedName("acknowledged") val acknowledged: Boolean? = null,
    @SerializedName("balance") val balance: UserBalanceResponse? = null
)

data class VerifyPurchaseResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("credited_minutes") val creditedMinutes: Int,
    @SerializedName("minutes") val minutes: Int,
    @SerializedName("consumed") val consumed: Boolean
)

private const val UPLOAD_KEY_PREFIX = "upload://"

/**
 * Library key of a song transcribed from an uploaded file. Links key songs by their URL; an uploaded
 * file has none, so each upload job gets its own key.
 */
fun uploadSourceKey(jobId: String): String = UPLOAD_KEY_PREFIX + jobId

val String.isUploadSourceKey: Boolean
    get() = startsWith(UPLOAD_KEY_PREFIX)
