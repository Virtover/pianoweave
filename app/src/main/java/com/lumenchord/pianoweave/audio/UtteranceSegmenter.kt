package com.lumenchord.pianoweave.audio

/**
 * Cuts short, isolated utterances out of the voice gate's 32 ms chunks, so voice commands need no wake
 * word. A command (or a few in a row, "full screen wait") is said on its own: no speech just before
 * it, at most ~2.5 s of speech, a pause after. Talking, singing along or a TV runs on without such
 * pauses or too long, and is dropped here before the recognizer sees it (which keeps it idle).
 *
 * Speech starts at [SPEECH_PROB], which is low: the voice gate (Silero) is trained to ignore music, so
 * a voice over a piano being played rates only ~0.1-0.4, while the piano alone stays near 0. Where
 * the room itself rates higher for a while (a murmur, a fan), the start level rises over it
 * ([background] + [BACKGROUND_MARGIN]), so that steady sound still counts as a pause. Once started,
 * speech goes on down to a level relative to the utterance's own peak ([CONTINUE_RATIO], at least
 * [CONTINUE_PROB] and over the background): a clear voice ends where its rating falls off, so room
 * noise after it isn't kept, and a voice over the piano, rated low throughout, isn't cut short.
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
        const val SPEECH_PROB = 0.15f
        /** Lowest voice probability that keeps an utterance going once it started. */
        const val CONTINUE_PROB = 0.08f
        /** Part of the utterance's peak probability that keeps it going (0.25 for a clear voice). */
        const val CONTINUE_RATIO = 0.25f
        /**
         * How far over the room's own background rating speech must start, and continue. The background
         * follows steady sound in ~1 s ([BACKGROUND_RISE] per chunk) and drops back fast. It is held
         * while an utterance is heard, so commands don't move it; only longer sound does.
         */
        const val BACKGROUND_MARGIN = 0.12f
        const val CONTINUE_MARGIN = 0.05f
        const val BACKGROUND_RISE = 0.03f
        const val BACKGROUND_FALL = 0.2f
        /** Pause needed before and after an utterance (8 x 32 ms = 256 ms). */
        const val PAUSE_CHUNKS = 8
        /**
         * Speech length accepted: ~100 ms .. ~2.5 s (a few words in a row). Shorter is a click, or the
         * attack of a piano note, which the level-boosted voice gate rates as voice for ~2 chunks.
         */
        const val MIN_SPEECH_CHUNKS = 3
        const val MAX_SPEECH_CHUNKS = 78
        /** Audio kept from before speech starts, so the first consonant isn't cut. */
        const val PRE_ROLL_CHUNKS = 14
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
    private var peakProb = 0f

    /** The room's steady voice rating, without the utterances (it's held while one is heard). */
    var background = 0f
        private set

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
        background = 0f
    }

    /** Feeds one chunk; returns a finished utterance's audio (a copy) when one just ended, else null. */
    fun feed(chunk: FloatArray, probability: Float): FloatArray? {
        val speech = probability >= maxOf(SPEECH_PROB, background + BACKGROUND_MARGIN)
        if (state != State.SPEECH) {
            background += (probability - background) * if (probability > background) BACKGROUND_RISE else BACKGROUND_FALL
        }
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
                    peakProb = probability
                } else {
                    // Speech that follows other speech too closely is part of a sentence
                    state = State.REJECTED
                    onRejected("speech ${pauseBefore * 32} ms after other speech")
                }
            }
            State.SPEECH -> {
                append(chunk)
                peakProb = maxOf(peakProb, probability)
                val keepGoing = maxOf(CONTINUE_PROB, peakProb * CONTINUE_RATIO, background + CONTINUE_MARGIN)
                if (probability >= keepGoing) trailingPause = 0 else trailingPause++
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
