package com.lumenchord.pianoweave.api

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import okhttp3.Headers
import retrofit2.HttpException
import java.io.IOException

/** Reading the transcription server's refusals: what they say, and which ones to wait out. */
object ServerErrors {

    /** A video site refused the server or didn't answer in time; Retry-After says when links work again. */
    const val SOURCE_TEMPORARILY_UNAVAILABLE = "source_temporarily_unavailable"
    /** A restart or a stopped worker interrupted the job; its minutes were given back. */
    const val TRANSCRIPTION_INTERRUPTED = "transcription_interrupted"

    /** Codes about the link itself, for which uploading the file instead may work. */
    private val UPLOAD_MAY_WORK = setOf(
        SOURCE_TEMPORARILY_UNAVAILABLE, "source_video_unavailable", "source_unsupported", "source_failed"
    )

    /** True if [code] is about the link, so the file itself can be uploaded instead. */
    fun uploadMayWork(code: String?): Boolean = code in UPLOAD_MAY_WORK

    /** Tries a busy server this many times in all before the refusal is shown. */
    const val BUSY_ATTEMPTS = 4

    /**
     * Sends a submission this many times in all while the server is busy or still checking the link or
     * receiving the file from an earlier try with the same key (about a minute at its usual 5 s).
     */
    const val SUBMIT_ATTEMPTS = 12
    private const val MAX_BUSY_WAIT_SEC = 30L

    /** The error body's text. Peeked, so it can be read again (Retrofit buffers error bodies). */
    fun errorBody(e: HttpException): String =
        try {
            e.response()?.errorBody()?.source()?.peek()?.readUtf8().orEmpty()
        } catch (_: Exception) {
            ""
        }

    /** FastAPI's `"detail"`, if [body] is JSON with one. */
    fun detail(body: String): JsonElement? =
        try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject?.get("detail")
        } catch (_: Exception) {
            null
        }

    /** The user-readable `"detail"`: plain text, or the `"message"` of a detail object. */
    fun detailMessage(body: String): String? {
        val detail = detail(body) ?: return null
        val text = when {
            detail.isJsonPrimitive -> detail.asString
            detail.isJsonObject -> detail.asJsonObject.get("message")?.takeIf { it.isJsonPrimitive }?.asString
            else -> null
        }
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** The `"error_code"` of a detail object, which the app acts on rather than on the message. */
    fun errorCode(body: String): String? {
        val detail = detail(body)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return detail.get("error_code")?.takeIf { it.isJsonPrimitive }?.asString
    }

    fun errorCode(e: Throwable): String? = (e as? HttpException)?.let { errorCode(errorBody(it)) }

    fun retryAfterSeconds(headers: Headers?): Long? =
        headers?.get("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    /** 429: the user already has as many transcriptions in progress as the server allows. */
    fun isTooManyRequests(e: Throwable): Boolean = (e as? HttpException)?.code() == 429

    /**
     * 404 from the API itself (with a `"detail"`): the job or its MIDI is gone from the server for good, e.g.
     * expired. Not a proxy's 404 while the server restarts, which has no detail; that one is waited out.
     */
    fun isGoneFromServer(e: Throwable): Boolean {
        val http = e as? HttpException ?: return false
        return http.code() == 404 && detailMessage(errorBody(http)) != null
    }

    /** 402: the balance doesn't cover the transcription. */
    fun isPaymentRequired(e: Throwable): Boolean = (e as? HttpException)?.code() == 402

    /**
     * How long to wait before sending a submission again with the same key: as [busyRetryDelayMs], or a 409
     * with Retry-After, meaning an earlier try with that key is still checking the link or uploading.
     */
    fun submitRetryDelayMs(e: Throwable): Long? {
        val http = e as? HttpException ?: return null
        if (http.code() != 409) return busyRetryDelayMs(e)
        val seconds = retryAfterSeconds(http.response()?.headers()) ?: return null
        return seconds.coerceIn(1, MAX_BUSY_WAIT_SEC) * 1000
    }

    /** 409 without Retry-After: this key's job was transcribed and has since expired on the server. */
    fun isAlreadyProcessed(e: Throwable): Boolean {
        val http = e as? HttpException ?: return false
        return http.code() == 409 && retryAfterSeconds(http.response()?.headers()) == null
    }

    /**
     * True if the server gave a submission its final answer, so its key is done with: a refusal it reached
     * a decision on. Not after a lost connection, a timeout, a 409 still in progress or a 5xx, after which
     * the job may exist and the same key finds it.
     */
    fun isSubmissionSettled(e: Throwable): Boolean {
        val code = (e as? HttpException)?.code() ?: return false
        return code in 400..499 && code != 408 && (code != 409 || isAlreadyProcessed(e))
    }

    /**
     * How long to wait before asking again, when the server is only busy (503 with Retry-After, e.g. while
     * it checks too many videos at once). Null for any other error, including a refusal because of a
     * planned restart or shutdown, which carries a maintenance notice and is shown instead.
     */
    fun busyRetryDelayMs(e: Throwable): Long? {
        val http = e as? HttpException ?: return null
        if (http.code() != 503) return null
        val seconds = retryAfterSeconds(http.response()?.headers()) ?: return null
        val body = errorBody(http)
        val detail = detail(body)
        if (detail != null && detail.isJsonObject && detail.asJsonObject.has("maintenance")) return null
        // Video links are off for a while (up to a day): asking again before then only waits in vain
        if (errorCode(body) == SOURCE_TEMPORARILY_UNAVAILABLE) return null
        return seconds.coerceIn(1, MAX_BUSY_WAIT_SEC) * 1000
    }

    /**
     * True if an upload broke off mid-transfer. The server refuses a balance under one minute before it reads
     * the body, and OkHttp often reports that as a broken connection instead of the 402.
     */
    fun isBrokenUpload(e: Throwable): Boolean {
        if (e !is IOException) return false
        val message = generateSequence(e as Throwable) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .joinToString(" ")
        return BROKEN_UPLOAD_MESSAGES.any { it in message }
    }

    private val BROKEN_UPLOAD_MESSAGES = listOf(
        "broken pipe", "unexpected end of stream", "connection reset", "stream was reset", "software caused connection abort"
    )

    /**
     * True if a refusal may be about the Google token, so a fresh one is worth getting. A 401 asking for the
     * server password is not (the token is fine), nor is a busy server that said when to come back.
     */
    fun canRefreshToken(code: Int, headers: Headers?): Boolean = when (code) {
        401 -> !PianoApiFactory.isServerPasswordRequired(code, headers)
        503 -> retryAfterSeconds(headers) == null
        else -> false
    }
}

/**
 * Runs [call], and while the server answers that it is busy, waits as long as it asked and tries again,
 * up to [ServerErrors.BUSY_ATTEMPTS] times in all. Any other error, or the last busy one, is thrown.
 */
suspend fun <T> retryWhenBusy(
    attempts: Int = ServerErrors.BUSY_ATTEMPTS,
    wait: suspend (Long) -> Unit = { delay(it) },
    retryDelayMs: (HttpException) -> Long? = ServerErrors::busyRetryDelayMs,
    call: suspend () -> T
): T {
    var attempt = 1
    while (true) {
        try {
            return call()
        } catch (e: HttpException) {
            val delayMs = retryDelayMs(e)
            if (delayMs == null || attempt >= attempts) throw e
            attempt++
            wait(delayMs)
        }
    }
}
