package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleMidiPacketTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** Feeds a decoded packet through the real note parser, the way BleMidiPeripheral does. */
    private fun notes(packet: ByteArray): List<String> {
        val events = mutableListOf<String>()
        val parser = MidiNoteParser({ p, v -> events.add("on $p $v") }, { p -> events.add("off $p") })
        val midi = BleMidiPacket.decode(packet)
        parser.feed(midi, 0, midi.size)
        return events
    }

    @Test
    fun encodeThenDecodeGivesTheMessageBack() {
        val noteOn = bytes(0x90, 60, 100)
        val packet = BleMidiPacket.encode(noteOn, timeMs = 0x1ABC)
        assertEquals(5, packet.size)
        assertEquals(0x80 or (0x1ABC shr 7), packet[0].toInt() and 0xFF)
        assertEquals(0x80 or (0x1ABC and 0x7F), packet[1].toInt() and 0xFF)
        assertArrayEquals(noteOn, BleMidiPacket.decode(packet))
    }

    @Test
    fun aChordInOnePacketDecodesToEveryNote() {
        val packet = BleMidiPacket.encodeAll(listOf(bytes(0x90, 60, 90), bytes(0x90, 64, 91), bytes(0x80, 67, 0)))
        assertEquals(listOf("on 60 90", "on 64 91", "off 67"), notes(packet))
    }

    @Test
    fun realTimeBytesBetweenNotesDoNotSwallowTheNextOne() {
        // The simulator's test 13: clock and active sensing, each with its own timestamp, between notes
        assertEquals(listOf("on 60 100", "on 64 100"),
            notes(bytes(0xA8, 0x81, 0x90, 0x3C, 0x64, 0x81, 0xF8, 0x81, 0x90, 0x40, 0x64, 0x81, 0xFE)))
        // ...and running status carried across a clock byte: G4 then C5
        assertEquals(listOf("on 67 100", "on 72 100"),
            notes(bytes(0xAA, 0xAE, 0x90, 0x43, 0x64, 0xAE, 0xF8, 0xAE, 0x48, 0x64)))
    }

    @Test
    fun runningStatusWithAndWithoutTimestamps() {
        // header, ts, note on C, then E with only a timestamp before it, then G with nothing before it
        val packet = bytes(0x80, 0x81, 0x90, 60, 100, 0x82, 64, 100, 67, 100)
        assertEquals(listOf("on 60 100", "on 64 100", "on 67 100"), notes(packet))
    }

    @Test
    fun webBluetoothStylePacketsFromTheWebsite() {
        // What the website sends: one header, then timestamp + full message for each, in one write
        val packet = bytes(0x80, 0x80, 0x90, 48, 80, 0x80, 0xB0, 64, 127, 0x80, 0x80, 48, 0)
        assertEquals(listOf("on 48 80", "off 48"), notes(packet))
    }

    @Test
    fun sysExGetsATimestampBeforeItsEndAndDecodesWhole() {
        val sync = bytes(0xF0, 0x7D, 0x50, 0x57, 0x03, 0, 0, 1, 2, 1, 0, 100, 0xF7)
        val noteOn = bytes(0x90, 60, 100)
        val packet = BleMidiPacket.encodeAll(listOf(sync, noteOn), timeMs = 5)
        assertEquals(1 + BleMidiPacket.encodedSize(sync) + BleMidiPacket.encodedSize(noteOn), packet.size)
        // ... timestamp, F7, then the note: timestamp, 90, 60, 100
        assertEquals(0x85, packet[packet.size - 6].toInt() and 0xFF)
        assertEquals(0xF7, packet[packet.size - 5].toInt() and 0xFF)
        assertArrayEquals(sync + noteOn, BleMidiPacket.decode(packet))
    }

    @Test
    fun garbageIsIgnored() {
        assertEquals(0, BleMidiPacket.decode(ByteArray(0)).size)
        assertEquals(0, BleMidiPacket.decode(bytes(0x10, 0x90, 60, 100)).size) // no header bit
    }
}
