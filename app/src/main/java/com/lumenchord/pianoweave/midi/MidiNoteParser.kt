package com.lumenchord.pianoweave.midi

/**
 * Streaming parser that turns raw MIDI bytes into note on / off calls.
 *
 * Keeps state between [feed] calls, so messages split across packets still parse, and supports running
 * status (a status byte followed by several data pairs), which many keyboards and BLE MIDI use for chords.
 * Real-time bytes (clock, active sensing) may appear anywhere and are ignored; SysEx is skipped.
 */
class MidiNoteParser(
    private val onNoteOn: (pitch: Int, velocity: Int) -> Unit,
    private val onNoteOff: (pitch: Int) -> Unit
) {
    private var runningStatus = 0
    private var inSysEx = false
    private val data = IntArray(2)
    private var dataCount = 0

    fun feed(msg: ByteArray, offset: Int, count: Int) {
        for (i in offset until offset + count) feedByte(msg[i].toInt() and 0xFF)
    }

    private fun feedByte(b: Int) {
        when {
            b >= 0xF8 -> Unit // Real-time: doesn't affect running status
            b == 0xF0 -> { inSysEx = true; runningStatus = 0 }
            b == 0xF7 -> inSysEx = false
            b >= 0xF1 -> { inSysEx = false; runningStatus = 0 } // System common clears running status
            b >= 0x80 -> { inSysEx = false; runningStatus = b; dataCount = 0 }
            inSysEx || runningStatus == 0 -> Unit // Stray data byte
            else -> {
                data[dataCount++] = b
                if (dataCount == dataLength(runningStatus)) {
                    dataCount = 0
                    dispatch(runningStatus and 0xF0)
                }
            }
        }
    }

    private fun dataLength(status: Int): Int = when (status and 0xF0) {
        0xC0, 0xD0 -> 1
        else -> 2
    }

    private fun dispatch(type: Int) {
        when (type) {
            0x90 -> if (data[1] > 0) onNoteOn(data[0], data[1]) else onNoteOff(data[0])
            0x80 -> onNoteOff(data[0])
        }
    }
}
