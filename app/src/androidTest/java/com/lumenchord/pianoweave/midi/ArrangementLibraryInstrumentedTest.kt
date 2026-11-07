package com.lumenchord.pianoweave.midi

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.api.isArrangementKey
import com.lumenchord.pianoweave.api.libraryKey
import com.lumenchord.pianoweave.api.placeholderMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library keeps a transcription and an arrangement of the same video as two songs. Writes to the app's
 * real library, so it uses a link no one would transcribe and removes both songs afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ArrangementLibraryInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val link = "https://example.invalid/watch?v=arrangement-library-test"
    private val stored = mutableListOf<StoredMidi>()

    @After
    fun removeTestSongs() {
        stored.forEach { MidiStorage.hardDelete(context, it) }
    }

    private fun save(mode: TranscriptionMode, bytes: ByteArray): StoredMidi {
        val key = libraryKey(link, mode)
        return MidiStorage.saveBytes(context, key, placeholderMetadata(key).copy(title = "Same Song"), bytes)
            .also { stored += it }
    }

    @Test
    fun bothVersionsOfOneVideoAreKept() {
        val transcription = save(TranscriptionMode.Transcription, byteArrayOf(1, 2, 3))
        val arrangement = save(TranscriptionMode.Arrangement, byteArrayOf(4, 5, 6, 7))

        assertNotEquals(transcription.file, arrangement.file)
        // Each is found by its own key, with its own MIDI
        assertEquals(transcription.file, MidiStorage.find(context, link))
        assertEquals(arrangement.file, MidiStorage.find(context, libraryKey(link, TranscriptionMode.Arrangement)))
        assertEquals(3L, transcription.file.length())
        assertEquals(4L, arrangement.file.length())

        val songs = MidiStorage.list(context).filter { it.videoUrl.startsWith(link) }
        assertEquals(2, songs.size)
        assertEquals(1, songs.count { it.videoUrl.isArrangementKey })
    }

    @Test
    fun deletingOneLeavesTheOther() {
        val transcription = save(TranscriptionMode.Transcription, byteArrayOf(1))
        val arrangement = save(TranscriptionMode.Arrangement, byteArrayOf(2))

        MidiStorage.hardDelete(context, transcription)

        assertEquals(null, MidiStorage.find(context, link))
        assertNotNull(MidiStorage.find(context, libraryKey(link, TranscriptionMode.Arrangement)))
        assertEquals(arrangement.file, MidiStorage.find(context, libraryKey(link, TranscriptionMode.Arrangement)))
    }
}
