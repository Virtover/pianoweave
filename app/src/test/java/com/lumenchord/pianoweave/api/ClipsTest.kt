package com.lumenchord.pianoweave.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lumenchord.pianoweave.cloud.CloudMetadataEnvelope
import com.lumenchord.pianoweave.cloud.parseCloudDescription
import com.lumenchord.pianoweave.midi.CompletedTranscriptions
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Trim: parts of a song, their library keys, what is sent, overlaps, and how they survive Drive sync. */
class ClipsTest {

    private val link = "https://www.youtube.com/watch?v=abc"

    // --- Library keys ---

    @Test
    fun aPartIsItsOwnSongBesideTheWholeAndOtherParts() {
        val whole = libraryKey(link, TranscriptionMode.Transcription)
        val first = libraryKey(clipSourceKey(link, Clip(30, 95)), TranscriptionMode.Transcription)
        val second = libraryKey(clipSourceKey(link, Clip(95, null)), TranscriptionMode.Transcription)
        assertEquals(3, setOf(whole, first, second).size)
        assertEquals("$link#part=30-95", first)
        assertEquals("$link#part=95-", second)
    }

    @Test
    fun aPartKeyGivesBackItsSourcePartAndMode() {
        for (mode in TranscriptionMode.entries) {
            val key = libraryKey(clipSourceKey(link, Clip(30, 95)), mode)
            assertEquals(link, key.sourceOfLibraryKey)
            assertEquals(Clip(30, 95), key.libraryKeyClip)
            assertEquals(mode, key.libraryKeyMode)
        }
        assertEquals("$link#part=30-95#piano-arrangement", libraryKey(clipSourceKey(link, Clip(30, 95)), TranscriptionMode.Arrangement))
    }

    @Test
    fun keysWithoutAPartAreAsBefore() {
        assertNull(link.libraryKeyClip)
        assertEquals(link, clipSourceKey(link, null))
        val arrangement = libraryKey(link, TranscriptionMode.Arrangement)
        assertNull(arrangement.libraryKeyClip)
        assertEquals(link, arrangement.sourceOfLibraryKey)
    }

    @Test
    fun uploadKeysTakeAPartToo() {
        val key = libraryKey(clipSourceKey(uploadSourceKey("job-7"), Clip(0, 60)), TranscriptionMode.Arrangement)
        assertEquals(uploadSourceKey("job-7"), key.sourceOfLibraryKey)
        assertTrue(key.sourceOfLibraryKey.isUploadSourceKey)
        assertEquals(Clip(0, 60), key.libraryKeyClip)
    }

    @Test
    fun aMarkThatIsNotAPartIsLeftInTheSource() {
        // A link that happens to have a fragment like it, but not one this app writes
        for (odd in listOf("$link#part=abc", "$link#part=40-30", "$link#part=0-", "$link#part=-5")) {
            assertNull(odd, odd.libraryKeyClip)
            assertEquals(odd, odd.sourceOfLibraryKey)
        }
    }

    // --- Times ---

    @Test
    fun timesAreReadAsTyped() {
        assertEquals(95, parseClipTime("95"))
        assertEquals(95, parseClipTime("1:35"))
        assertEquals(3725, parseClipTime(" 1:02:05 "))
        for (bad in listOf("", "1:75", "a", "1::2", "-5", "1.5", "1:2:3:4", "9999999")) assertNull(bad, parseClipTime(bad))
        assertEquals("1:35", formatClipTime(95))
        assertEquals("1:02:05", formatClipTime(3725))
        assertEquals("0:30 – end", Clip(30, null).describe())
    }

    @Test
    fun aChoiceIsKeptWithinTheSong() {
        assertEquals(Clip(30, 95), Clip.within(30, 95, 130.4f))
        // To the song's end means "to the end", which the server measures itself
        assertEquals(Clip(30, null), Clip.within(30, 130, 130.4f))
        assertNull(Clip.within(0, 130, 130.4f))
        assertNull(Clip.within(-10, 500, 130f))
        assertEquals(Clip(129, null), Clip.within(400, 500, 130f))
        assertEquals(Clip(60, 61), Clip.within(60, 60, 130f))
        assertEquals(65.5f, Clip(30, null).lengthSeconds(95.5f))
    }

    // --- Overlaps ---

    private fun key(clip: Clip?, mode: TranscriptionMode = TranscriptionMode.Transcription) =
        libraryKey(clipSourceKey(link, clip), mode)

