package com.lumenchord.pianoweave

import com.lumenchord.pianoweave.api.UploadFormats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadFormatsTest {

    @Test
    fun acceptsTheServersFormatsByMimeType() {
        listOf("audio/mpeg", "audio/wav", "audio/flac", "audio/mp4", "audio/ogg", "video/mp4", "video/quicktime",
            "video/x-matroska", "video/webm", "audio/x-ms-wma", "audio/aiff", "audio/aac").forEach {
            assertTrue(it, UploadFormats.isAllowed(it, "song"))
        }
    }

    @Test
    fun fallsBackToTheExtensionForGenericTypes() {
        assertTrue(UploadFormats.isAllowed("application/octet-stream", "Nocturne.MP3"))
        assertTrue(UploadFormats.isAllowed(null, "take 2.flac"))
        // Some providers use unlisted audio aliases
        assertTrue(UploadFormats.isAllowed("audio/x-mpeg-3", "song.mp3"))
    }

    @Test
    fun rejectsOtherFiles() {
        assertFalse(UploadFormats.isAllowed("audio/3gpp", "voice.3gp"))
        assertFalse(UploadFormats.isAllowed("audio/midi", "song.mid"))
        assertFalse(UploadFormats.isAllowed("application/x-mpegurl", "list.m3u8"))
        assertFalse(UploadFormats.isAllowed("application/pdf", "sheet.pdf"))
        // A misleading extension does not override a known non-media type
        assertFalse(UploadFormats.isAllowed("text/plain", "notes.mp3"))
        assertFalse(UploadFormats.isAllowed(null, "noextension"))
    }
}
