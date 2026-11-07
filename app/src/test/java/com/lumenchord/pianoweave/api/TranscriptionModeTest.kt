package com.lumenchord.pianoweave.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Piano arrangements: what is sent, how their songs are keyed in the library, and the server's refusals. */
class TranscriptionModeTest {

    private val link = "https://www.youtube.com/watch?v=abc"

    // --- Library keys ---

    @Test
    fun aTranscriptionKeepsItsSourceAsKey() {
        assertEquals(link, libraryKey(link, TranscriptionMode.Transcription))
        assertFalse(link.isArrangementKey)
        assertEquals(TranscriptionMode.Transcription, link.libraryKeyMode)
    }

    @Test
    fun anArrangementIsKeptApartFromTheTranscriptionOfTheSameLink() {
        val key = libraryKey(link, TranscriptionMode.Arrangement)
        assertTrue(key != link)
        assertTrue(key.isArrangementKey)
        assertEquals(TranscriptionMode.Arrangement, key.libraryKeyMode)
        assertEquals(link, key.sourceOfLibraryKey)
        // Still a link to the same video, so opening the source works as before
        assertTrue(key.startsWith(link + "#"))
    }

    @Test
    fun anArrangementKeyIsMarkedOnce() {
        val key = libraryKey(link, TranscriptionMode.Arrangement)
        assertEquals(key, libraryKey(key, TranscriptionMode.Arrangement))
    }

    @Test
    fun anUploadArrangementStaysAnUploadKey() {
        val key = libraryKey(uploadSourceKey("job-7"), TranscriptionMode.Arrangement)
        assertTrue(key.isUploadSourceKey)
        assertEquals(uploadSourceKey("job-7"), key.sourceOfLibraryKey)
    }

    // --- Requests ---

    @Test
    fun anArrangementRequestCarriesModeAndStyle() {
        val mode = TranscriptionMode.Arrangement
        val json = JsonParser.parseString(Gson().toJson(CreateTranscriptionRequest(link, mode.apiValue, mode.style))).asJsonObject
        assertEquals("arrangement", json["mode"].asString)
        assertEquals("level2", json["style"].asString)
    }

    @Test
    fun aTranscriptionRequestIsTheOneServersWithoutArrangementsKnow() {
        val json = JsonParser.parseString(Gson().toJson(CreateTranscriptionRequest(link))).asJsonObject
        assertEquals(setOf("source_url"), json.keySet())
        // Also: the server refuses a style on a transcription (422)
        assertEquals(null, TranscriptionMode.Transcription.style)
    }

    @Test
    fun serverInfoTellsWhetherArrangementsAreOn() {
        assertEquals(true, Gson().fromJson("""{"arrangement": true}""", ServerInfoResponse::class.java).arrangement)
        assertEquals(null, Gson().fromJson("""{}""", ServerInfoResponse::class.java).arrangement)
    }

    @Test
    fun unknownModesAreTranscriptions() {
        assertEquals(TranscriptionMode.Transcription, TranscriptionMode.fromApiValue(null))
        assertEquals(TranscriptionMode.Transcription, TranscriptionMode.fromApiValue("karaoke"))
        assertEquals(TranscriptionMode.Arrangement, TranscriptionMode.fromApiValue("arrangement"))
    }

    // --- Refusals ---

    private fun refusal(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    private val arrangementsOff = refusal(
        409,
        """{"detail": {"message": "Piano arrangements are off on this server", "error_code": "arrangement_unavailable"}}"""
    )

    @Test
    fun arrangementsOffIsNotAnExpiredJob() {
        // Both are 409 without Retry-After; only the code tells them apart
        assertFalse(ServerErrors.isAlreadyProcessed(arrangementsOff))
        assertEquals(ServerErrors.ARRANGEMENT_UNAVAILABLE, ServerErrors.errorCode(arrangementsOff))
    }

    @Test
    fun arrangementsUnavailableSettlesTheSubmission() {
        assertTrue(ServerErrors.isSubmissionSettled(arrangementsOff))
        val noRoom = refusal(503, """{"detail": {"message": "No worker has room", "error_code": "arrangement_unavailable"}}""")
        assertTrue(ServerErrors.isSubmissionSettled(noRoom))
    }

    @Test
    fun aFailedArrangementSaysSo() {
        val job = TranscriptionStatusResponse("job-1", "failed", 0f, error = null, mode = "arrangement")
        assertEquals("The server could not arrange this.", job.failureMessage)
    }
}
