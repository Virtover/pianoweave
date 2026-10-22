package com.lumenchord.pianoweave.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMaintenanceTest {

    private val now = 1_800_000_000L
    private val clock: (Long) -> String = { "T+${it - now}" }

    @Test
    fun headersAnnounceANotice() {
        val notice = ServerMaintenance.headerNotice("restart", "${now + 300}")
        assertEquals(MaintenanceNotice("restart", now + 300), notice)
        assertNull(ServerMaintenance.headerNotice("reboot", "${now + 300}"))
        assertNull(ServerMaintenance.headerNotice("shutdown", "soon"))
        assertNull(ServerMaintenance.headerNotice(null, null))
    }

    @Test
    fun sameNoticeInHeadersKeepsBodyDetails() {
        val detailed = MaintenanceNotice("shutdown", now + 600, reason = "Upgrade", backAt = now + 7200)
        val fromHeaders = MaintenanceNotice("shutdown", now + 600)
        assertSame(detailed, ServerMaintenance.nextNotice(detailed, fromHeaders, 200))
    }

    @Test
    fun rescheduledNoticeReplacesTheOldOne() {
        val old = MaintenanceNotice("restart", now + 300, reason = "Update")
        val moved = MaintenanceNotice("restart", now + 900)
        assertEquals(moved, ServerMaintenance.nextNotice(old, moved, 200))
    }

    @Test
    fun normalAnswerWithoutHeadersClearsIt() {
        val old = MaintenanceNotice("restart", now - 10)
        assertNull(ServerMaintenance.nextNotice(old, null, 200))
        assertNull(ServerMaintenance.nextNotice(old, null, 404))
    }

    @Test
    fun errorsWhileRestartingKeepIt() {
        val old = MaintenanceNotice("restart", now - 10)
        // A proxy's 502 or the API's 503 while it starts carry no headers
        assertSame(old, ServerMaintenance.nextNotice(old, null, 502))
        assertSame(old, ServerMaintenance.nextNotice(old, null, 503))
    }

    @Test
    fun countdownRoundsLikeTheServer() {
        assertEquals("45 s", MaintenanceTexts.countdown(45))
        assertEquals("1 min", MaintenanceTexts.countdown(64))
        assertEquals("5 min", MaintenanceTexts.countdown(299))
        assertEquals("0 s", MaintenanceTexts.countdown(-3))
    }

    @Test
    fun titlesFollowThePhase() {
        val restart = MaintenanceNotice("restart", now + 120)
        assertEquals("Server restart in 2 min", MaintenanceTexts.title(restart, now, clock))
        assertEquals("Server is restarting", MaintenanceTexts.title(restart, now + 120, clock))

        val shutdown = MaintenanceNotice("shutdown", now + 30, backAt = now + 3600)
        assertEquals("Server going offline in 30 s", MaintenanceTexts.title(shutdown, now, clock))
        assertEquals("Server offline until about T+3600", MaintenanceTexts.title(shutdown, now + 60, clock))
        // Past the expected return time it is just offline
        assertEquals("Server offline", MaintenanceTexts.title(shutdown, now + 4000, clock))
    }

    @Test
    fun detailMentionsReturnTimeAndReason() {
        val shutdown = MaintenanceNotice("shutdown", now + 600, reason = "New GPU", backAt = now + 7200)
        val detail = MaintenanceTexts.detail(shutdown, now, clock)
        assertTrue(detail, detail.contains("T+7200"))
        assertTrue(detail, detail.endsWith("Reason: New GPU"))
    }

    @Test
    fun refusalSaysWhenToTryAgain() {
        val restart = MaintenanceNotice("restart", now + 45)
        assertEquals(
            "The server restarts in 45 s, so new transcriptions are paused. Try again in about 7 min.",
            MaintenanceTexts.refusal(restart, now, retryAfterSec = 405, formatTime = clock)
        )
        val shutdown = MaintenanceNotice("shutdown", now + 600, backAt = now + 7200)
        assertEquals(
            "The server goes offline in 10 min, so new transcriptions are paused. Try again after T+7200.",
            MaintenanceTexts.refusal(shutdown, now, retryAfterSec = 7200, formatTime = clock)
        )
        val noReturn = MaintenanceNotice("shutdown", now + 600)
        assertTrue(MaintenanceTexts.refusal(noReturn, now, null, clock).endsWith("Try again later."))
    }
}
