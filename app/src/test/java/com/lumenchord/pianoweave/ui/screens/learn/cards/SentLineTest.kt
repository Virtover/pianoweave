package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.LinkPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The line under Transcribe: the link's title and length in the button's own sub-line, else the card as it was. */
class SentLineTest {

    private val preview = LinkPreview(title = "SO BE IT", channel = "Elevation Worship", durationSeconds = 344f)

    @Test
    fun noTitleAndNoPart_leaveTheButtonAsItWas() {
        assertNull(sentLine(null, null, withChannel = true))
        // An older server: the length alone adds nothing to the button
        assertNull(sentLine(LinkPreview(durationSeconds = 344f), null, withChannel = true))
    }

    @Test
    fun theTitleAndLength_withTheChannelWhereThereIsRoom() {
        assertEquals("SO BE IT" to "5:44", sentLine(preview, null, withChannel = false))
        assertEquals("SO BE IT · Elevation Worship" to "5:44", sentLine(preview, null, withChannel = true))
    }

    @Test
    fun aChosenPart_isTheLengthSaid() {
        val clip = Clip(30, 70)
        val (name, length) = sentLine(preview, clip, withChannel = false)!!
        assertEquals("SO BE IT", name)
        assertEquals("0:30 – 1:10", length)
        assertEquals(null to "0:30 – 1:10", sentLine(null, clip, withChannel = false))
    }

    @Test
    fun readsAsWords() {
        assertEquals("Video: SO BE IT, by Elevation Worship, length 5:44", sentLineDescription(preview, null))
    }
}
