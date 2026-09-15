package com.example.ytpiano.midi

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SimpleMidiReader {

    fun parse(file: File): List<MidiNoteEvent> {
        if (!file.exists() || file.length() == 0L) {
            return emptyList()
        }

        return file.inputStream().use {
            parse(it)
        }
    }

    fun parse(inputStream: InputStream): List<MidiNoteEvent> {
        val bytes = inputStream.readBytes()

        if (bytes.size < 14) {
            return emptyList()
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)

        /*
         * MThd
         */
        if (buffer.intSafe() != 0x4D546864) {
            return emptyList()
        }

        val headerLength = buffer.intSafe()
            ?: return emptyList()

        if (headerLength < 6) {
            return emptyList()
        }

        if (buffer.remaining() < 6) {
            return emptyList()
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
            return emptyList()
        }

        if (division == 0) {
            return emptyList()
        }

        /*
         * Some MIDI files can theoretically contain a header
         * larger than the standard 6-byte payload.
         */
        val extraHeaderBytes = headerLength - 6

        if (extraHeaderBytes > 0) {
            if (buffer.remaining() < extraHeaderBytes) {
                return emptyList()
            }

            buffer.position(
                buffer.position() + extraHeaderBytes
            )
        }

        val allNotes = mutableListOf<MidiNoteEvent>()

        /*
         * MIDI format 2 contains independent sequences.
         *
         * For our piano-learning use case, format 0 and 1
         * are the relevant ones.
         */
        if (format !in 0..1) {
            return emptyList()
        }

        for (trackIndex in 0 until numTracks) {
            if (buffer.remaining() < 8) {
                break
            }

            if (buffer.intSafe() != 0x4D54726B) {
                return allNotes.sortedWith(
                    compareBy(
                        { it.startMs },
                        { it.pitch }
                    )
                )
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
                division = division,
                output = allNotes
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

        return allNotes.sortedWith(
            compareBy(
                { it.startMs },
                { it.pitch }
            )
        )
    }

    private fun parseTrack(
        buffer: ByteBuffer,
        trackEnd: Int,
        division: Int,
        output: MutableList<MidiNoteEvent>
    ) {
        var currentTicks = 0L
        var currentMs = 0.0

        /*
         * MIDI starts with the conventional 120 BPM
         * tempo if no tempo event has appeared yet.
         */
        var microsecondsPerQuarter = 500_000.0

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

            currentMs +=
                deltaTime *
                        microsecondsPerQuarter /
                        (1000.0 * division)

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
                        microsecondsPerQuarter =
                            tempo.toDouble()
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
                        endMs = currentMs.toLong(),
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
                            endMs = currentMs.toLong(),
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
                                    startMs =
                                        currentMs.toLong(),
                                    velocity = velocity
                                )
                            )
                    }
                }

                0xA0,
                0xB0,
                0xE0 -> {
                    /*
                     * Polyphonic pressure,
                     * Control Change,
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
        endMs: Long,
        activeNotes:
        HashMap<Int, MutableList<ActiveNote>>,
        output: MutableList<MidiNoteEvent>
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

        val duration =
            endMs - note.startMs

        if (duration >= 0) {
            output.add(
                MidiNoteEvent(
                    pitch = pitch,
                    startMs = note.startMs,
                    durationMs = duration,
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
        val startMs: Long,
        val velocity: Int
    )
}