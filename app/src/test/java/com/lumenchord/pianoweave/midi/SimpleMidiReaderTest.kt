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

    private fun timeSignature(numerator: Int, denominatorPower: Int) =
        intArrayOf(0xFF, 0x58, 0x04, numerator, denominatorPower, 24, 8)

    /** A note from tick 0 to tick 2400 (five quarters; 2400 is the variable-length 0x92 0x60). */
    private val fiveQuarterNote = intArrayOf(0x00, 0x90, 60, 100, 0x92, 0x60, 0x80, 60, 0) + endOfTrack

    private fun beats(vararg tracks: IntArray) = SimpleMidiReader.parseSong(midi(1, *tracks).inputStream()).beats

    @Test
    fun tempoMapBeatsFollowTheTempoAndBarsTheTimeSignature() {
        // Like the server's tempo map: a tempo change at beats, 3/4 from the start
        val tempoTrack = intArrayOf(0x00) + tempo(500_000) + intArrayOf(0x00) + timeSignature(3, 2) +
            intArrayOf(0x83, 0x60) + tempo(1_000_000) + intArrayOf(0x83, 0x60) + tempo(250_000) + endOfTrack
        val beats = beats(tempoTrack, fiveQuarterNote)
        assertEquals(listOf(0L, 500L, 1500L, 1750L, 2000L, 2250L), beats.map { it.timeMs })
        assertEquals(listOf(true, false, false, true, false, false), beats.map { it.isBarStart })
    }

    @Test
    fun pickupIsNoBarStartAndTheNextSignatureStartsABar() {
        // The server writes a pickup as a short first bar: 1/4, then 3/4 from the second beat. The pickup
        // beat leads up to the first downbeat, so it is no bar start
        val tempoTrack = intArrayOf(0x00) + timeSignature(1, 2) + intArrayOf(0x83, 0x60) + timeSignature(3, 2) + endOfTrack
        val beats = beats(tempoTrack, fiveQuarterNote)
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L), beats.map { it.timeMs })
        assertEquals(listOf(false, true, false, false, true, false), beats.map { it.isBarStart })
    }

    @Test
    fun prettyMidiDefaultsAreNoBeatGrid() {
        // Transkun's output when the server keeps it: one 120 BPM tempo and 4/4, both at tick 0
        val tempoTrack = intArrayOf(0x00) + tempo(500_000) + intArrayOf(0x00) + timeSignature(4, 2) + endOfTrack
        assertEquals(emptyList<Beat>(), beats(tempoTrack, fiveQuarterNote))
        assertEquals(emptyList<Beat>(), beats(fiveQuarterNote))
    }

    @Test
    fun sixEightBeatsAreEighths() {
        val tempoTrack = intArrayOf(0x00) + timeSignature(6, 3) + endOfTrack
        val beats = beats(tempoTrack, fiveQuarterNote)
        assertEquals((0L..2500L step 250L).toList(), beats.map { it.timeMs })
        assertEquals(listOf(0, 6), beats.indices.filter { beats[it].isBarStart })
    }
}
