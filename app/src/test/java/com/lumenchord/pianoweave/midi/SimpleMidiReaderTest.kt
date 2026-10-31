package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class SimpleMidiReaderTest {

    /** A standard MIDI file with 480 ticks per quarter note and the given tracks' event bytes. */
    private fun midi(format: Int, vararg tracks: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun int32(v: Int) = listOf(v ushr 24, v ushr 16, v ushr 8, v).forEach { out.write(it and 0xFF) }
        fun int16(v: Int) = listOf(v ushr 8, v).forEach { out.write(it and 0xFF) }
        out.write("MThd".toByteArray()); int32(6); int16(format); int16(tracks.size); int16(480)
        for (track in tracks) {
            out.write("MTrk".toByteArray()); int32(track.size); track.forEach(out::write)
        }
        return out.toByteArray()
    }

    private fun tempo(microsPerQuarter: Int) =
        intArrayOf(0xFF, 0x51, 0x03, microsPerQuarter ushr 16 and 0xFF, microsPerQuarter ushr 8 and 0xFF, microsPerQuarter and 0xFF)

    private val endOfTrack = intArrayOf(0x00, 0xFF, 0x2F, 0x00)

    /** Delta 480 (one quarter), note on, delta 480, note off. 480 is the variable-length 0x83 0x60. */
    private fun quarterNoteAfterOneQuarter(pitch: Int) =
        intArrayOf(0x83, 0x60, 0x90, pitch, 100, 0x83, 0x60, 0x80, pitch, 0) + endOfTrack

    private fun parse(bytes: ByteArray) = SimpleMidiReader.parse(bytes.inputStream())

    @Test
    fun formatOneTracksFollowTheFirstTracksTempo() {
        // 60 BPM in the tempo track; the notes are in the next track, as DAWs and most exports write them
        val notes = parse(midi(1, intArrayOf(0x00) + tempo(1_000_000) + endOfTrack, quarterNoteAfterOneQuarter(60)))
        assertEquals(1, notes.size)
        assertEquals(1000L, notes[0].startMs)
        assertEquals(1000L, notes[0].durationMs)
    }

    @Test
    fun withoutTempoEventsItIs120Bpm() {
        val notes = parse(midi(0, quarterNoteAfterOneQuarter(64)))
        assertEquals(500L, notes[0].startMs)
        assertEquals(500L, notes[0].durationMs)
    }

    @Test
    fun tempoChangeMidSongTimesLaterNotes() {
        // 120 BPM, then from tick 480 on 60 BPM: a note at tick 960 starts at 500 + 1000 ms
        val tempoTrack = intArrayOf(0x00) + tempo(500_000) + intArrayOf(0x83, 0x60) + tempo(1_000_000) + endOfTrack
        val noteTrack = intArrayOf(0x87, 0x40, 0x90, 67, 90, 0x83, 0x60, 0x80, 67, 0) + endOfTrack
        val notes = parse(midi(1, tempoTrack, noteTrack))
        assertEquals(1500L, notes[0].startMs)
        assertEquals(1000L, notes[0].durationMs)
    }

    @Test
    fun formatZeroTempoAppliesInItsOwnTrack() {
        val track = intArrayOf(0x00) + tempo(250_000) + quarterNoteAfterOneQuarter(72)
        val notes = parse(midi(0, track))
        assertEquals(250L, notes[0].startMs)
        assertEquals(250L, notes[0].durationMs)
    }

    @Test
    fun sustainPedalSpansComeFromControlChange64() {
        // At 120 BPM: pedal down at 0, up at 500 ms (running status), down again at 1000 ms and never lifted.
        // Other controllers (7, volume) are ignored.
        val track = intArrayOf(0x00, 0xB0, 64, 127, 0x00, 0xB0, 7, 100, 0x83, 0x60, 64, 0, 0x83, 0x60, 64, 90) + endOfTrack
        val song = SimpleMidiReader.parseSong(midi(0, track).inputStream())
        assertEquals(listOf(SustainPedalSpan(0L, 500L), SustainPedalSpan(1000L, Long.MAX_VALUE)), song.sustainPedal)
    }
}
