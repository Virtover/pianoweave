package com.lumenchord.pianoweave.midi

import android.os.SystemClock

/**
 * Mirrors the piano roll to a connected computer, so a page there can show the song's notes falling
 * towards its keys ahead of time, and stop with the app in wait mode. Plain MIDI only says "this note,
 * now"; falling notes need what's coming, so this sends it ahead, in System Exclusive messages under the
 * MIDI spec's non-commercial manufacturer ID (0x7D), which no hardware reacts to:
 *
 *   F0 7D 50 57 <command> <data> F7          ("PW" = Piano Weave), numbers in 7-bit groups, high first
 *   01 reset      head(4)                    forget all notes; the song is at [head] ms
 *   02 notes      (start(4) length(3) pitch velocity)*   notes of the song, sent ~6 s before they're due
 *   03 transport  head(4) flags speed(2)     flags: 1 playing, 2 waiting (wait mode); speed x100
 *   04 end                                   the song was closed
 *
 * Times are song milliseconds (already transposed notes, as the app plays them). Sent only while a
 * device or computer is listening and "Send the song" is on.
 */
object RollSync {
    private const val LOOKAHEAD_MS = 6_000L
    private const val TRANSPORT_EVERY_MS = 200L
    private val HEADER = byteArrayOf(0xF0.toByte(), 0x7D, 0x50, 0x57)

    private var notes: List<MidiNoteEvent> = emptyList()
    private var nextIndex = 0
    private var syncedGeneration = -1
    private var lastHead = -1L
    private var lastTickAt = 0L
    private var lastTransport: List<Int>? = null
    private var lastTransportAt = 0L

    /** A song opened (or its notes changed, e.g. transposed): the other side starts over. */
    fun startSong(songNotes: List<MidiNoteEvent>) {
        notes = songNotes.sortedBy { it.startMs }
        syncedGeneration = -1
        lastHead = -1L
    }

    /** The song closed: the other side clears its falling notes. */
    fun endSong() {
        if (notes.isNotEmpty() && MidiInputManager.canSendSync()) send(4, ByteArray(0))
        notes = emptyList()
        syncedGeneration = -1
    }

    /** Called ~20 times a second while the piano roll is open. */
    fun tick(headMs: Long, isPlaying: Boolean, isWaiting: Boolean, speed: Float) {
        if (notes.isEmpty() || !MidiInputManager.canSendSync()) {
            syncedGeneration = -1 // when someone listens again, they get everything from the start
            return
        }
        val now = SystemClock.uptimeMillis()
        val generation = MidiInputManager.listenerGeneration
        // A jump the clock can't explain (seek, loop back, restart) means the notes sent no longer fit
        val elapsed = (now - lastTickAt).coerceAtLeast(0L)
        val jumped = lastHead >= 0 && (headMs < lastHead - 20 || headMs > lastHead + (elapsed * speed * 2).toLong() + 800)
        if (generation != syncedGeneration || jumped) reset(headMs)
        syncedGeneration = generation
        announce(headMs)
        sendTransport(headMs, isPlaying, isWaiting, speed, now)
        lastHead = headMs
        lastTickAt = now
    }

    private fun reset(headMs: Long) {
        send(1, sevenBit(headMs, 4))
        // Notes still sounding at the head are sent too (they started before it)
        nextIndex = notes.indexOfFirst { it.startMs + it.durationMs > headMs }.let { if (it < 0) notes.size else it }
        lastTransport = null
    }

    private fun announce(headMs: Long) {
        val perMessage = ((MidiInputManager.maxSysExSize() - HEADER.size - 2) / 9).coerceAtLeast(1)
        val batch = ArrayList<MidiNoteEvent>(perMessage)
        while (nextIndex < notes.size && notes[nextIndex].startMs < headMs + LOOKAHEAD_MS) {
            val note = notes[nextIndex++]
            if (note.startMs + note.durationMs <= headMs) continue // over already (we came in mid-song)
            batch.add(note)
            if (batch.size == perMessage) { sendNotes(batch); batch.clear() }
        }
        if (batch.isNotEmpty()) sendNotes(batch)
    }

    private fun sendNotes(batch: List<MidiNoteEvent>) {
        val data = ByteArray(batch.size * 9)
        batch.forEachIndexed { i, n ->
            sevenBit(n.startMs, 4).copyInto(data, i * 9)
            sevenBit(n.durationMs.coerceAtMost(0x1FFFFF), 3).copyInto(data, i * 9 + 4)
            data[i * 9 + 7] = n.pitch.coerceIn(0, 127).toByte()
            data[i * 9 + 8] = n.velocity.coerceIn(1, 127).toByte()
        }
        send(2, data)
    }

    private fun sendTransport(headMs: Long, isPlaying: Boolean, isWaiting: Boolean, speed: Float, now: Long) {
        val flags = (if (isPlaying) 1 else 0) or (if (isWaiting) 2 else 0)
        val speedX100 = (speed * 100).toInt().coerceIn(1, 0x3FFF)
        val state = listOf(flags, speedX100)
        val moving = isPlaying && !isWaiting
        // On every change of state, and while moving a few times a second so the other side stays in step
        val due = state != lastTransport ||
            (moving && now - lastTransportAt >= TRANSPORT_EVERY_MS) ||
            (!moving && headMs != lastHead)
        if (!due) return
        val data = sevenBit(headMs, 4) + byteArrayOf(flags.toByte()) + sevenBit(speedX100.toLong(), 2)
        send(3, data)
        lastTransport = state
        lastTransportAt = now
    }

    private fun send(command: Int, data: ByteArray) {
        MidiInputManager.sendSysEx(HEADER + byteArrayOf(command.toByte()) + data + byteArrayOf(0xF7.toByte()))
    }

    /** [value] as [groups] 7-bit bytes, most significant first. */
    private fun sevenBit(value: Long, groups: Int): ByteArray {
        val v = value.coerceAtLeast(0)
        return ByteArray(groups) { i -> ((v shr (7 * (groups - 1 - i))) and 0x7F).toByte() }
    }
}
