package com.lumenchord.pianoweave.audio

/**
 * Cuts short, isolated utterances out of the voice gate's 32 ms chunks, so voice commands need no wake
 * word. A command (or a few in a row, "full screen wait") is said on its own: no speech just before
 * it, at most ~2.5 s of speech, a pause after. Talking, singing along or a TV runs on without such
 * pauses or too long, and is dropped here before the recognizer sees it (which keeps it idle).
 *
 * Speech starts at [SPEECH_PROB]. Anything below counts as a pause before it, including the in-between
 * values room noise or a piano can hover at, so those can't keep a pause from being seen. Once
 * started, speech goes on down to [CONTINUE_PROB]: a quiet voice (the mic is unprocessed, without
 * automatic gain) peaks only briefly over the start threshold, and would otherwise be cut too short.
 *
 * Pure and single-threaded (fed from the audio thread), so it is unit-tested on the JVM. [onRejected]
 * gets the reason a candidate was dropped, for logs.
 */
internal class UtteranceSegmenter(
    private val chunkSize: Int = 512,
    private val onRejected: (String) -> Unit = {}
) {
    companion object {
        /** Voice probability at or above which a chunk is speech. */
        const val SPEECH_PROB = 0.4f
        /** Voice probability that keeps an utterance going once it started. */
        const val CONTINUE_PROB = 0.25f
        /** Pause needed before and after an utterance (8 x 32 ms = 256 ms). */
        const val PAUSE_CHUNKS = 8
        /**
         * Speech length accepted: ~100 ms .. ~2.5 s (a few words in a row). Shorter is a click, or the
         * attack of a piano note, which the level-boosted voice gate rates as voice for ~2 chunks.
         */
        const val MIN_SPEECH_CHUNKS = 3
        const val MAX_SPEECH_CHUNKS = 78
        /** Audio kept from before speech starts, so the first consonant isn't cut. */
        const val PRE_ROLL_CHUNKS = 6
        /** Pause kept after the speech, for the recognizer's endpoint. */
        const val TAIL_CHUNKS = 3
    }

    enum class State { IDLE, SPEECH, REJECTED }

    var state = State.IDLE
        private set

    /**
     * Whether speech must follow a pause to count. Off when a wake word guards the commands instead
     * (set from the main thread while the audio thread feeds).
     */
    @Volatile
    var requirePauseBefore = true

    private val preRoll = Array(PRE_ROLL_CHUNKS) { FloatArray(chunkSize) }
    private var preRollPos = 0
    private var preRollCount = 0

    private val buffer = FloatArray((PRE_ROLL_CHUNKS + MAX_SPEECH_CHUNKS + PAUSE_CHUNKS + 1) * chunkSize)
    private var bufferChunks = 0
    private var speechChunks = 0
    private var trailingPause = 0

    // Consecutive non-speech chunks; starting "paused" lets a command be the first thing said
    private var pauseRun = PAUSE_CHUNKS

    fun reset() {
        state = State.IDLE
        preRollPos = 0
        preRollCount = 0
        bufferChunks = 0
        speechChunks = 0
        trailingPause = 0
        pauseRun = PAUSE_CHUNKS
    }

    /** Feeds one chunk; returns a finished utterance's audio (a copy) when one just ended, else null. */
    fun feed(chunk: FloatArray, probability: Float): FloatArray? {
        val speech = probability >= SPEECH_PROB
        val pauseBefore = pauseRun
        pauseRun = if (speech) 0 else pauseRun + 1

        var finished: FloatArray? = null
        when (state) {
            State.IDLE -> if (speech) {
                if (pauseBefore >= PAUSE_CHUNKS || !requirePauseBefore) {
                    state = State.SPEECH
                    bufferChunks = 0
                    for (i in 0 until preRollCount) {
                        append(preRoll[(preRollPos - preRollCount + i + PRE_ROLL_CHUNKS) % PRE_ROLL_CHUNKS])
                    }
                    append(chunk)
                    speechChunks = 1
                    trailingPause = 0
                } else {
                    // Speech that follows other speech too closely is part of a sentence
                    state = State.REJECTED
                    onRejected("speech ${pauseBefore * 32} ms after other speech")
                }
            }
            State.SPEECH -> {
                append(chunk)
                if (probability >= CONTINUE_PROB) trailingPause = 0 else trailingPause++
                speechChunks++
                val spoken = speechChunks - trailingPause
                if (spoken > MAX_SPEECH_CHUNKS) {
                    state = State.REJECTED
                    onRejected("longer than ${MAX_SPEECH_CHUNKS * 32} ms")
                } else if (trailingPause >= PAUSE_CHUNKS) {
                    if (spoken >= MIN_SPEECH_CHUNKS) {
                        val keptChunks = bufferChunks - trailingPause + TAIL_CHUNKS
                        finished = buffer.copyOf(keptChunks * chunkSize)
                    } else {
                        onRejected("too short (${spoken * 32} ms)")
                    }
                    state = State.IDLE
                }
            }
            State.REJECTED -> if (pauseRun >= PAUSE_CHUNKS) state = State.IDLE
        }

        System.arraycopy(chunk, 0, preRoll[preRollPos], 0, chunkSize)
        preRollPos = (preRollPos + 1) % PRE_ROLL_CHUNKS
        if (preRollCount < PRE_ROLL_CHUNKS) preRollCount++
        return finished
    }

    private fun append(chunk: FloatArray) {
        System.arraycopy(chunk, 0, buffer, bufferChunks * chunkSize, chunkSize)
        bufferChunks++
    }
}
