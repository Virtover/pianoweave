package com.lumenchord.pianoweave.midi

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SimpleMidiReader {

    private val EMPTY_SONG = MidiSong(emptyList(), emptyList())

    fun parse(file: File): List<MidiNoteEvent> = parseSong(file).notes

    fun parse(inputStream: InputStream): List<MidiNoteEvent> = parseSong(inputStream).notes

    fun parseSong(file: File): MidiSong {
        if (!file.exists() || file.length() == 0L) {
            return EMPTY_SONG
        }

        return file.inputStream().use {
            parseSong(it)
        }
    }

    fun parseSong(inputStream: InputStream): MidiSong {
        val bytes = inputStream.readBytes()

        if (bytes.size < 14) {
            return EMPTY_SONG
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)

        /*
         * MThd
         */
        if (buffer.intSafe() != 0x4D546864) {
            return EMPTY_SONG
        }

        val headerLength = buffer.intSafe()
            ?: return EMPTY_SONG

        if (headerLength < 6) {
            return EMPTY_SONG
        }

        if (buffer.remaining() < 6) {
            return EMPTY_SONG
        }

        val format = buffer.short.toInt() and 0xFFFF
        val numTracks = buffer.short.toInt() and 0xFFFF
        val division = buffer.short.toInt() and 0xFFFF

        /*
         * SMPTE timing.
         *
         * The generated MIDI should normally use PPQN,
         * which is what this reader supports.
         */
        if (division and 0x8000 != 0) {
            return EMPTY_SONG
        }

        if (division == 0) {
            return EMPTY_SONG
        }

        /*
         * Some MIDI files can theoretically contain a header
         * larger than the standard 6-byte payload.
         */
        val extraHeaderBytes = headerLength - 6

        if (extraHeaderBytes > 0) {
            if (buffer.remaining() < extraHeaderBytes) {
                return EMPTY_SONG
            }

            buffer.position(
                buffer.position() + extraHeaderBytes
            )
        }

        // Notes are read in ticks and timed at the end with one tempo map for the whole file: in format 1
        // the tempo events sit in the first track and apply to every track
        val tickNotes = mutableListOf<TickNote>()
        val tempoChanges = mutableListOf<Pair<Long, Int>>()
        // Sustain pedal (CC64) changes as tick to down; a piano has one pedal, whichever track holds it
        val pedalChanges = mutableListOf<Pair<Long, Boolean>>()

        /*
         * MIDI format 2 contains independent sequences.
         *
         * For our piano-learning use case, format 0 and 1
         * are the relevant ones.
         */
        if (format !in 0..1) {
            return EMPTY_SONG
        }

        for (trackIndex in 0 until numTracks) {
            if (buffer.remaining() < 8) {
                break
            }

            if (buffer.intSafe() != 0x4D54726B) {
                break
            }

            val trackLength = buffer.intSafe()
                ?: break

            if (trackLength < 0) {
                break
            }

            if (trackLength > buffer.remaining()) {
                /*
                 * Broken track length.
                 *
                 * Do not attempt to read beyond the file.
                 */
                break
            }

            val trackEnd =
                buffer.position() + trackLength

            parseTrack(
                buffer = buffer,
                trackEnd = trackEnd,
                output = tickNotes,
                tempoChanges = tempoChanges,
                pedalChanges = pedalChanges
            )

            /*
             * Always move exactly to the end of the track.
             */
            if (trackEnd <= buffer.limit()) {
                buffer.position(trackEnd)
            } else {
                break
            }
        }

        val tempoMap = TempoMap(tempoChanges, division)

        val sustainPedal = mutableListOf<SustainPedalSpan>()
        var pedalDownSinceTick: Long? = null
        for ((tick, down) in pedalChanges.sortedBy { it.first }) {
            val since = pedalDownSinceTick
            if (down && since == null) {
                pedalDownSinceTick = tick
            } else if (!down && since != null) {
                sustainPedal += SustainPedalSpan(tempoMap.toMs(since), tempoMap.toMs(tick))
                pedalDownSinceTick = null
            }
        }
        pedalDownSinceTick?.let { sustainPedal += SustainPedalSpan(tempoMap.toMs(it), Long.MAX_VALUE) }

        val notes = tickNotes
            .map { note ->
                val startMs = tempoMap.toMs(note.startTick)
                MidiNoteEvent(
                    pitch = note.pitch,
                    startMs = startMs,
                    durationMs = tempoMap.toMs(note.endTick) - startMs,
                    velocity = note.velocity
                )
            }
            .sortedWith(
                compareBy(
                    { it.startMs },
                    { it.pitch }
                )
            )
        return MidiSong(notes, sustainPedal)
    }

    private fun parseTrack(
        buffer: ByteBuffer,
        trackEnd: Int,
        output: MutableList<TickNote>,
        tempoChanges: MutableList<Pair<Long, Int>>,
        pedalChanges: MutableList<Pair<Long, Boolean>>
    ) {
        var currentTicks = 0L

        /*
         * Running status is only valid for channel messages.
         */
        var runningStatus = 0

        /*
         * pitch -> queue of active notes.
         *
         * The queue is important because malformed or unusual
         * MIDI can contain overlapping notes of the same pitch.
         */
        val activeNotes =
            HashMap<Int, MutableList<ActiveNote>>()

        while (
            buffer.position() < trackEnd
        ) {
            /*
             * Delta time
             */
            val deltaTime =
                readVariableLength(
                    buffer,
                    trackEnd
                ) ?: break

            currentTicks += deltaTime

            /*
             * We need at least one event/status byte.
             */
            if (buffer.position() >= trackEnd) {
                break
            }

            val rawStatus =
                buffer.get().toInt() and 0xFF

            val status: Int

            if (rawStatus < 0x80) {
                /*
                 * Running status.
                 *
                 * The byte we just consumed is actually the
                 * first data byte, so put it back.
                 */
                if (runningStatus == 0) {
                    break
                }

                status = runningStatus

                buffer.position(
                    buffer.position() - 1
                )
            } else {
                status = rawStatus

                /*
                 * System messages don't establish running status.
                 */
                if (status in 0x80..0xEF) {
                    runningStatus = status
                }
            }

            /*
             * End of track.
             */
            if (status == 0xFF) {
                if (
                    buffer.position() >= trackEnd
                ) {
                    break
                }

                val metaType =
                    buffer.get().toInt() and 0xFF

                val length =
                    readVariableLength(
                        buffer,
                        trackEnd
                    ) ?: break

                if (
                    length > Int.MAX_VALUE
                ) {
                    break
                }

                val metaLength = length.toInt()

                if (
                    metaLength >
                    trackEnd - buffer.position()
                ) {
                    break
                }

                if (
                    metaType == 0x51 &&
                    metaLength == 3
                ) {
                    val b1 =
                        buffer.get().toInt() and 0xFF

                    val b2 =
                        buffer.get().toInt() and 0xFF

                    val b3 =
                        buffer.get().toInt() and 0xFF

                    val tempo =
                        (b1 shl 16) or
                                (b2 shl 8) or
                                b3

                    if (tempo > 0) {
                        tempoChanges += currentTicks to tempo
                    }
                } else {
                    buffer.position(
                        buffer.position() +
                                metaLength
                    )
                }

                /*
                 * FF 2F 00 = End Of Track.
                 */
                if (metaType == 0x2F) {
                    break
                }

                continue
            }

            /*
             * SysEx
             */
            if (
                status == 0xF0 ||
                status == 0xF7
            ) {
                val length =
                    readVariableLength(
                        buffer,
                        trackEnd
                    ) ?: break

                if (
                    length >
                    trackEnd - buffer.position()
                ) {
                    break
                }

                buffer.position(
                    buffer.position() +
                            length.toInt()
                )

                continue
            }

            /*
             * MIDI channel message
             */
            val type = status and 0xF0

            when (type) {
                0x80 -> {
                    /*
                     * Note Off:
                     *
                     * status
                     * pitch
                     * velocity
                     */
                    val pitch =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    val velocity =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    finishNote(
                        pitch = pitch,
                        endTick = currentTicks,
                        activeNotes = activeNotes,
                        output = output
                    )
                }

                0x90 -> {
                    /*
                     * Note On:
                     *
                     * status
                     * pitch
                     * velocity
                     */
                    val pitch =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    val velocity =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    if (velocity == 0) {
                        /*
                         * MIDI convention:
                         *
                         * Note On velocity 0 == Note Off
                         */
                        finishNote(
                            pitch = pitch,
                            endTick = currentTicks,
                            activeNotes = activeNotes,
                            output = output
                        )
                    } else {
                        activeNotes
                            .getOrPut(pitch) {
                                mutableListOf()
                            }
                            .add(
                                ActiveNote(
                                    startTick =
                                        currentTicks,
                                    velocity = velocity
                                )
                            )
                    }
                }

                0xB0 -> {
                    /*
                     * Control Change:
                     *
                     * status
                     * controller
                     * value
                     *
                     * Only the sustain pedal (64) is kept; 64 and up is down.
                     */
                    val controller =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    val value =
                        readByte(
                            buffer,
                            trackEnd
                        ) ?: break

                    if (controller == 64) {
                        pedalChanges += currentTicks to (value >= 64)
                    }
                }

                0xA0,
                0xE0 -> {
                    /*
                     * Polyphonic pressure,
                     * Pitch Bend.
                     */
                    if (
                        !skipBytes(
                            buffer,
                            trackEnd,
                            2
                        )
                    ) {
                        break
                    }
                }

                0xC0,
                0xD0 -> {
                    /*
                     * Program Change,
                     * Channel Pressure.
                     */
                    if (
                        !skipBytes(
                            buffer,
                            trackEnd,
                            1
                        )
                    ) {
                        break
                    }
                }

                else -> {
                    /*
                     * Unknown system/channel event.
                     *
                     * Stop parsing this track rather than
                     * risking desynchronization.
                     */
                    break
                }
            }
        }
    }

    private fun finishNote(
        pitch: Int,
        endTick: Long,
        activeNotes:
        HashMap<Int, MutableList<ActiveNote>>,
        output: MutableList<TickNote>
    ) {
        val notes = activeNotes[pitch]
            ?: return

        if (notes.isEmpty()) {
            return
        }

        /*
         * FIFO is appropriate for overlapping instances of
         * the same pitch.
         */
        val note = notes.removeAt(0)

        if (endTick >= note.startTick) {
            output.add(
                TickNote(
                    pitch = pitch,
                    startTick = note.startTick,
                    endTick = endTick,
                    velocity = note.velocity
                )
            )
        }

        if (notes.isEmpty()) {
            activeNotes.remove(pitch)
        }
    }

    private fun readByte(
        buffer: ByteBuffer,
        trackEnd: Int
    ): Int? {
        if (buffer.position() >= trackEnd) {
            return null
        }

        if (!buffer.hasRemaining()) {
            return null
        }

        return buffer.get().toInt() and 0xFF
    }

    private fun skipBytes(
        buffer: ByteBuffer,
        trackEnd: Int,
        count: Int
    ): Boolean {
        if (count < 0) {
            return false
        }

        if (
            buffer.position() + count >
            trackEnd
        ) {
            return false
        }

        if (
            buffer.position() + count >
            buffer.limit()
        ) {
            return false
        }

        buffer.position(
            buffer.position() + count
        )

        return true
    }

    private fun readVariableLength(
        buffer: ByteBuffer,
        trackEnd: Int
    ): Long? {
        var value = 0L

        repeat(4) {
            if (buffer.position() >= trackEnd) {
                return null
            }

            if (!buffer.hasRemaining()) {
                return null
            }

            val byte =
                buffer.get().toInt() and 0xFF

            value =
                (value shl 7) or
                        (byte and 0x7F).toLong()

            if (byte and 0x80 == 0) {
                return value
            }
        }

        /*
         * MIDI variable-length quantities are at most
         * four bytes.
         */
        return null
    }

    private fun ByteBuffer.intSafe(): Int? {
        if (remaining() < 4) {
            return null
        }

        return getInt()
    }

    private data class ActiveNote(
        val startTick: Long,
        val velocity: Int
    )

    private data class TickNote(
        val pitch: Int,
        val startTick: Long,
        val endTick: Long,
        val velocity: Int
    )

    /**
     * Ticks to milliseconds under the file's tempo changes ([tempoChanges]: tick to microseconds per
     * quarter note). Until the first one, MIDI's conventional 120 BPM applies.
     */
    private class TempoMap(tempoChanges: List<Pair<Long, Int>>, private val division: Int) {
        private val ticks = mutableListOf(0L)
        private val startMs = mutableListOf(0.0)
        private val tempos = mutableListOf(500_000.0)

        init {
            // Stable sort: of several changes at one tick, the last one read wins
            for ((tick, tempo) in tempoChanges.sortedBy { it.first }) {
                val last = ticks.lastIndex
                if (tick == ticks[last]) {
                    tempos[last] = tempo.toDouble()
                } else {
                    startMs += msAt(last, tick)
                    ticks += tick
                    tempos += tempo.toDouble()
                }
            }
        }

        private fun msAt(segment: Int, tick: Long): Double =
            startMs[segment] + (tick - ticks[segment]) * tempos[segment] / (1000.0 * division)

        fun toMs(tick: Long): Long {
            var segment = ticks.binarySearch(tick)
            if (segment < 0) segment = -segment - 2
            return msAt(segment.coerceAtLeast(0), tick).toLong()
        }
    }
}