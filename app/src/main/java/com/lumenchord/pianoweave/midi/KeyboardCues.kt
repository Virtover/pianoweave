package com.lumenchord.pianoweave.midi

/**
 * When the "lost" and "back" sounds for the Bluetooth keyboard play (the sounds themselves are
 * [com.lumenchord.pianoweave.audio.ConnectionChime]); kept apart from Android so it's tested.
 *
 * - Lost: only if it stays lost for [GRACE_MS]. A weak signal drops and regains the link in a second or two,
 *   and a chime for each of those would be noise.
 * - Back: only after a "lost" was heard, and only when it came back by itself. Connecting at app start or
 *   by tapping it is silent: the person is looking at the screen then.
 * - Only while the app is on screen ([isAudible]).
 */
internal class KeyboardCues(
    private val schedule: (delayMs: Long, task: () -> Unit) -> Cancel,
    private val isAudible: () -> Boolean,
    private val play: (rising: Boolean) -> Unit,
) {
    fun interface Cancel { fun cancel() }

    private var pendingLost: Cancel? = null
    private var lostWasHeard = false

    /** The keyboard in use dropped out (not on purpose). */
    fun onLost() {
        pendingLost?.cancel()
        pendingLost = schedule(GRACE_MS) {
            pendingLost = null
            if (isAudible()) {
                play(false)
                lostWasHeard = true
            }
        }
    }

    /** The keyboard reconnected by itself. */
    fun onBack() {
        val stillInGrace = pendingLost != null
        pendingLost?.cancel()
        pendingLost = null
        if (!stillInGrace && lostWasHeard && isAudible()) play(true)
        lostWasHeard = false
    }

    /** Connected by tapping, disconnected on purpose, Bluetooth MIDI turned off: no sound owed. */
    fun reset() {
        pendingLost?.cancel()
        pendingLost = null
        lostWasHeard = false
    }

    companion object {
        const val GRACE_MS = 3_000L
    }
}
