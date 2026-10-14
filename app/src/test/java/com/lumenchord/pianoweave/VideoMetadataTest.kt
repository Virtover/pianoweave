package com.lumenchord.pianoweave

import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.isPlaceholder
import com.lumenchord.pianoweave.api.placeholderMetadata
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMetadataTest {

    private val url = "https://www.youtube.com/watch?v=abc"

    @Test
    fun placeholderIsDetected_alsoAfterSanitizing() {
        assertTrue(placeholderMetadata(url).isPlaceholder)
        assertTrue(sanitizeVideoMetadata(null, url).isPlaceholder)
    }

    @Test
    fun realMetadataIsNotAPlaceholder() {
        assertFalse(VideoMetadata(title = "Clair de Lune", author = "Someone", channel = "Someone", duration = 300f).isPlaceholder)
        // Server sent only a title (metadata failed validation server-side)
        assertFalse(placeholderMetadata(url).copy(title = "Clair de Lune").isPlaceholder)
        // Real video that happens to use the placeholder title still has a duration
        assertFalse(placeholderMetadata(url).copy(duration = 180f).isPlaceholder)
    }

    @Test
    fun sanitizeReplacesNullsGsonPutIntoNonNullFields() {
        val fromServer = Gson().fromJson(
            """{"title":"Nocturne","author":null,"channel":null,"thumbnail":null,"webpage_url":null,"duration":120.5}""",
            VideoMetadata::class.java
        )
        val clean = sanitizeVideoMetadata(fromServer, url)
        assertEquals("Nocturne", clean.title)
        assertEquals("Unknown", clean.author)
        assertEquals("", clean.thumbnail)
        assertEquals(url, clean.webpage_url)
        assertFalse(clean.isPlaceholder)
    }
}
