package com.lumenchord.pianoweave.audio

import org.vosk.Model

/**
 * Hears commands two ways at once, on one timeline. Utterances the voice gate cuts out
 * ([UtteranceSegmenter]) are read first: quick (~0.3 s after the word) and well guarded against
 * talking, but the gate misses a voice that is far away while the piano plays. [CommandStream] hears
 * the whole stream as well and catches those, ~0.6 s after the word. A stretch of audio counts once:
 * whichever way first gives commands for it, the other's reading of it is skipped.
 *
 * Not thread-safe: feed the chunks and the utterances (in order, an utterance after its last chunk)
 * from one thread.
 */
internal class CommandListener(model: Model) : AutoCloseable {
    companion object {
        private const val SAMPLE_RATE = 16000
        // How long taken stretches are remembered (longer than any phrase or utterance)
        private const val MEMORY_SAMPLES = 10L * SAMPLE_RATE
        /**
         * Each word the stream alone hears must be this sure: it has no pause rule against talking, and
         * its doubtful readings over the piano were mostly not what was said (a voice across the room
         * that the gate missed still came out at 1.00).
         */
        const val STREAM_MIN_CONFIDENCE = 0.9f
    }

    /** What a stretch of audio sounded like, and where it was (samples since listening started). */
    class Phrase(val heard: VoiceCommandDecoder.Heard, val span: LongRange, val fromUtterance: Boolean)

    private val decoder = VoiceCommandDecoder(model)
    private val stream = CommandStream(model)
    /** Samples fed so far (the timeline's now). */
    var fed = 0L
        private set
    private val taken = ArrayDeque<LongRange>()

    /** A raw chunk of the stream; the stream's phrase that just ended, unless already taken. */
    fun chunk(samples: FloatArray): Phrase? {
        fed += samples.size
        val heard = stream.feed(samples) ?: return null
        if (heard.words.any { it.word != VoiceCommand.UNKNOWN_WORD && it.confidence < STREAM_MIN_CONFIDENCE }) return null
        return candidate(Phrase(heard, stream.lastSpan, fromUtterance = false))
    }

    /** An utterance the voice gate cut, which ended with the last chunk fed; its reading, unless taken. */
    fun utterance(audio: FloatArray): Phrase? =
        candidate(Phrase(decoder.hear(audio), (fed - audio.size).coerceAtLeast(0)..fed, fromUtterance = true))

    /** [phrase] gave commands: the other way's reading of the same stretch is skipped. */
    fun take(phrase: Phrase) {
        taken.addLast(phrase.span)
        while (taken.isNotEmpty() && taken.first().last < fed - MEMORY_SAMPLES) taken.removeFirst()
    }

    /** Whether commands were already taken from audio overlapping [span]. */
    fun isTaken(span: LongRange): Boolean = taken.any { it.first <= span.last && span.first <= it.last }

    /** Audio was lost: the phrase in progress is forgotten. */
    fun reset() = stream.reset()

    private fun candidate(phrase: Phrase): Phrase? = if (isTaken(phrase.span)) null else phrase

    override fun close() {
        stream.close()
        decoder.close()
    }
}
