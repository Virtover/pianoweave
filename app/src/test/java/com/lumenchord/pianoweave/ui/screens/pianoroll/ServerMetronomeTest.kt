package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiSong
import com.lumenchord.pianoweave.midi.SimpleMidiReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The metronome on MIDI files shaped like the transcription server's: 960 ticks per beat, a set_tempo at
 * every beat, time signatures (x/4) only where the bar length changes, or, when no beat was found, a single
 * 120 BPM tempo in 4/4. Each file is parsed, then played through [ClickScheduler] on a simulated playback
 * clock, and the clicks are checked in seconds against the tempo map walked by hand here.
 */
class ServerMetronomeTest {

    // --- A tiny MIDI writer: events at absolute ticks, one track ---

    private class Event(val tick: Long, val bytes: IntArray)

    private fun tempoEvent(tick: Long, microsPerQuarter: Int) =
        Event(tick, intArrayOf(0xFF, 0x51, 0x03, microsPerQuarter ushr 16 and 0xFF, microsPerQuarter ushr 8 and 0xFF, microsPerQuarter and 0xFF))

    private fun meterEvent(tick: Long, beatsPerBar: Int) = Event(tick, intArrayOf(0xFF, 0x58, 0x04, beatsPerBar, 2, 24, 8))

    private fun note(beat: Int, pitch: Int = 60) = listOf(
        Event(beat * TPB, intArrayOf(0x90, pitch, 80)),
        Event(beat * TPB + TPB / 2, intArrayOf(0x80, pitch, 0))
    )

    private fun midiFile(events: List<Event>): ByteArray {
        val track = ByteArrayOutputStream()
        var lastTick = 0L
        for (event in events.sortedBy { it.tick } + Event(events.maxOf { it.tick }, intArrayOf(0xFF, 0x2F, 0x00))) {
            var delta = event.tick - lastTick
            lastTick = event.tick
            // Variable-length delta time
            val groups = mutableListOf((delta and 0x7F).toInt())
            delta = delta shr 7
            while (delta > 0) {
                groups += ((delta and 0x7F) or 0x80).toInt()
                delta = delta shr 7
            }
            groups.reversed().forEach(track::write)
            event.bytes.forEach(track::write)
        }
        val out = ByteArrayOutputStream()
        fun int32(v: Int) = listOf(v ushr 24, v ushr 16, v ushr 8, v).forEach { out.write(it and 0xFF) }
        fun int16(v: Int) = listOf(v ushr 8, v).forEach { out.write(it and 0xFF) }
        out.write("MThd".toByteArray()); int32(6); int16(0); int16(1); int16(TPB.toInt())
        out.write("MTrk".toByteArray()); int32(track.size()); out.write(track.toByteArray())
        return out.toByteArray()
    }

    /** A server tempo map: one set_tempo per beat with that beat's length, and the bar lengths at their beats. */
    private fun serverFile(beatLengthsMs: List<Int>, meters: Map<Int, Int>, noteBeats: List<Int>): MidiSong {
        val events = beatLengthsMs.mapIndexed { beat, ms -> tempoEvent(beat * TPB, ms * 1000) } +
            meters.map { (beat, beatsPerBar) -> meterEvent(beat * TPB, beatsPerBar) } +
            noteBeats.flatMap { note(it) }
        return SimpleMidiReader.parseSong(midiFile(events).inputStream())
    }

    /** The time of every beat, by adding up the beat lengths from tick 0. */
    private fun expectedSeconds(beatLengthsMs: List<Int>, count: Int): List<Double> =
        (0 until count).map { beat -> beatLengthsMs.take(beat).sum() / 1000.0 }

    /**
     * Plays [beats] from [fromMs] at [speed] on a simulated clock stepped every 10 ms, as the playback loop
     * does, and returns each click's beat with the real time (seconds since play) it is scheduled for.
     */
    private fun play(beats: List<Beat>, speed: Float = 1f, fromMs: Long = 0L, clickFromMs: Long = Long.MIN_VALUE): List<Pair<Beat, Double>> {
        val scheduler = ClickScheduler(beats, clickFromMs)
        val clock = PlaybackClock(headMs = fromMs.toDouble(), anchorNanos = 0L, speed = speed)
        val end = (beats.last().timeMs - fromMs) / speed + 100
        return (0..end.toLong() step 10).flatMap { realMs ->
            scheduler.due(clock.headAt(realMs * 1_000_000L).toLong(), (40 * speed).toLong())
                .map { it to clock.nanosAt(it.timeMs) / 1e9 }
        }
    }

