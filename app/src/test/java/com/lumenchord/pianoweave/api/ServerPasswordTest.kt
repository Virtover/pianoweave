package com.lumenchord.pianoweave.api

import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPasswordTest {

    private val challenge = headersOf("WWW-Authenticate", "Basic realm=\"Piano Transcription Server\", charset=\"UTF-8\"")

    @Test
    fun passwordChallengeIsRecognized() {
        assertTrue(PianoApiFactory.isServerPasswordRequired(401, challenge))
        assertTrue(PianoApiFactory.isServerPasswordRequired(401, headersOf("www-authenticate", "basic realm=\"x\"")))
    }

    @Test
    fun googleSignInRefusalIsNotAPasswordChallenge() {
        // The server's 401 for a missing or bad Google token sends no Basic challenge
        assertFalse(PianoApiFactory.isServerPasswordRequired(401, headersOf()))
        assertFalse(PianoApiFactory.isServerPasswordRequired(401, headersOf("WWW-Authenticate", "Bearer")))
        assertFalse(PianoApiFactory.isServerPasswordRequired(401, null))
    }

    @Test
    fun otherStatusesAreNotAPasswordChallenge() {
        assertFalse(PianoApiFactory.isServerPasswordRequired(200, challenge))
        assertFalse(PianoApiFactory.isServerPasswordRequired(403, challenge))
    }
}
