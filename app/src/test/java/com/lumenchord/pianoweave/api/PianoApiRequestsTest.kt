package com.lumenchord.pianoweave.api

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** What the app sends and reads, against a fake server that answers every request with one canned response. */
class PianoApiRequestsTest {

    private var lastRequest: Request? = null

    private fun api(code: Int, body: String, vararg headers: Pair<String, String>): PianoApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                lastRequest = chain.request()
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Answer")
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("http://server.test/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PianoApi::class.java)
    }

    // --- Idempotency-Key ---

    @Test
    fun linkSubmitSendsTheIdempotencyKey() = runBlocking {
        api(202, """{"job_id": "job-1", "status": "queued"}""")
            .createTranscription(CreateTranscriptionRequest("https://example.com/v/1"), idempotencyKey = "key-1")
        assertEquals("key-1", lastRequest!!.header("Idempotency-Key"))
    }

    @Test
    fun uploadSendsTheIdempotencyKey() = runBlocking {
        api(202, """{"job_id": "job-1", "status": "queued"}""")
            .uploadTranscription("song.mp3", "abc".toRequestBody(), idempotencyKey = "key-2")
        assertEquals("key-2", lastRequest!!.header("Idempotency-Key"))
    }

    @Test
    fun withoutAKeyNoHeaderIsSent() = runBlocking {
        api(202, """{"job_id": "job-1", "status": "queued"}""")
            .createTranscription(CreateTranscriptionRequest("https://example.com/v/1"))
        assertNull(lastRequest!!.header("Idempotency-Key"))
    }

    // --- 1. Billing cost needs the Google sign-in ---

    @Test
    fun billingCostForAFileSendsTheGoogleToken() = runBlocking {
        val cost = api(200, """{"duration_seconds": 125.0, "cost_minutes": 3}""")
            .getBillingCost(durationSeconds = 125f, authHeader = "Bearer token-1")
        assertEquals(3, cost.costMinutes)
        val request = lastRequest!!
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("125.0", request.url.queryParameter("duration_seconds"))
        assertNull(request.url.queryParameter("source_url"))
    }

    @Test
    fun billingCostForALinkSendsTheGoogleToken() = runBlocking {
        api(200, """{"duration_seconds": 60.0, "cost_minutes": 1}""")
            .getBillingCost(sourceUrl = "https://example.com/v/1", authHeader = "Bearer token-1")
        assertEquals("Bearer token-1", lastRequest!!.header("Authorization"))
        assertEquals("https://example.com/v/1", lastRequest!!.url.queryParameter("source_url"))
    }

    @Test
    fun billingCostWithoutSignInIsRefused() = runBlocking {
        try {
            api(401, """{"detail": "Sign in with Google"}""").getBillingCost(durationSeconds = 10f)
            fail("expected 401")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
        assertNull(lastRequest!!.header("Authorization"))
    }

    // --- 2. Too many transcriptions in progress ---

    @Test
    fun uploadRefusedWith429CarriesTheMessage() = runBlocking {
        val api = api(
            429, """{"detail": "You can have at most 5 transcriptions in progress; wait for one to finish"}""",
            "Retry-After" to "60"
        )
        try {
            api.uploadTranscription("song.mp3", "audio".toRequestBody(), authHeader = "Bearer t")
            fail("expected 429")
        } catch (e: HttpException) {
            assertTrue(ServerErrors.isTooManyRequests(e))
            assertEquals(
                "You can have at most 5 transcriptions in progress; wait for one to finish",
                ServerErrors.detailMessage(ServerErrors.errorBody(e))
            )
        }
    }

    // --- 5. Busy while checking videos ---

    @Test
    fun linkSubmitRefusedAsBusyIsRetryable() = runBlocking {
        val api = api(503, """{"detail": "Checking too many videos"}""", "Retry-After" to "5")
        try {
            api.createTranscription(CreateTranscriptionRequest("https://example.com/v/1"))
            fail("expected 503")
        } catch (e: HttpException) {
            assertEquals(5_000L, ServerErrors.busyRetryDelayMs(e))
        }
    }

    // --- 3 and 7. Balances, which can be negative; the submit response's is final ---

    @Test
    fun submitResponseCarriesTheFinalBalance() = runBlocking {
        val response = api(200, """{"job_id": "j1", "status": "queued", "minutes": 7}""")
            .uploadTranscription("song.mp3", "audio".toRequestBody(), authHeader = "Bearer t")
        assertEquals("j1", response.job_id)
        assertEquals(7, response.minutes)
    }

    @Test
    fun negativeBalancesAreRead() = runBlocking {
        val balance = api(200, """{"user_id": "u", "minutes": -4}""").getUserBalance(authHeader = "Bearer t")
        assertEquals(-4, balance.minutes)
        val submitted = Gson().fromJson("""{"job_id": "j", "status": "queued", "minutes": -2}""", CreateTranscriptionResponse::class.java)
        assertEquals(-2, submitted.minutes)
        val job = Gson().fromJson(
            """{"job_id": "j", "status": "running", "progress": 0.5, "error": null, "minutes": -1}""",
            TranscriptionStatusResponse::class.java
        )
        assertEquals(-1, job.minutes)
    }

    @Test
    fun unbilledSubmitResponseHasNoBalance() {
        val response = Gson().fromJson("""{"job_id": "j", "status": "queued"}""", CreateTranscriptionResponse::class.java)
        assertNull(response.minutes)
    }

    // --- 6. A failed job's error is written for users ---

    @Test
    fun failedJobErrorIsShownAsItIs() {
        val job = Gson().fromJson(
            """{"job_id": "j", "status": "failed", "progress": 0, "error": "The video could not be downloaded: Video unavailable. This video is private"}""",
            TranscriptionStatusResponse::class.java
        )
        assertEquals("The video could not be downloaded: Video unavailable. This video is private", job.failureMessage)
    }

    @Test
    fun failedJobWithoutAnErrorStillSaysSomething() {
        val gson = Gson()
        for (error in listOf("null", "\"\"", "\"  \"")) {
            val job = gson.fromJson(
                """{"job_id": "j", "status": "failed", "progress": 0, "error": $error}""",
                TranscriptionStatusResponse::class.java
            )
            assertEquals("The server could not transcribe this.", job.failureMessage)
        }
    }
}
