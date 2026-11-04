package com.lumenchord.pianoweave.audio

import com.google.gson.JsonParser
import org.vosk.Model
import org.vosk.Recognizer

/**
 * The command grammar listening to the whole microphone stream, cut into phrases by the recognizer's
 * own pauses: the second way [CommandListener] hears commands. The voice gate (Silero), even on audio
 * cleaned by [SpeechEnhancer], rated a voice across the room over a piano 0.00 for minutes (phone
 * recording), while this heard those commands at full confidence. Decoding against the small grammar
 * costs a few percent of one core.
 *
 * Each phrase that holds a word the grammar knows becomes a [VoiceCommandDecoder.Heard]: its words with
 * confidences, and the other readings, from decoding the phrase's audio again (kept in a short
 * history). Phrases of unknown sound only (the piano, talking) are skipped at once.
 *
 * Not thread-safe: use from one thread (VoiceCommandRecognizer's worker).
 */
internal class CommandStream(model: Model) : AutoCloseable {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val PCM_SCALE = 32767f
        private const val HISTORY_SECONDS = 8
        // Audio kept around a phrase's words when it's decoded again
        private const val MARGIN_SECONDS = 0.3f
    }

    private val recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), VoiceCommand.grammarJson).apply { setWords(true) }
    private val decoder = VoiceCommandDecoder(model)

    private val history = FloatArray(HISTORY_SECONDS * SAMPLE_RATE)
    // Samples fed since the recognizer started; its word times count from there
    private var fed = 0L
    private var block = FloatArray(0)

    /** Where the last phrase's words were, in samples since the stream started. */
    var lastSpan: LongRange = LongRange.EMPTY
        private set

    /** Feeds 16 kHz audio (-1..1); returns the phrase that just ended, if it had a known word. */
    fun feed(samples: FloatArray): VoiceCommandDecoder.Heard? {
        for (s in samples) {
            history[(fed % history.size).toInt()] = s
            fed++
        }
        if (block.size != samples.size) block = FloatArray(samples.size)
        for (i in samples.indices) block[i] = samples[i] * PCM_SCALE
        return if (recognizer.acceptWaveForm(block, block.size)) phrase(recognizer.result) else null
    }

    /** The phrase in progress, if it has a known word; for when listening stops. */
    fun finish(): VoiceCommandDecoder.Heard? = phrase(recognizer.finalResult)

    /** Forgets the phrase in progress (audio was lost). */
    fun reset() {
        recognizer.reset()
    }

    private fun phrase(json: String): VoiceCommandDecoder.Heard? {
        val result = JsonParser.parseString(json).asJsonObject.getAsJsonArray("result") ?: return null
        val words = result.map { it.asJsonObject }
        if (words.none { it["word"].asString != VoiceCommand.UNKNOWN_WORD }) return null
        val heard = words.map {
            HeardWord(it["word"].asString, it["conf"]?.asFloat ?: 1f, it["end"].asFloat - it["start"].asFloat)
        }
        lastSpan = (words.first()["start"].asFloat * SAMPLE_RATE).toLong()..(words.last()["end"].asFloat * SAMPLE_RATE).toLong()
        // The phrase's audio, for the other readings
        val from = ((words.first()["start"].asFloat - MARGIN_SECONDS) * SAMPLE_RATE).toLong()
            .coerceAtLeast(maxOf(0L, fed - history.size))
        val to = ((words.last()["end"].asFloat + MARGIN_SECONDS) * SAMPLE_RATE).toLong().coerceAtMost(fed)
        val audio = FloatArray((to - from).toInt().coerceAtLeast(0)) { history[((from + it) % history.size).toInt()] }
        val alternatives = if (audio.isNotEmpty()) decoder.hear(audio).alternatives else emptyList()
        return VoiceCommandDecoder.Heard(heard, alternatives)
    }

    override fun close() {
        recognizer.close()
        decoder.close()
    }
}
