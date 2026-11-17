package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.httpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Credit packs found again in Play's purchases: which are sent to the server, and how often. */
class PackRecoveryTest {

    private val offered = setOf("credits_50", "credits_100")

    @Test
    fun aPackTheServerSellsIsSent() {
        assertTrue(shouldRecoverPack("credits_100", offered, refusedBefore = false, verifiedAgoMs = null))
    }

    @Test
    fun aProductTheServerDoesntSellIsNotSent() {
        // Old purchases of another server's products: refused with 400 on every recovery before
        assertFalse(shouldRecoverPack("transcription_25", offered, refusedBefore = false, verifiedAgoMs = null))
        assertFalse(shouldRecoverPack("transcription_25", emptySet(), refusedBefore = false, verifiedAgoMs = null))
    }

    @Test
    fun withTheOffersUnknownOrStaleOnlyRefusedTokensAreHeldBack() {
        assertTrue(shouldRecoverPack("transcription_25", null, refusedBefore = false, verifiedAgoMs = null))
        assertFalse(shouldRecoverPack("transcription_25", null, refusedBefore = true, verifiedAgoMs = null))
    }

    @Test
    fun aRefusedTokenIsNeverSentAgain() {
        assertFalse(shouldRecoverPack("credits_100", offered, refusedBefore = true, verifiedAgoMs = null))
    }

    @Test
    fun aTokenVerifiedAMomentAgoRests() {
        assertFalse(shouldRecoverPack("credits_100", offered, refusedBefore = false, verifiedAgoMs = 0))
        assertFalse(shouldRecoverPack("credits_100", offered, refusedBefore = false, verifiedAgoMs = RECOVERY_RECHECK_MS - 1))
        assertTrue(shouldRecoverPack("credits_100", offered, refusedBefore = false, verifiedAgoMs = RECOVERY_RECHECK_MS))
    }

    @Test
    fun aBusyServerIsAskedAgainOnlyAfterItsWait() {
        assertEquals(120L, retryDelaySeconds(httpError(429, "", "Retry-After", "120")))
        // Without Retry-After, or a shorter one: at least a minute
        assertEquals(60L, retryDelaySeconds(httpError(429)))
        assertEquals(60L, retryDelaySeconds(httpError(429, "", "Retry-After", "5")))
        assertEquals(3_600L, retryDelaySeconds(httpError(429, "", "Retry-After", "86400")))
        // Other retryable answers: the worker's own backoff
        assertEquals(0L, retryDelaySeconds(httpError(503)))
        assertTrue(isVerificationRetryable(httpError(429)))
        assertFalse(isVerificationRetryable(httpError(400, """{"detail": "Unknown product"}""")))
    }

    @Test
    fun refusalsAreRememberedPerServerAndAccount() {
        val key = refusedPurchaseKey("https://dev.example/", "user-1", "tok")
        assertEquals(key, refusedPurchaseKey("https://dev.example", "user-1", "tok"))
        assertNotEquals(key, refusedPurchaseKey("https://other.example/", "user-1", "tok"))
        assertNotEquals(key, refusedPurchaseKey("https://dev.example/", "user-2", "tok"))
        // The account is stored as its hash, not as the id itself
        assertFalse(key.contains("user-1"))
    }
}
