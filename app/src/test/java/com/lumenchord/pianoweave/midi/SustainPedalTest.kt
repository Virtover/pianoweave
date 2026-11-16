package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SustainPedalTest {
    private val stopped = mutableListOf<Int>()
    private val pedal = SustainPedal { stopped += it }

    @Test
    fun withoutThePedalALetGoKeyStopsAtOnce() {
        assertTrue(pedal.noteOff(60))
    }

    @Test
    fun keysLetGoWhileThePedalIsDownRingUntilItComesUp() {
        pedal.onPedal(127)
        assertFalse(pedal.noteOff(60))
        assertFalse(pedal.noteOff(64))
        assertTrue(pedal.isRinging(60))
        assertTrue(stopped.isEmpty())
        pedal.onPedal(0)
        assertEquals(listOf(60, 64), stopped.sorted())
        assertFalse(pedal.isRinging(60))
        assertTrue(pedal.noteOff(67)) // pedal up again: back to stopping at once
    }

    @Test
    fun aKeyPlayedAgainWhileRingingIsHeldByTheFingerAndNotCutByThePedal() {
        pedal.onPedal(100)
        pedal.noteOff(60)
        pedal.noteOn(60)
        pedal.onPedal(10)
        assertTrue(stopped.isEmpty()) // still held down
    }

    @Test
    fun valuesAreSplitAt64AndRepeatsChangeNothing() {
        pedal.onPedal(63)
        assertFalse(pedal.isDown)
        pedal.onPedal(64)
        assertTrue(pedal.isDown)
        pedal.noteOff(60)
        pedal.onPedal(90) // still down (half-pedal wobble): nothing released
        assertTrue(stopped.isEmpty())
    }

    @Test
    fun resetLiftsThePedalWithoutStoppingAnythingItself() {
        pedal.onPedal(127)
        pedal.noteOff(60)
        pedal.reset()
        assertFalse(pedal.isDown)
        assertFalse(pedal.isRinging(60))
        assertTrue(stopped.isEmpty()) // the caller stops all sound itself
    }

    @Test
    fun allNotesOffFromTheSimulatorReachesTheParserAsControlChange123() {
        val changes = mutableListOf<Pair<Int, Int>>()
        val parser = MidiNoteParser({ _, _ -> }, { }, { c, v -> changes += c to v })
        val midi = BleMidiPacket.decode(byteArrayOf(0x9A.toByte(), 0xD5.toByte(), 0xB0.toByte(), 0x7B, 0x00))
        parser.feed(midi, 0, midi.size)
        assertEquals(listOf(123 to 0), changes)
    }

    @Test
    fun theParserReportsControlChangesIncludingRunningStatus() {
        val changes = mutableListOf<Pair<Int, Int>>()
        val parser = MidiNoteParser({ _, _ -> }, { }, { c, v -> changes += c to v })
        val bytes = byteArrayOf(0xB0.toByte(), 64, 127, 64, 0, 0x90.toByte(), 60, 100)
        parser.feed(bytes, 0, bytes.size)
        assertEquals(listOf(64 to 127, 64 to 0), changes)
    }
}
