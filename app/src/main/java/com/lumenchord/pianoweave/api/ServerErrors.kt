package com.lumenchord.pianoweave.api

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import okhttp3.Headers
import retrofit2.HttpException
import java.io.IOException

/** Reading the transcription server's refusals: what they say, and which ones to wait out. */
object ServerErrors {

    /** Tries a busy server this many times in all before the refusal is shown. */
    const val BUSY_ATTEMPTS = 4
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

    fun retryAfterSeconds(headers: Headers?): Long? =
        headers?.get("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    /** 429: the user already has as many transcriptions in progress as the server allows. */
    fun isTooManyRequests(e: Throwable): Boolean = (e as? HttpException)?.code() == 429

    /** 402: the balance doesn't cover the transcription. */
    fun isPaymentRequired(e: Throwable): Boolean = (e as? HttpException)?.code() == 402

    /**
     * How long to wait before asking again, when the server is only busy (503 with Retry-After, e.g. while
     * it checks too many videos at once). Null for any other error, including a refusal because of a
     * planned restart or shutdown, which carries a maintenance notice and is shown instead.
     */
    fun busyRetryDelayMs(e: Throwable): Long? {
        val http = e as? HttpException ?: return null
        if (http.code() != 503) return null
        val seconds = retryAfterSeconds(http.response()?.headers()) ?: return null
        val detail = detail(errorBody(http))
        if (detail != null && detail.isJsonObject && detail.asJsonObject.has("maintenance")) return null
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
    call: suspend () -> T
): T {
    var attempt = 1
    while (true) {
        try {
            return call()
        } catch (e: HttpException) {
            val delayMs = ServerErrors.busyRetryDelayMs(e)
            if (delayMs == null || attempt >= attempts) throw e
            attempt++
            wait(delayMs)
        }
    }
}
