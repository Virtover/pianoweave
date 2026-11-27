package com.lumenchord.pianoweave.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether the link field lets go of focus: after a pasted link, never while typing or after pasting something else. */
class LinkPasteTest {

    private val link = "https://youtu.be/edZVnKxKEUU"

    @Test
    fun aLinkPastedIntoAnEmptyField_orOverAllOfIt_isAPaste() {
        assertTrue(isPastedLink("", link))
        assertTrue(isPastedLink("", "  $link  "))
        assertTrue(isPastedLink("https://www.youtube.com/watch?v=WCxx_OVOVUw", link))
        assertTrue(isPastedLink("youtu.be/x", "https://www.tiktok.com/@someone/video/7312345678901234567"))
        // The app completes a bare address with https, as it does when it reads the link
        assertTrue(isPastedLink("", "youtu.be/edZVnKxKEUU"))
    }

    @Test
    fun typing_aCharacterAtATime_orEditingPartOfTheLink_keepsFocus() {
        var field = ""
        for (c in link) {
            val next = field + c
            assertFalse("typed '$next'", isPastedLink(field, next))
            field = next
        }
        assertFalse(isPastedLink(link, link.dropLast(1)))
        assertFalse(isPastedLink(link, link.replace("edZ", "edY")))
        assertFalse(isPastedLink(link, link + "&t=4"))
    }

    @Test
    fun pastingSomethingThatIsNotALink_keepsFocus() {
        for (text in listOf("SO BE IT Elevation Worship", "not a link at all", "ftp://files.example.com/song.mid", "localhost/abc", "https:// broken link"))
            assertFalse(text, isPastedLink("", text))
    }

    @Test
    fun insertedLength_countsOnlyWhatChanged() {
        assertEquals(1, insertedLength("abc", "abXc"))
        assertEquals(0, insertedLength("abc", "ab"))
        assertEquals(3, insertedLength("aaa", "aaaaaa"))
        assertEquals(link.length, insertedLength("", link))
    }
}
