package com.lumenchord.pianoweave.midi

/**
 * Runs one GATT request on each of several targets, one after another (Android takes one request at a time):
 * the next starts when the previous reports back. Used for devices with more than one MIDI service (a Linux
 * computer with PipeWire's MIDI server next to another app's), so every one of them is subscribed and
 * written to, not only the first.
 *
 * [request] starts the request on a target and returns false if it couldn't start (that target is skipped).
 * [onDone] comes after the last result, with whether at least one target succeeded. Single-threaded.
 */
internal class GattSequence<T>(
    private val targets: List<T>,
    private val request: (T) -> Boolean,
    private val onDone: (anySucceeded: Boolean) -> Unit,
) {
    private var index = -1
    private var anySucceeded = false
    var isRunning = false
        private set

    /** Starts with the first target that takes the request; false if none did (then [onDone] never comes). */
    fun start(): Boolean {
        index = -1
        anySucceeded = false
        isRunning = advance()
        return isRunning
    }

    /** The current target's request reported back. */
    fun onResult(success: Boolean) {
        if (!isRunning) return
        if (success) anySucceeded = true
        if (!advance()) {
            isRunning = false
            onDone(anySucceeded)
        }
    }

    private fun advance(): Boolean {
        while (++index < targets.size) {
            if (request(targets[index])) return true
        }
        return false
    }

    fun cancel() { isRunning = false }
}
