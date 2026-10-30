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

    @Test
    fun expiredJobOrResultIsGoneFromTheServer() {
        assertTrue(ServerErrors.isGoneFromServer(httpError(404, """{"detail": "Transcription job not found"}""")))
        assertTrue(ServerErrors.isGoneFromServer(httpError(404, """{"detail": "MIDI result is no longer available"}""")))
        // A proxy's page while the server restarts is waited out
        assertFalse(ServerErrors.isGoneFromServer(httpError(404, "<html>Not Found</html>")))
        assertFalse(ServerErrors.isGoneFromServer(httpError(409, """{"detail": "Transcription is not completed yet"}""")))
        assertFalse(ServerErrors.isGoneFromServer(IOException("Connection reset")))
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

    // --- Error codes ---

    private val linksOff = """{"detail": {"message": "Video links cannot be read at the moment; try again later, or upload the file instead", "error_code": "source_temporarily_unavailable"}}"""

    @Test
    fun errorCodeIsReadFromTheDetailObject() {
        assertEquals("source_temporarily_unavailable", ServerErrors.errorCode(httpError(503, linksOff, "Retry-After", "1500")))
        assertEquals(
            "Video links cannot be read at the moment; try again later, or upload the file instead",
            ServerErrors.detailMessage(linksOff)
        )
        // A plain string detail, and FastAPI's own list, have no code
        assertNull(ServerErrors.errorCode(httpError(503, """{"detail": "Server is restarting"}""")))
        assertNull(ServerErrors.errorCode(httpError(422, """{"detail": [{"loc": ["body"], "msg": "x"}]}""")))
    }

    @Test
    fun linksOffIsNotRetriedButABusyCheckIs() {
        // Retry-After can be up to a day here; waiting it out in the app would only hang the submit
        assertNull(ServerErrors.busyRetryDelayMs(httpError(503, linksOff, "Retry-After", "30")))
        assertNull(ServerErrors.submitRetryDelayMs(httpError(503, linksOff, "Retry-After", "30")))
        val busy = """{"detail": {"message": "Too many videos are being checked; try again in a moment", "error_code": "source_busy"}}"""
        assertEquals(5000L, ServerErrors.busyRetryDelayMs(httpError(503, busy, "Retry-After", "5")))
    }

    @Test
    fun uploadIsOfferedOnlyForLinkProblems() {
        assertTrue(ServerErrors.uploadMayWork("source_temporarily_unavailable"))
        assertTrue(ServerErrors.uploadMayWork("source_video_unavailable"))
        assertTrue(ServerErrors.uploadMayWork("source_unsupported"))
        assertTrue(ServerErrors.uploadMayWork("source_failed"))
        assertFalse(ServerErrors.uploadMayWork("source_busy"))
        assertFalse(ServerErrors.uploadMayWork("insufficient_minutes"))
        assertFalse(ServerErrors.uploadMayWork("transcription_failed"))
        assertFalse(ServerErrors.uploadMayWork(null))
    }

    @Test
    fun interruptedJobSaysItCanBeSentAgain() {
        val job = TranscriptionStatusResponse(
            job_id = "j", status = "failed", progress = 0.4f,
            error = "Transcription interrupted because its worker stopped", errorCode = "transcription_interrupted"
        )
        assertEquals(
            "Transcription interrupted because its worker stopped. Its minutes were given back; tap Transcribe to try again.",
            job.failureMessage
        )
        assertEquals("Converting failed", job.copy(error = "Converting failed", errorCode = "transcription_failed").failureMessage)
    }

    // --- Retrying a submission with its Idempotency-Key ---

    @Test
    fun conflictWithRetryAfterIsWaitedOut() {
        // The first try with this key is still checking the link or receiving the file
        assertEquals(5000L, ServerErrors.submitRetryDelayMs(httpError(409, "", "Retry-After", "5")))
        assertFalse(ServerErrors.isAlreadyProcessed(httpError(409, "", "Retry-After", "5")))
        assertFalse(ServerErrors.isSubmissionSettled(httpError(409, "", "Retry-After", "5")))
        // A busy server is waited out as before
        assertEquals(5000L, ServerErrors.submitRetryDelayMs(httpError(503, "", "Retry-After", "5")))
    }

    @Test
    fun conflictWithoutRetryAfterMeansAlreadyProcessed() {
        val expired = httpError(409, """{"detail": "already processed"}""")
        assertNull(ServerErrors.submitRetryDelayMs(expired))
        assertTrue(ServerErrors.isAlreadyProcessed(expired))
        assertTrue(ServerErrors.isSubmissionSettled(expired))
    }

    @Test
    fun keyIsKeptWhileTheJobMayExist() {
        // No answer, a timeout or a server error: the job may have been queued, so the same key must find it
        assertFalse(ServerErrors.isSubmissionSettled(IOException("unexpected end of stream")))
        assertFalse(ServerErrors.isSubmissionSettled(httpError(408)))
        assertFalse(ServerErrors.isSubmissionSettled(httpError(503)))
        assertFalse(ServerErrors.isSubmissionSettled(httpError(504)))
        // A refusal: nothing was queued, the next submission is a new one
        assertTrue(ServerErrors.isSubmissionSettled(httpError(402)))
        assertTrue(ServerErrors.isSubmissionSettled(httpError(422)))
        assertTrue(ServerErrors.isSubmissionSettled(httpError(429)))
    }

    @Test
    fun submissionRetriesWithTheSameCallUntilTheConflictClears() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = retryWhenBusy(
            attempts = ServerErrors.SUBMIT_ATTEMPTS,
            wait = { waits += it },
            retryDelayMs = ServerErrors::submitRetryDelayMs
        ) {
            calls++
            if (calls < 3) throw httpError(409, "", "Retry-After", "5")
            "job-1"
        }
        assertEquals("job-1", result)
        assertEquals(listOf(5000L, 5000L), waits)
    }
}
