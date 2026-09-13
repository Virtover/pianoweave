package com.example.ytpiano.midi

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

object SimpleMidiReader {

    fun parse(file: File): List<MidiNoteEvent> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return file.inputStream().use { parse(it) }
    }

    fun parse(inputStream: InputStream): List<MidiNoteEvent> {
        val bytes = inputStream.readBytes()
        if (bytes.size < 14) return emptyList()
        val buffer = ByteBuffer.wrap(bytes)

        // Parse MThd Header
        val magic = buffer.int
        if (magic != 0x4D546864) return emptyList() // "MThd"
        val headerLength = buffer.int
        val format = buffer.short.toInt()
        val numTracks = buffer.short.toInt()
        val division = buffer.short.toInt() and 0xFFFF

        if (division and 0x8000 != 0) {
            // SMPTE time code - fallback to a sensible division constant
            return emptyList()
        }

        val allNotes = mutableListOf<MidiNoteEvent>()

        // Look through tracks
        for (t in 0 until numTracks) {
            if (buffer.remaining() < 8) break
            val trackMagic = buffer.int
            val trackLength = buffer.int
            if (trackMagic != 0x4D54726B) { // "MTrk"
                // Skip unknown chunk
                if (buffer.remaining() >= trackLength) {
                    buffer.position(buffer.position() + trackLength)
                } else {
                    break
                }
                continue
            }

            val trackEndPosition = buffer.position() + trackLength
            val activeNotes = HashMap<Int, MutableList<Pair<Long, Int>>>() // pitch -> list of (startMs, velocity)
            
            var currentTicks = 0L
            var currentMs = 0.0
            var microsecondsPerQuarter = 500000.0 // Default 120 BPM

            fun ticksToMs(ticks: Long): Long {
                return (ticks * (microsecondsPerQuarter / (1000.0 * division))).toLong()
            }

            var runningStatus = 0

            while (buffer.position() < trackEndPosition && buffer.hasRemaining()) {
                val deltaTime = readVariableLength(buffer)
                currentTicks += deltaTime
                currentMs += (deltaTime * (microsecondsPerQuarter / (1000.0 * division)))

                val statusByte = buffer.get().toInt() and 0xFFFF
                var eventByte = statusByte
                if (statusByte and 0x80 == 0) {
                    // Running status in effect
                    eventByte = runningStatus
                    buffer.position(buffer.position() - 1) // Rewind 1 byte
                } else {
                    runningStatus = statusByte
                }

                val type = eventByte and 0xF0
                val channel = eventByte and 0x0F

                when (type) {
                    0x80 -> { // Note Off
                        val pitch = buffer.get().toInt() and 0xFF
                        val velocity = buffer.get().toInt() and 0xFF
                        val startList = activeNotes[pitch]
                        if (startList != null && startList.isNotEmpty()) {
                            val activeNote = startList.removeAt(0)
                            val noteStartMs = activeNote.first
                            val noteVelocity = activeNote.second
                            val durationMs = currentMs.toLong() - noteStartMs
                            if (durationMs >= 0) {
                                allNotes.add(MidiNoteEvent(pitch, noteStartMs, durationMs, noteVelocity))
                            }
                        }
                    }
                    0x90 -> { // Note On
                        val pitch = buffer.get().toInt() and 0xFF
                        val velocity = buffer.get().toInt() and 0xFF
                        if (velocity > 0) {
                            activeNotes.getOrPut(pitch) { mutableListOf() }.add(Pair(currentMs.toLong(), velocity))
                        } else {
                            // Note On with velocity 0 is treated as Note Off
                            val startList = activeNotes[pitch]
                            if (startList != null && startList.isNotEmpty()) {
                                val activeNote = startList.removeAt(0)
                                val noteStartMs = activeNote.first
                                val noteVelocity = activeNote.second
                                val durationMs = currentMs.toLong() - noteStartMs
                                if (durationMs >= 0) {
                                    allNotes.add(MidiNoteEvent(pitch, noteStartMs, durationMs, noteVelocity))
                                }
                            }
                        }
                    }
                    0xA0, 0xB0, 0xE0 -> { // Polyphonic Key Pressure, Control Change, Pitch Bend Change
                        buffer.get()
                        buffer.get()
                    }
                    0xC0, 0xD0 -> { // Program Change, Channel Pressure
                        buffer.get()
                    }
                    0xF0 -> { // Sysex or Meta event
                        if (eventByte == 0xFF) { // Meta Event
                            val metaType = buffer.get().toInt() and 0xFF
                            val metaLength = readVariableLength(buffer).toInt()
                            if (metaType == 0x51 && metaLength == 3) { // Set Tempo Meta Event
                                val m1 = buffer.get().toInt() and 0xFF
                                val m2 = buffer.get().toInt() and 0xFF
                                val m3 = buffer.get().toInt() and 0xFF
                                val tempoVal = (m1 shl 16) or (m2 shl 8) or m3
                                microsecondsPerQuarter = tempoVal.toDouble()
                            } else {
                                // Skip other meta events
                                if (buffer.remaining() >= metaLength) {
                                    buffer.position(buffer.position() + metaLength)
                                } else {
                                    break
                                }
                            }
                        } else if (eventByte == 0xF0 || eventByte == 0xF7) { // Sysex chunk
                            val sysexLength = readVariableLength(buffer).toInt()
                            if (buffer.remaining() >= sysexLength) {
                                buffer.position(buffer.position() + sysexLength)
                            } else {
                                break
                            }
                        }
                    }
                }
            }
            
            // Guarantee position is advanced correctly to track end chunk bound
            if (buffer.position() != trackEndPosition && trackEndPosition <= buffer.limit()) {
                buffer.position(trackEndPosition)
            }
        }

        return allNotes.sortedWith(compareBy({ it.startMs }, { it.pitch }))
    }

    private fun readVariableLength(buffer: ByteBuffer): Long {
        var value = 0L
        var byte: Int
        do {
            if (!buffer.hasRemaining()) break
            byte = buffer.get().toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F).toLong()
        } while (byte and 0x80 != 0)
        return value
    }
}
