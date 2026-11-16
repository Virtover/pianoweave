package com.lumenchord.pianoweave.midi

/**
 * The keyboard's sustain pedal (MIDI control change 64) for the sound the app plays: while it's down, keys
 * let go keep ringing, and they stop together when it comes up. Only the sound: the keys on screen and wait
 * mode follow the fingers, as on a real piano.
 *
 * [stop] silences one pitch. Thread-safe (USB and Bluetooth keyboards report on different threads).
 */
internal class SustainPedal(private val stop: (pitch: Int) -> Unit) {
    private val ringing = mutableSetOf<Int>()

    @get:Synchronized
    var isDown = false
        private set

    /** A key went down; if it was ringing on the pedal, it's held by the finger now. */
    @Synchronized
    fun noteOn(pitch: Int) { ringing.remove(pitch) }

    /** A key was let go: true if its sound should stop now, false if the pedal keeps it ringing. */
    @Synchronized
    fun noteOff(pitch: Int): Boolean {
        if (!isDown) return true
        ringing.add(pitch)
        return false
    }

    /** Whether [pitch] is ringing only because of the pedal (so the song shouldn't cut it either). */
    @Synchronized
    fun isRinging(pitch: Int): Boolean = pitch in ringing

    /** Control change 64: 64 and above is down (half-pedal values count by that line). */
    fun onPedal(value: Int) {
        val released: List<Int>
        synchronized(this) {
            val down = value >= 64
            if (down == isDown) return
            isDown = down
            if (down) return
            released = ringing.toList()
            ringing.clear()
        }
        released.forEach(stop) // outside the lock: stopping calls into the synth
    }

    /** The keyboard went away or the sound was turned off: pedal up, nothing ringing (the caller stops the sound). */
    @Synchronized
    fun reset() {
        isDown = false
        ringing.clear()
    }
}