    private fun assertSeconds(expected: List<Double>, actual: List<Double>) {
        assertEquals("click count", expected.size, actual.size)
        // The reader keeps whole milliseconds
        expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals("click $i", e, a, 0.0015) }
    }

    private fun accented(clicks: List<Pair<Beat, Double>>) = clicks.indices.filter { clicks[it].first.isBarStart }

    @Test
    fun rubatoClicksFollowEveryTempoChange() {
        val lengths = listOf(500, 520, 480, 610, 450, 700, 400, 530, 560)
        val song = serverFile(lengths, meters = mapOf(0 to 4), noteBeats = (0..8).toList())
        val clicks = play(song.beats)
        // Not 120 BPM (the first tempo would give 0.5 s steps) and not any other single tempo
        assertSeconds(expectedSeconds(lengths, 9), clicks.map { it.second })
        assertEquals(listOf(0, 4, 8), accented(clicks))
    }

    @Test
    fun pickupBarLeadsIntoTheFirstDownbeat() {
        // One beat of 1/4, then 4/4
        val lengths = listOf(450, 470, 480, 500, 490, 510, 520, 500, 480, 470)
        val song = serverFile(lengths, meters = mapOf(0 to 1, 1 to 4), noteBeats = (0..9).toList())
        val clicks = play(song.beats)
        assertSeconds(expectedSeconds(lengths, 10), clicks.map { it.second })
        // The pickup is the end of a bar, not a downbeat; bars of four start after it
        assertEquals(listOf(1, 5, 9), accented(clicks))
        // While in the pickup the meter shown is the bar it leads into
        assertEquals("4/4", beatInfoAt(song.beats, 0L)?.meter)
    }

    @Test
    fun anOddBarRestartsTheCount() {
        // 4/4, one bar of 2/4, then 4/4 again
        val lengths = List(14) { 400 + 10 * (it % 3) }
        val song = serverFile(lengths, meters = mapOf(0 to 4, 4 to 2, 6 to 4), noteBeats = (0..13).toList())
        val clicks = play(song.beats)
        assertSeconds(expectedSeconds(lengths, 14), clicks.map { it.second })
        assertEquals(listOf(0, 4, 6, 10), accented(clicks))
        assertEquals("2/4", beatInfoAt(song.beats, song.beats[5].timeMs)?.meter)
        assertEquals("4/4", beatInfoAt(song.beats, song.beats[7].timeMs)?.meter)
    }

    @Test
    fun clicksGoOnThroughAPause() {
        // Four beats of music, a 12-beat bar of silence at about the same tempo, then music again
        val lengths = List(4) { 600 } + List(12) { 590 + 5 * (it % 4) } + List(4) { 610 }
        val song = serverFile(lengths, meters = mapOf(0 to 4, 4 to 12, 16 to 4), noteBeats = (0..3) + (16..19))
        val clicks = play(song.beats)
        assertSeconds(expectedSeconds(lengths, 20), clicks.map { it.second })
        assertEquals(listOf(0, 4, 16), accented(clicks))
        assertEquals(12, beatInfoAt(song.beats, song.beats[10].timeMs)?.beatsPerBar)
    }

    @Test
    fun aFileWithOneTempoHasNoBeatGrid() {
        // The server's "no beat found" file: a single 120 BPM tempo and 4/4, notes off any grid
        val events = listOf(tempoEvent(0L, 500_000), meterEvent(0L, 4)) +
            listOf(0, 3, 7, 10).flatMap { note(it) } +
            listOf(Event(1234L, intArrayOf(0x90, 64, 80)), Event(2000L, intArrayOf(0x80, 64, 0)))
        val song = SimpleMidiReader.parseSong(midiFile(events).inputStream())
        assertTrue(song.notes.isNotEmpty())
        // No grid: the metronome is greyed out with "No beat information for this piece"
        assertTrue(song.beats.isEmpty())
        assertEquals(null, beatInfoAt(song.beats, 0L))
        assertEquals(null, countInFor(song.beats, 0L))
    }

    @Test
    fun aTempoMapIsFoundEvenWhenItsFirstTempoIs120() {
        // Starting at exactly 120 BPM must not look like the "no beat" file
        val lengths = listOf(500, 500, 540, 560, 580)
        val song = serverFile(lengths, meters = mapOf(0 to 4), noteBeats = (0..4).toList())
        assertSeconds(expectedSeconds(lengths, 5), play(song.beats).map { it.second })
    }

    @Test
    fun theClicksFollowThePlaybackSpeed() {
        val lengths = listOf(500, 520, 480, 610, 450)
        val song = serverFile(lengths, meters = mapOf(0 to 4), noteBeats = (0..4).toList())
        val clicks = play(song.beats, speed = 0.5f)
        assertSeconds(expectedSeconds(lengths, 5).map { it * 2 }, clicks.map { it.second })
    }

    @Test
    fun theLeadInCanStaySilent() {
        // Three beats of silence the server added before the music, then notes from beat 3
        val lengths = listOf(700, 700, 700, 500, 500, 500, 500)
        val song = serverFile(lengths, meters = mapOf(0 to 3, 3 to 4), noteBeats = (3..6).toList())
        val all = play(song.beats)
        assertEquals(7, all.size)
        val silentLeadIn = play(song.beats, clickFromMs = firstClickMs(song.notes)!!)
        assertSeconds(expectedSeconds(lengths, 7).drop(3), silentLeadIn.map { it.second })
        assertTrue(silentLeadIn.first().first.isBarStart)
    }

    @Test
    fun theBpmShownIsTheMedianOfTheLastFourBeats() {
        // One rushed beat (300 ms) among 500s doesn't move it
        val lengths = listOf(500, 500, 500, 300, 500, 500, 500, 500)
        val song = serverFile(lengths, meters = mapOf(0 to 4), noteBeats = (0..8).toList())
        (0..7).forEach { assertEquals("beat $it", 120, beatInfoAt(song.beats, song.beats[it].timeMs)?.bpm) }
    }

    private companion object {
        const val TPB = 960L
    }
}