    @Test
    fun partsThatOverlapAreFound() {
        val library = listOf(key(Clip(0, 60)), key(Clip(60, 120)), key(Clip(200, null)), "https://other.example/v")
        assertEquals(listOf(Clip(0, 60), Clip(60, 120)), overlappingParts(library, link, TranscriptionMode.Transcription, Clip(30, 90)))
        // Touching is not overlapping
        assertEquals(emptyList<Clip?>(), overlappingParts(library, link, TranscriptionMode.Transcription, Clip(120, 200)))
        // The whole song overlaps every part
        assertEquals(3, overlappingParts(library, link, TranscriptionMode.Transcription, null).size)
        assertEquals(listOf(Clip(200, null)), overlappingParts(library, link, TranscriptionMode.Transcription, Clip(250, 260)))
    }

    @Test
    fun theWholeSongInTheLibraryOverlapsAnyPart() {
        assertEquals(listOf<Clip?>(null), overlappingParts(listOf(key(null)), link, TranscriptionMode.Transcription, Clip(10, 20)))
    }

    @Test
    fun theSamePartAndOtherModesDoNotWarn() {
        val library = listOf(key(Clip(0, 60)), key(Clip(0, 60), TranscriptionMode.Arrangement))
        // The same part loads from the library instead
        assertEquals(emptyList<Clip?>(), overlappingParts(library, link, TranscriptionMode.Transcription, Clip(0, 60)))
        assertEquals(listOf<Clip?>(Clip(0, 60)), overlappingParts(library, link, TranscriptionMode.Arrangement, Clip(30, 90)))
        assertEquals(emptyList<Clip?>(), overlappingParts(listOf(key(Clip(0, 60))), link, TranscriptionMode.Arrangement, Clip(30, 90)))
    }

    // --- Google Drive ---

    @Test
    fun aPartsKeySurvivesTheDriveDescription() {
        val gson = Gson()
        for (mode in TranscriptionMode.entries) {
            val key = key(Clip(30, 95), mode)
            val description = gson.toJson(CloudMetadataEnvelope(videoUrl = key, metadata = VideoMetadata(title = "Song"), addedAt = 1L))
            val (envelope, isLegacy) = parseCloudDescription(gson, description)!!
            assertFalse(isLegacy)
            assertEquals(key, envelope.videoUrl)
            assertEquals(Clip(30, 95), envelope.videoUrl!!.libraryKeyClip)
            assertEquals(mode, envelope.videoUrl!!.libraryKeyMode)
        }
    }

    @Test
    fun aPartsSongLastsAsLongAsThePart() {
        val metadata = VideoMetadata(title = "Song", duration = 130f)
        assertEquals(65f, CompletedTranscriptions.partLength(metadata, key(Clip(30, 95))).duration)
        assertEquals(100f, CompletedTranscriptions.partLength(metadata, key(Clip(30, null), TranscriptionMode.Arrangement)).duration)
        assertEquals(130f, CompletedTranscriptions.partLength(metadata, key(null)).duration)
    }

    // --- Requests ---

    private var lastRequest: Request? = null

    private fun api(): PianoApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                lastRequest = chain.request()
                okhttp3.Response.Builder()
                    .request(chain.request()).protocol(Protocol.HTTP_1_1).code(202).message("Accepted")
                    .body("""{"job_id": "job-1", "status": "queued"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return Retrofit.Builder().baseUrl("http://server.test/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PianoApi::class.java)
    }

    private fun sentJson() = JsonParser.parseString(Buffer().also { lastRequest!!.body!!.writeTo(it) }.readUtf8()).asJsonObject

    @Test
    fun aLinkSendsItsPart() = runBlocking {
        api().createTranscription(CreateTranscriptionRequest(link, startSeconds = 30, endSeconds = 95))
        val sent = sentJson()
        assertEquals(30, sent["start_seconds"].asInt)
        assertEquals(95, sent["end_seconds"].asInt)
    }

    @Test
    fun theWholeSongSendsNoPart() = runBlocking {
        // What servers without trimming know: nothing new in the request
        api().createTranscription(CreateTranscriptionRequest(link, startSeconds = 30))
        assertFalse(sentJson().has("end_seconds"))
        api().createTranscription(CreateTranscriptionRequest(link))
        assertEquals(setOf("source_url"), sentJson().keySet())
    }

    @Test
    fun anUploadSendsItsPartAsQueryParameters() = runBlocking {
        api().uploadTranscription("song.mp3", "abc".toRequestBody(), startSeconds = 60, endSeconds = 180)
        assertEquals("60", lastRequest!!.url.queryParameter("start_seconds"))
        assertEquals("180", lastRequest!!.url.queryParameter("end_seconds"))
        api().uploadTranscription("song.mp3", "abc".toRequestBody())
        assertNull(lastRequest!!.url.queryParameter("start_seconds"))
    }

    @Test
    fun serverInfoSaysWhetherItTrims() {
        val gson = Gson()
        assertEquals(true, gson.fromJson("""{"trimming": true}""", ServerInfoResponse::class.java).trimming)
        assertNull(gson.fromJson("""{}""", ServerInfoResponse::class.java).trimming)
    }
}
