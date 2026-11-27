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

    @Test
    fun anEnsembleHasItsOwnKeyBesideThePianoAndTheArrangement() {
        val ensemble = libraryKey(link, TranscriptionMode.Ensemble)
        val arrangement = libraryKey(link, TranscriptionMode.Arrangement)
        assertEquals(link + "#ensemble", ensemble)
        assertEquals(3, setOf(link, ensemble, arrangement).size)
        assertTrue(ensemble.isEnsembleKey)
        assertFalse(ensemble.isArrangementKey)
        assertFalse(arrangement.isEnsembleKey)
        assertEquals(TranscriptionMode.Ensemble, ensemble.libraryKeyMode)
        assertEquals(TranscriptionMode.Arrangement, arrangement.libraryKeyMode)
        assertEquals(link, ensemble.sourceOfLibraryKey)
        assertEquals(ensemble, libraryKey(ensemble, TranscriptionMode.Ensemble))
        // A key that already carries a mode keeps it
        assertEquals(arrangement, libraryKey(arrangement, TranscriptionMode.Ensemble))
    }

    @Test
    fun aPartOfAnEnsembleKeepsItsPartAndMode() {
        val key = libraryKey(clipSourceKey(link, Clip(30, 95)), TranscriptionMode.Ensemble)
        assertEquals(link + "#part=30-95#ensemble", key)
        assertEquals(link, key.sourceOfLibraryKey)
        assertEquals(Clip(30, 95), key.libraryKeyClip)
        assertEquals(TranscriptionMode.Ensemble, key.libraryKeyMode)
        // Songs already in a library (old arrangement keys) read as before
        val old = "$link#part=30-95#piano-arrangement"
        assertEquals(Clip(30, 95), old.libraryKeyClip)
        assertEquals(link, old.sourceOfLibraryKey)
    }

    // --- Server modes ---

    @Test
    fun serverInfoDecidesTheOfferedModes() {
        val piano = TranscriptionMode.Transcription
        assertEquals(listOf(piano, TranscriptionMode.Ensemble), offeredModes(arrangement = false, ensemble = true))
        // Defensive: a new server never says arrangement, but ensemble wins if it did
        assertEquals(listOf(piano, TranscriptionMode.Ensemble), offeredModes(arrangement = true, ensemble = true))
        assertEquals(listOf(piano, TranscriptionMode.Arrangement), offeredModes(arrangement = true, ensemble = null))
        assertEquals(listOf(piano, TranscriptionMode.Arrangement), offeredModes(arrangement = true, ensemble = false))
        assertEquals(listOf(piano), offeredModes(arrangement = null, ensemble = null))
        assertEquals(listOf(piano), offeredModes(arrangement = false, ensemble = false))
    }

    @Test
    fun serverInfoAndDeveloperStateReadEnsemble() {
        val info = Gson().fromJson("""{"arrangement": false, "ensemble": true}""", ServerInfoResponse::class.java)
        assertEquals(true, info.ensemble)
        assertEquals(null, Gson().fromJson("""{"arrangement": true}""", ServerInfoResponse::class.java).ensemble)
        val state = Gson().fromJson(
            """{"ensemble": true, "arrangement": false, "workers": {"count": 1, "ensemble": 2, "arranging": 0}}""",
            DeveloperServerState::class.java
        )
        assertEquals(true, state.ensemble)
        assertEquals(2, state.workers?.ensemble)
    }

    // --- Requests ---

    private fun requestJson(mode: TranscriptionMode, ensembleServer: Boolean) = JsonParser.parseString(
        Gson().toJson(CreateTranscriptionRequest(link, mode.requestValue(ensembleServer), mode.style))
    ).asJsonObject

    @Test
    fun newServersGetPianoAndEnsembleWithoutAStyle() {
        val piano = requestJson(TranscriptionMode.Transcription, ensembleServer = true)
        assertEquals("piano", piano["mode"].asString)
        assertFalse(piano.has("style"))
        val ensemble = requestJson(TranscriptionMode.Ensemble, ensembleServer = true)
        assertEquals("ensemble", ensemble["mode"].asString)
        assertFalse(ensemble.has("style"))
    }

    @Test
    fun oldServersGetTheRequestsTheyKnow() {
        assertEquals(setOf("source_url"), requestJson(TranscriptionMode.Transcription, ensembleServer = false).keySet())
        val arrangement = requestJson(TranscriptionMode.Arrangement, ensembleServer = false)
        assertEquals("arrangement", arrangement["mode"].asString)
        assertEquals("level2", arrangement["style"].asString)
    }

    @Test
    fun statusModesAreReadFromAllFourWords() {
        assertEquals(TranscriptionMode.Transcription, TranscriptionMode.fromApiValue("piano"))
        assertEquals(TranscriptionMode.Transcription, TranscriptionMode.fromApiValue("transcription"))
        assertEquals(TranscriptionMode.Ensemble, TranscriptionMode.fromApiValue("ensemble"))
        assertEquals(TranscriptionMode.Arrangement, TranscriptionMode.fromApiValue("arrangement"))
        // What the app saved as its last choice reads back as itself
        TranscriptionMode.entries.forEach { assertEquals(it, TranscriptionMode.fromApiValue(it.apiValue)) }
    }

    @Test
    fun aFailedEnsembleSaysSo() {
        val job = TranscriptionStatusResponse("job-1", "failed", 0f, error = null, mode = "ensemble")
        assertEquals("The server could not transcribe this ensemble.", job.failureMessage)
        assertEquals("ensemble transcription", TranscriptionMode.Ensemble.jobNoun)
    }

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
