package com.lumenchord.pianoweave.midi

import java.util.UUID

/**
 * The Bluetooth LE MIDI wire format (MIDI Association "MIDI over Bluetooth LE" spec): every packet starts
 * with a header byte, and every MIDI message in it is preceded by a timestamp byte. Both carry the top bit
 * so they can't be mistaken for MIDI data; the 13-bit millisecond timestamp is split between them.
 */
object BleMidiPacket {
    val SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
    /** The standard Client Characteristic Configuration descriptor, written by a central to subscribe. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Wraps one MIDI message (e.g. note on: status, pitch, velocity) into a BLE MIDI packet. */
    fun encode(message: ByteArray, timeMs: Long = System.currentTimeMillis()): ByteArray {
        val ts = (timeMs and 0x1FFF).toInt()
        val header = (0x80 or (ts shr 7)).toByte()
        val stamp = (0x80 or (ts and 0x7F)).toByte()
        return byteArrayOf(header, stamp) + message
    }

    /**
     * Several MIDI messages in one packet (one header, a timestamp before each), e.g. a whole chord. A
     * SysEx message's closing F7 gets a timestamp of its own, as the BLE MIDI spec asks.
     */
    fun encodeAll(messages: List<ByteArray>, timeMs: Long = System.currentTimeMillis()): ByteArray {
        val ts = (timeMs and 0x1FFF).toInt()
        val stamp = (0x80 or (ts and 0x7F)).toByte()
        val out = ArrayList<Byte>(1 + messages.sumOf { encodedSize(it) })
        out.add((0x80 or (ts shr 7)).toByte())
        messages.forEach { message ->
            out.add(stamp)
            if (isSysEx(message)) {
                for (i in 0 until message.size - 1) out.add(message[i])
                out.add(stamp)
                out.add(message.last())
            } else {
                message.forEach(out::add)
            }
        }
        return out.toByteArray()
    }

    /** Bytes [message] takes in a packet, its timestamp(s) included. */
    fun encodedSize(message: ByteArray): Int = message.size + if (isSysEx(message)) 2 else 1

    private fun isSysEx(message: ByteArray) =
        message.size >= 2 && message[0] == 0xF0.toByte() && message.last() == 0xF7.toByte()

    /**
     * Strips the header and timestamp bytes and returns the plain MIDI bytes, ready for [MidiNoteParser]
     * (which handles running status). A timestamp byte is told apart from a status byte by position: it
     * comes right after the header, or where a status byte could follow a complete message.
     */
    fun decode(packet: ByteArray): ByteArray {
        if (packet.size < 2 || packet[0].toInt() and 0x80 == 0) return ByteArray(0)
        val out = ArrayList<Byte>(packet.size)
        var i = 1
        var expectTimestamp = true
        while (i < packet.size) {
            val b = packet[i].toInt() and 0xFF
            when {
                expectTimestamp && b and 0x80 != 0 -> expectTimestamp = false // timestamp low byte
                // A status after its timestamp. One that is a whole message by itself (F7 ending a SysEx,
                // tune request F6, real-time F8-FF such as clock) is followed by a timestamp again; reading
                // that as a status once swallowed the next note (a clock byte between notes lost C5)
                b and 0x80 != 0 -> { out.add(b.toByte()); expectTimestamp = b == 0xF7 || b >= 0xF4 }
                else -> {
                    out.add(b.toByte())
                    // After data, the next byte with the top bit set is a timestamp (then a status, or
                    // running-status data)
                    expectTimestamp = true
                }
            }
            i++
        }
        return out.toByteArray()
    }
}
