package com.lumenchord.pianoweave.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import kotlin.math.floor

/**
 * A planned server restart or shutdown, as announced by the server (`maintenance` in server-info and
 * job status, `X-Server-Maintenance` headers on every response). [at] and [backAt] are Unix seconds.
 */
data class MaintenanceNotice(
    @SerializedName("action") val action: String?,
    @SerializedName("at") val at: Long,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("back_at") val backAt: Long? = null
) {
    val isShutdown: Boolean get() = action == SHUTDOWN
    val isValid: Boolean get() = (action == RESTART || action == SHUTDOWN) && at > 0

    /** True once the announced time has passed: the server is restarting or offline. */
    fun hasStarted(nowSec: Long): Boolean = nowSec >= at

    companion object {
        const val RESTART = "restart"
        const val SHUTDOWN = "shutdown"
    }
}

/**
 * The selected server's pending restart or shutdown, observable from Compose. Fed by every response
 * from that server (see [PianoApiFactory]) and by the full notices in response bodies.
 */
object ServerMaintenance {

    private const val HEADER_ACTION = "X-Server-Maintenance"
    private const val HEADER_AT = "X-Server-Maintenance-At"

    var notice by mutableStateOf<MaintenanceNotice?>(null)
        private set

    @Volatile private var watchedServer: HttpUrl? = null

    /** Follows [serverUrl] from now on; a notice from a previously selected server is dropped. */
    fun watch(serverUrl: String) {
        val url = withTrailingSlash(serverUrl).toHttpUrlOrNull()
        if (url != watchedServer) {
            watchedServer = url
            notice = null
        }
    }

    /** Called for every response of the transcription-server client. */
    fun onResponse(response: Response) {
        if (!isWatched(response.request.url)) return
        val fromHeaders = headerNotice(response.header(HEADER_ACTION), response.header(HEADER_AT))
        notice = nextNotice(notice, fromHeaders, response.code)
    }

    /** A full notice from a response body; adds what the headers lack (reason, return time). */
    fun onNotice(serverUrl: String, fromBody: MaintenanceNotice?) {
        val url = withTrailingSlash(serverUrl).toHttpUrlOrNull() ?: return
        if (fromBody == null || !fromBody.isValid || !isWatched(url)) return
        notice = fromBody
    }

    private fun isWatched(url: HttpUrl): Boolean {
        val watched = watchedServer ?: return false
        return url.scheme == watched.scheme && url.host == watched.host && url.port == watched.port &&
            url.encodedPath.startsWith(watched.encodedPath)
    }

    internal fun headerNotice(action: String?, at: String?): MaintenanceNotice? =
        MaintenanceNotice(action?.trim()?.lowercase(), at?.trim()?.toLongOrNull() ?: 0L).takeIf { it.isValid }

    /**
     * The notice after a response. Headers announce or refresh it, keeping the body's details when it is
     * the same notice. Without headers, an answer from the API itself (< 500) means nothing is planned
     * any more; a 5xx without headers (a proxy while the server restarts, a starting API) keeps it.
     */
    internal fun nextNotice(current: MaintenanceNotice?, fromHeaders: MaintenanceNotice?, code: Int): MaintenanceNotice? = when {
        fromHeaders != null ->
            if (current != null && current.action == fromHeaders.action && current.at == fromHeaders.at) current
            else fromHeaders
        code < 500 -> null
        else -> current
    }

    private fun withTrailingSlash(url: String) = if (url.endsWith("/")) url else "$url/"
}

/** Texts for a [MaintenanceNotice]; [formatTime] turns Unix seconds into a local clock time. */
object MaintenanceTexts {

    fun title(notice: MaintenanceNotice, nowSec: Long, formatTime: (Long) -> String): String {
        if (notice.hasStarted(nowSec)) {
            return if (notice.isShutdown) {
                notice.backAt?.takeIf { it > nowSec }?.let { "Server offline until about ${formatTime(it)}" }
                    ?: "Server offline"
            } else {
                "Server is restarting"
            }
        }
        val countdown = countdown(notice.at - nowSec)
        return if (notice.isShutdown) "Server going offline in $countdown" else "Server restart in $countdown"
    }

    fun detail(notice: MaintenanceNotice, nowSec: Long, formatTime: (Long) -> String): String {
        val started = notice.hasStarted(nowSec)
        val text = when {
            !notice.isShutdown && !started ->
                "Transcriptions in progress continue after it. Long new ones may have to wait until it's back."
            !notice.isShutdown -> "It should be back in a few minutes. Your transcriptions continue then."
            !started -> buildString {
                append("Queued transcriptions continue when it's back")
                notice.backAt?.let { append(" (about ${formatTime(it)})") }
                append(".")
            }
            else -> "Queued transcriptions continue when it's back."
        }
        return notice.reason?.takeIf { it.isNotBlank() }?.let { "$text Reason: $it" } ?: text
    }

    /** Status line for a running transcription while the server cannot be reached. */
    fun waitingStatus(notice: MaintenanceNotice): String =
        if (notice.isShutdown) "Server is offline. Your transcription stays queued and continues when it's back..."
        else "Server is restarting. Your transcription continues when it's back..."

    /** Why a new transcription was refused because of [notice]; [retryAfterSec] is the server's Retry-After. */
    fun refusal(notice: MaintenanceNotice, nowSec: Long, retryAfterSec: Long?, formatTime: (Long) -> String): String {
        val what = if (notice.hasStarted(nowSec)) {
            if (notice.isShutdown) "The server is shutting down" else "The server is restarting"
        } else {
            val countdown = countdown(notice.at - nowSec)
            if (notice.isShutdown) "The server goes offline in $countdown" else "The server restarts in $countdown"
        }
        val retry = when {
            retryAfterSec != null && retryAfterSec > 0 ->
                if (retryAfterSec < 15 * 60) "Try again in about ${countdown(retryAfterSec)}."
                else "Try again after ${formatTime(nowSec + retryAfterSec)}."
            notice.backAt != null -> "Try again after ${formatTime(notice.backAt)}."
            else -> "Try again later."
        }
        return "$what, so new transcriptions are paused. $retry"
    }

    /** "45 s" under a minute, otherwise whole minutes rounded to the nearest, like the server. */
    internal fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s < 60) "$s s" else "${floor(s / 60.0 + 0.5).toLong()} min"
    }
}
