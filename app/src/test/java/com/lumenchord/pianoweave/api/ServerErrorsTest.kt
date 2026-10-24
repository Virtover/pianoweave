package com.lumenchord.pianoweave.api

import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketException

/** An HTTP error as Retrofit throws it, with [headers] given as name, value, name, value... */
internal fun httpError(code: Int, body: String = "", vararg headers: String): HttpException {
    val raw = okhttp3.Response.Builder()
        .code(code)
        .message("Error")
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://localhost/").build())
        .headers(headersOf(*headers))
        .build()
    return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
}

class ServerErrorsTest {

    private val busy = """{"detail": "The server is checking too many videos right now; try again in a moment"}"""
    private val maintenance = """{"detail": {"message": "Restarting soon", "maintenance": {"kind": "restart", "starts_at": 1}}}"""

    // --- 429 Too Many Requests ---

    @Test
    fun tooManyRequestsShowsTheServersMessage() {
        val e = httpError(
            429, """{"detail": "You can have at most 5 transcriptions in progress; wait for one to finish"}""",
            "Retry-After", "60"
        )
        assertTrue(ServerErrors.isTooManyRequests(e))
        assertEquals(
            "You can have at most 5 transcriptions in progress; wait for one to finish",
            ServerErrors.detailMessage(ServerErrors.errorBody(e))
        )
        assertEquals(60L, ServerErrors.retryAfterSeconds(e.response()?.headers()))
    }

    @Test
    fun tooManyRequestsIsNeverRetried() = runBlocking {
        var calls = 0
        try {
            retryWhenBusy(wait = { fail("must not wait") }) {
                calls++
                throw httpError(429, """{"detail": "wait"}""", "Retry-After", "60")
            }
        } catch (e: HttpException) {
            assertEquals(429, e.code())
        }
        assertEquals(1, calls)
    }

    @Test
    fun errorBodyCanBeReadMoreThanOnce() {
        val e = httpError(429, """{"detail": "wait"}""")
        assertEquals(ServerErrors.errorBody(e), ServerErrors.errorBody(e))
        assertEquals("""{"detail": "wait"}""", e.response()?.errorBody()?.string())
    }

    @Test
    fun detailMessageReadsTextAndObjectDetails() {
        assertEquals("Plain", ServerErrors.detailMessage("""{"detail": " Plain "}"""))
        assertEquals("Restarting soon", ServerErrors.detailMessage(maintenance))
        assertNull(ServerErrors.detailMessage("""{"detail": ""}"""))
        assertNull(ServerErrors.detailMessage("<html>Bad gateway</html>"))
        assertNull(ServerErrors.detailMessage(""))
    }

    // --- 503 busy, with Retry-After ---

    @Test
    fun busyServerIsWaitedOutForItsRetryAfter() {
        assertEquals(5_000L, ServerErrors.busyRetryDelayMs(httpError(503, busy, "Retry-After", "5")))
    }

    @Test
    fun otherRefusalsAreNotWaitedOut() {
        assertNull(ServerErrors.busyRetryDelayMs(httpError(503, busy)))
        assertNull(ServerErrors.busyRetryDelayMs(httpError(503, maintenance, "Retry-After", "120")))
        assertNull(ServerErrors.busyRetryDelayMs(httpError(429, busy, "Retry-After", "60")))
        assertNull(ServerErrors.busyRetryDelayMs(IOException("offline")))
    }

    @Test
    fun busyWaitIsKeptShort() {
        assertEquals(30_000L, ServerErrors.busyRetryDelayMs(httpError(503, busy, "Retry-After", "3600")))
        assertEquals(1_000L, ServerErrors.busyRetryDelayMs(httpError(503, busy, "Retry-After", "0")))
    }

    @Test
    fun busyServerIsAskedAgainQuietly() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        val result = retryWhenBusy(wait = { waits += it }) {
            if (++calls < 3) throw httpError(503, busy, "Retry-After", "5")
            "job-1"
        }
        assertEquals("job-1", result)
        assertEquals(listOf(5_000L, 5_000L), waits)
    }

    @Test
    fun busyRetryStopsAfterItsAttempts() = runBlocking {
        var calls = 0
        val busyError = httpError(503, busy, "Retry-After", "5")
        try {
            retryWhenBusy(attempts = 3, wait = {}) {
                calls++
                throw busyError
            }
            fail("expected the busy refusal")
        } catch (e: HttpException) {
            assertSame(busyError, e)
        }
        assertEquals(3, calls)
    }

    // --- Upload refused before it was read ---

    @Test
    fun brokenUploadConnectionIsRecognized() {
        assertTrue(ServerErrors.isBrokenUpload(SocketException("Broken pipe")))
        assertTrue(ServerErrors.isBrokenUpload(IOException("unexpected end of stream on http://localhost/...")))
        assertTrue(ServerErrors.isBrokenUpload(IOException("upload failed", SocketException("Connection reset by peer"))))
    }

    @Test
    fun otherFailuresAreNotABrokenUpload() {
        assertFalse(ServerErrors.isBrokenUpload(IOException("Upload cancelled")))
        assertFalse(ServerErrors.isBrokenUpload(java.net.UnknownHostException("Unable to resolve host")))
        assertFalse(ServerErrors.isBrokenUpload(IllegalStateException("broken pipe")))
        assertFalse(ServerErrors.isBrokenUpload(httpError(402)))
    }

    @Test
    fun paymentRequiredIsRecognized() {
        assertTrue(ServerErrors.isPaymentRequired(httpError(402, """{"detail": "Not enough minutes"}""")))
        assertFalse(ServerErrors.isPaymentRequired(httpError(401)))
    }

    // --- Server password vs. Google token ---

    @Test
    fun passwordRefusalDoesNotRefreshTheGoogleToken() {
        assertFalse(ServerErrors.canRefreshToken(401, headersOf("WWW-Authenticate", "Basic realm=\"x\"")))
        assertTrue(ServerErrors.canRefreshToken(401, headersOf()))
    }

    @Test
    fun busyServerDoesNotRefreshTheGoogleToken() {
        assertFalse(ServerErrors.canRefreshToken(503, headersOf("Retry-After", "5")))
        assertTrue(ServerErrors.canRefreshToken(503, headersOf()))
        assertFalse(ServerErrors.canRefreshToken(429, headersOf()))
        assertFalse(ServerErrors.canRefreshToken(402, headersOf()))
    }
}
