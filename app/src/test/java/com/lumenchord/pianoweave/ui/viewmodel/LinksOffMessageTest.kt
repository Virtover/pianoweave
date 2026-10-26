package com.lumenchord.pianoweave.ui.viewmodel

import com.lumenchord.pianoweave.api.httpError
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksOffMessageTest {

    private fun refusal(message: String) =
        """{"detail": {"message": "$message", "error_code": "source_temporarily_unavailable"}}"""

    @Test
    fun theServersMessageNamesTheSiteAndGetsTheWait() {
        val e = httpError(
            503,
            refusal("Links to youtube.com cannot be read at the moment; try again later, or upload the file instead"),
            "Retry-After", "1500"
        )
        assertEquals(
            "Links to youtube.com cannot be read at the moment; try again in 25 minutes, or upload the file instead.",
            linksOffMessage(e)
        )
    }

    @Test
    fun aSingleTimeoutKeepsItsOwnWording() {
        val e = httpError(
            503,
            refusal("youtube.com did not answer in time; try again, or upload the file instead"),
            "Retry-After", "30"
        )
        assertEquals("youtube.com did not answer in time; try again, or upload the file instead.", linksOffMessage(e))
    }

    @Test
    fun withoutAMessageItFallsBack() {
        assertEquals(
            "Video links can't be read at the moment. Try again in a minute, or upload the file instead.",
            linksOffMessage(httpError(503, """{"detail": {"error_code": "source_temporarily_unavailable"}}""", "Retry-After", "30"))
        )
    }
}
