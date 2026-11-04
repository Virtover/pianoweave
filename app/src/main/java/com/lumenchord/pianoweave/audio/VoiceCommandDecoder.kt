package com.lumenchord.pianoweave.audio

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Turns one utterance into [VoiceCommand]s with two Vosk recognizers on the command grammar: one gives
 * each word's confidence, the other the next-best readings. Vosk can't give both at once, and the
 * readings tell an unsure word apart from a confused one (see [VoiceCommand.decide]). Both decode only
 * isolated utterances, so the second costs a few tens of ms per spoken command.
 *
 * Not thread-safe: use from one thread (VoiceCommandRecognizer's worker).
 */
internal class VoiceCommandDecoder(model: Model) : AutoCloseable {
    companion object {
        private const val SAMPLE_RATE = 16000f
        // Vosk takes float samples in the 16-bit range
        private const val PCM_SCALE = 32768f
        // The mic is unprocessed (no automatic gain), so a voice across the room arrives quiet: each
        // utterance is brought to this peak, by at most MAX_GAIN, before decoding
        private const val TARGET_PEAK = 0.7f
        private const val MAX_GAIN = 64f
        // Fed in blocks so an endpoint between words in a row only closes a part, not the rest
        private const val FEED_BLOCK = 1600
        private const val ALTERNATIVES = 4

        fun decide(heard: Heard, requireWake: Boolean): List<VoiceCommand> = VoiceCommand.decide(
            words = VoiceCommand.trimEdgeNoise(heard.words),
            alternatives = heard.alternatives.map { VoiceCommand.trimEdgeNoise(it) },
            requireWake = requireWake
        )
    }

    /** What one utterance sounded like: the best reading's words, and the other readings. */
    class Heard(val words: List<HeardWord>, val alternatives: List<List<HeardWord>>)

    private val scored = Recognizer(model, SAMPLE_RATE, VoiceCommand.grammarJson).apply { setWords(true) }
    private val ranked = Recognizer(model, SAMPLE_RATE, VoiceCommand.grammarJson).apply {
        setWords(true)
        setMaxAlternatives(ALTERNATIVES)
    }

    /** Decodes [utterance] (16 kHz, -1..1). The array is not changed. */
    fun hear(utterance: FloatArray): Heard {
        var peak = 0f
        for (s in utterance) peak = maxOf(peak, kotlin.math.abs(s))
        val scale = PCM_SCALE * if (peak > 0f) minOf(TARGET_PEAK / peak, MAX_GAIN) else 1f
        val pcm = FloatArray(utterance.size) { utterance[it] * scale }

        val words = mutableListOf<HeardWord>()
        // Readings of each part (an endpoint between words closes one), best first
        val parts = mutableListOf<List<List<HeardWord>>>()
        var off = 0
        while (off < pcm.size) {
            val n = minOf(FEED_BLOCK, pcm.size - off)
            val block = pcm.copyOfRange(off, off + n)
            if (scored.acceptWaveForm(block, n)) words += scoredWords(scored.result)
            if (ranked.acceptWaveForm(block, n)) parts += readings(ranked.result)
            off += n
        }
        words += scoredWords(scored.finalResult)
        parts += readings(ranked.finalResult)

        // Whole-utterance readings: the best of every part, with one part swapped for each alternative
        val best = parts.map { it.firstOrNull().orEmpty() }
        val alternatives = parts.flatMapIndexed { p, readings ->
            readings.drop(1).map { alt -> best.flatMapIndexed { i, b -> if (i == p) alt else b } }
        }
        return Heard(words, alternatives)
    }

    /** The commands in [utterance], or empty. */
    fun commands(utterance: FloatArray, requireWake: Boolean = false): List<VoiceCommand> =
        decide(hear(utterance), requireWake)

    fun decide(heard: Heard, requireWake: Boolean): List<VoiceCommand> = Companion.decide(heard, requireWake)

    override fun close() {
        scored.close()
        ranked.close()
    }

    private fun scoredWords(json: String): List<HeardWord> =
        wordsOf(JsonParser.parseString(json).asJsonObject) { it.get("conf")?.asFloat ?: 1f }

    private fun readings(json: String): List<List<HeardWord>> =
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("alternatives")
            ?.map { alt -> wordsOf(alt.asJsonObject) { 1f } }
            .orEmpty()

    private fun wordsOf(result: JsonObject, confidence: (JsonObject) -> Float): List<HeardWord> =
        result.getAsJsonArray("result")?.map { element ->
            val word = element.asJsonObject
            HeardWord(
                word = word.get("word").asString,
                confidence = confidence(word),
                duration = word.get("end").asFloat - word.get("start").asFloat
            )
        }.orEmpty()
}
