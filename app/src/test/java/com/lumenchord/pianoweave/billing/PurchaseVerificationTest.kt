package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.httpError
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
    fun realAnswersAboutThePurchaseEndIt() {
        // e.g. an invalid or already refunded purchase token
        for (code in listOf(400, 403, 404, 409, 422)) assertFalse("$code", isVerificationRetryable(httpError(code)))
    }
}
