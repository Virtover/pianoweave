package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.api.LinkPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The link field while it shows a read link: the title alone, cut to the field, and what a screen reader hears. */
class ResolvedLinkTest {

    private fun fitsIn(chars: Int): (String) -> Boolean = { it.length <= chars }

    @Test
    fun aTitleThatFits_isShownWhole() {
        assertEquals("SO BE IT", resolvedLinkText("SO BE IT", fitsIn(30)))
    }

    @Test
    fun aLongTitle_isCutWithAnEllipsis_withinTheField() {
        val shown = resolvedLinkText("SO BE IT | Elevation Worship (feat. Tiffany Hudson)", fitsIn(20))
        assertEquals("SO BE IT | Elevatio…", shown)
        assertTrue(shown.length <= 20)
        // No space left before the ellipsis
        assertEquals("SO BE IT…", resolvedLinkText("SO BE IT now", fitsIn(10)))
    }

    @Test
    fun aVeryNarrowField_stillShowsTheTitlesStart() {
        assertEquals("S…", resolvedLinkText("SO BE IT", fitsIn(0)))
    }

    @Test
    fun theScreenReaderHearsTheChannelAndLength_thatAreNotShown() {
        assertEquals(
            "Link: SO BE IT, by Elevation Worship, length 5:44",
            resolvedLinkDescription(LinkPreview(title = "SO BE IT", channel = "Elevation Worship", durationSeconds = 344f))
        )
        assertEquals("Link: SO BE IT", resolvedLinkDescription(LinkPreview(title = "SO BE IT")))
    }
}
