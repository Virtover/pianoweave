package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattSequenceTest {
    private val started = mutableListOf<String>()
    private val done = mutableListOf<Boolean>()
    private val refuse = mutableSetOf<String>()

    private fun sequence(vararg targets: String) = GattSequence(
        targets.toList(),
        request = { t -> (t !in refuse).also { if (it) started += t } },
        onDone = { done += it },
    )

    @Test
    fun eachTargetGetsItsRequestAfterThePreviousReportedBack() {
        val s = sequence("pipewire", "script")
        assertTrue(s.start())
        assertEquals(listOf("pipewire"), started)
        s.onResult(true)
        assertEquals(listOf("pipewire", "script"), started)
        assertTrue(done.isEmpty())
        s.onResult(false)
        assertEquals(listOf(true), done) // one of them worked
        assertFalse(s.isRunning)
    }

    @Test
    fun aTargetThatCannotStartIsSkipped() {
        refuse += "pipewire"
        val s = sequence("pipewire", "script")
        assertTrue(s.start())
        assertEquals(listOf("script"), started)
        s.onResult(true)
        assertEquals(listOf(true), done)
    }

    @Test
    fun nothingStartingMeansNoResultAtAll() {
        refuse += listOf("a", "b")
        assertFalse(sequence("a", "b").start())
        assertTrue(done.isEmpty())
    }

    @Test
    fun allFailingIsReportedAsFailed() {
        val s = sequence("a")
        s.start()
        s.onResult(false)
        assertEquals(listOf(false), done)
    }

    @Test
    fun aCancelledOrFinishedSequenceIgnoresLateResults() {
        val s = sequence("a", "b")
        s.start()
        s.cancel()
        s.onResult(true)
        assertEquals(listOf("a"), started)
        assertTrue(done.isEmpty())
    }
}
