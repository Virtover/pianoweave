package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.httpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseVerificationTest {

    @Test
    fun refusalsBeforeThePurchaseWasLookedAtKeepItPending() {
        // Expired Google sign-in or a missing server password: the purchase was never checked
        assertTrue(isVerificationRetryable(httpError(401)))
        assertTrue(isVerificationRetryable(httpError(401, "", "WWW-Authenticate", "Basic realm=\"x\"")))
        assertTrue(isVerificationRetryable(httpError(408)))
        assertTrue(isVerificationRetryable(httpError(429, "", "Retry-After", "60")))
    }

    @Test
    fun serverFailuresKeepItPending() {
        for (code in listOf(500, 502, 503, 504)) assertTrue("$code", isVerificationRetryable(httpError(code)))
    }

    @Test
    fun onlyFinalRefusalsEndIt() {
        // A product the server doesn't sell, or a purchase Google says isn't done
        assertFalse(isVerificationRetryable(httpError(400, """{"detail": "Unknown product"}""")))
        assertFalse(isVerificationRetryable(httpError(400, """{"detail": "Purchase is not completed"}""")))
        assertEquals(VerificationOutcome.Drop, packVerificationOutcome(400, "Unknown product"))
    }

    @Test
    fun anyOtherRefusalKeepsItForAnotherTry() {
        // "billing is disabled" (also briefly without the auth header), other 4xx: the paid purchase must not be lost
        assertTrue(isVerificationRetryable(httpError(409, """{"detail": "Google Play billing is disabled"}""")))
        for (code in listOf(400, 403, 404, 409, 422)) assertTrue("$code", isVerificationRetryable(httpError(code)))
        assertEquals(VerificationOutcome.Retry, packVerificationOutcome(400, "Something else"))
    }
}
