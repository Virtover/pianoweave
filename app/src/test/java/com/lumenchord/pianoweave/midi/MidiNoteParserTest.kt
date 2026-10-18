package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiNoteParserTest {

    private val events = mutableListOf<String>()
    private val parser = MidiNoteParser(
        onNoteOn = { pitch, velocity -> events += "on $pitch $velocity" },
        onNoteOff = { pitch -> events += "off $pitch" }
    )

    private fun feed(vararg bytes: Int, offset: Int = 0, count: Int = bytes.size) =
        parser.feed(ByteArray(bytes.size) { bytes[it].toByte() }, offset, count)

    @Test
    fun noteOnAndOff() {
        feed(0x90, 60, 100, 0x80, 60, 0)
        assertEquals(listOf("on 60 100", "off 60"), events)
    }

    @Test
    fun noteOnWithZeroVelocityIsNoteOff() {
        feed(0x90, 60, 100, 0x90, 60, 0)
        assertEquals(listOf("on 60 100", "off 60"), events)
    }

    @Test
    fun anyChannelIsAccepted() {
        feed(0x93, 62, 80, 0x8F, 62, 0)
        assertEquals(listOf("on 62 80", "off 62"), events)
    }

    @Test
    fun runningStatusChord() {
        feed(0x90, 60, 100, 64, 90, 67, 80)
        assertEquals(listOf("on 60 100", "on 64 90", "on 67 80"), events)
    }

    @Test
    fun runningStatusReleaseWithZeroVelocity() {
        feed(0x90, 60, 100, 60, 0)
        assertEquals(listOf("on 60 100", "off 60"), events)
    }

    @Test
    fun messageSplitAcrossPackets() {
        feed(0x90, 60)
        feed(100)
        feed(64, 90)
        assertEquals(listOf("on 60 100", "on 64 90"), events)
    }

    @Test
    fun realTimeBytesInsideMessageAreIgnored() {
        feed(0x90, 0xF8, 60, 0xFE, 100, 0xF8, 64, 90)
        assertEquals(listOf("on 60 100", "on 64 90"), events)
    }

    @Test
    fun sysExIsSkipped() {
        // Data bytes inside SysEx must not continue the earlier note running status
        feed(0x90, 59, 1, 0xF0, 0x7E, 60, 100, 0xF7, 0x90, 62, 70)
        assertEquals(listOf("on 59 1", "on 62 70"), events)
    }

    @Test
    fun otherMessagesAreNotMistakenForNotes() {
        // Sustain pedal, program change (1 data byte), aftertouch, pitch bend, then a note
        feed(0xB0, 64, 127, 0xC0, 5, 0xD0, 40, 0xE0, 0, 64, 0x90, 60, 100)
        assertEquals(listOf("on 60 100"), events)
    }

    @Test
    fun runningStatusAfterControlChangeIsNotANote() {
        feed(0xB0, 64, 127, 64, 0)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun systemCommonClearsRunningStatus() {
        feed(0x90, 60, 100, 0xF2, 0, 0, 62, 70)
        assertEquals(listOf("on 60 100"), events)
    }

    @Test
    fun strayDataBeforeAnyStatusIsIgnored() {
        feed(60, 100, 0x90, 62, 70)
        assertEquals(listOf("on 62 70"), events)
    }

    @Test
    fun respectsOffsetAndCount() {
        feed(0x90, 1, 1, 0x90, 60, 100, 0x90, 2, 2, offset = 3, count = 3)
        assertEquals(listOf("on 60 100"), events)
    }
}
