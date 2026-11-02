package com.lumenchord.pianoweave.audio

/**
 * How voice commands are listened for. [WORDS]: just the command, said on its own. [SELAH]: "Selah"
 * first ("Selah, full screen wait"), for noisy rooms or lessons, where talking could sound like a
 * command; then it may also follow other speech closely.
 */
enum class VoiceListeningMode { WORDS, SELAH }

/** One recognized word, with the recognizer's confidence and how long it lasted (seconds). */
data class HeardWord(val word: String, val confidence: Float, val duration: Float = 0.3f)

/**
 * The piano roll's spoken commands, each one or two words ([phrases]). Several can be said in a row
 * ("full screen wait", "faster faster"). [minConfidence] is the recognizer confidence each word needs;
 * commands that lose your place (jump, loop marks) ask for more than the harmless ones, since a false
 * "pause" costs nothing and a false "again" loses where you were.
 *
 * Clear speech comes out at confidence 1.0, even ~30 dB down or with room noise ~10 dB under it. In
 * noise, right words drop to ~0.5-0.7 and wrong ones appeared at 0.6-0.66, so confidence alone can't
 * tell them apart: a command is taken at 0.7, or from [RELAXED_CONFIDENCE] when no other reading of
 * the utterance is a different command (see [decide]).
 *
 * The words are chosen not to sound alike to the small speech model, so one is never taken for
 * another. Left out on purpose: "mark" (heard as "back"), "skip" (as "stop"), and "no" for undo (as
 * "go", and too common in normal talk). Commands that still share sounds ("faster" / "forward")
 * need more confidence.
 */
enum class VoiceCommand(val phrases: List<String>, val minConfidence: Float) {
    PLAY(listOf("play", "go", "continue"), 0.7f),
    PAUSE(listOf("pause", "stop"), 0.7f),
    WAIT(listOf("wait"), 0.7f),
    // "loop start" / "loop end" with the second word drowned out come out as "loop"
    LOOP(listOf("loop"), 0.75f),
    FULLSCREEN(listOf("full screen"), 0.7f),
    LOOP_START(listOf("loop start"), 0.7f),
    LOOP_END(listOf("loop end"), 0.7f),
    AGAIN(listOf("again"), 0.75f),
    BACK(listOf("back", "rewind", "go back"), 0.7f),
    FORWARD(listOf("forward", "go forward"), 0.75f),
    SLOWER(listOf("slower"), 0.7f),
    FASTER(listOf("faster"), 0.75f),
    NORMAL(listOf("normal"), 0.7f),
    UNDO(listOf("undo"), 0.7f);

    companion object {
        /** Vosk's word for speech outside the grammar. */
        const val UNKNOWN_WORD = "[unk]"

        /**
         * "Selah" (the Psalms' mark for a pause) before the commands. It's optional unless the mode is
         * [VoiceListeningMode.SELAH]; said alone it opens a short window for the commands that follow
         * (see VoiceCommandRecognizer). The other spellings are words of the speech model that sound the
         * same, so each way of saying it ("SEH-lah", "SEE-lah") is caught.
         */
        val WAKE_WORDS = listOf("selah", "sela", "sella", "cela")
        // Low on purpose: the sound-alike spellings split the recognizer's confidence between them, so
        // a clear "Selah" can come out at 0.3 for each. The commands after it keep their own bars.
        private const val WAKE_MIN_CONFIDENCE = 0.25f

        /**
         * Words in the grammar that are no command. "no" sounds like "go" (play); with it listed, a "no"
         * said in conversation is recognized as itself and ignored, instead of being forced onto "go".
         */
        val DISTRACTOR_WORDS = listOf("no")

        // Noise at the edges of an utterance (a breath, a click, a chair) comes out as a short unknown
        // word; it doesn't make the commands next to it talking. Longer unknown speech does.
        private const val MAX_EDGE_NOISE_SECONDS = 0.25f

        /** Confidence an unsure command still counts from, when nothing else could have been meant. */
        const val RELAXED_CONFIDENCE = 0.5f

        /** Most commands taken from one utterance; more is talking, not commanding. */
        const val MAX_PER_UTTERANCE = 4

        private val byPhrase: Map<List<String>, VoiceCommand> =
            entries.flatMap { c -> c.phrases.map { it.split(' ') to c } }.toMap()
        private val longestPhrase = byPhrase.keys.maxOf { it.size }

        /**
         * Vosk grammar: the [WAKE_WORDS], every phrase, the [DISTRACTOR_WORDS] and [UNKNOWN_WORD]. The full list is always active (not only what
         * makes sense right now), so a word that's out of place is still told apart from the others
         * instead of being forced onto the nearest allowed one.
         */
        val grammarJson: String =
            (WAKE_WORDS + entries.flatMap { it.phrases } + DISTRACTOR_WORDS + UNKNOWN_WORD)
                .joinToString(",", "[", "]") { "\"$it\"" }

        /**
         * The commands in a recognized utterance of (word, confidence) pairs, in order, or empty. The
         * utterance must be only commands: anything else heard with them ([UNKNOWN_WORD], a stray word,
         * a word below its command's confidence) means it was normal speech, and nothing is taken.
         * With [requireWake], it must also start with one of the [WAKE_WORDS].
         */
        fun parse(
            words: List<Pair<String, Float>>,
            requireWake: Boolean = false,
            confidenceFloor: Float = 1f
        ): List<VoiceCommand> {
            var i = 0
            while (i < words.size && words[i].first.lowercase() in WAKE_WORDS && words[i].second >= WAKE_MIN_CONFIDENCE) i++
            val woke = i > 0
            if (requireWake && !woke) return emptyList()
            if (woke && i == words.size) return emptyList()

            val result = mutableListOf<VoiceCommand>()
            while (i < words.size) {
                // Longest phrase first, so "full screen" isn't read as two unknown words
                val match = (minOf(longestPhrase, words.size - i) downTo 1).firstNotNullOfOrNull { n ->
                    byPhrase[words.subList(i, i + n).map { it.first.lowercase() }]?.let { it to n }
                } ?: return emptyList()
                val (command, n) = match
                val needed = minOf(command.minConfidence, confidenceFloor)
                if (words.subList(i, i + n).any { it.second < needed }) return emptyList()
                result += command
                i += n
            }
            return if (result.size <= MAX_PER_UTTERANCE) result else emptyList()
        }

        /**
         * The commands of an utterance, from its best reading's [words] and the recognizer's other
         * readings ([alternatives], words only). Confident commands are taken as [parse] gives them.
         * Unsure ones (from [RELAXED_CONFIDENCE]) only when no other reading is a different command:
         * "wait" at 0.6 with "play" as a rival is a confusion and is dropped, while "pause" at 0.6 whose
         * rivals are only noise is just a voice in a loud room.
         */
        fun decide(
            words: List<Pair<String, Float>>,
            alternatives: List<List<Pair<String, Float>>>,
            requireWake: Boolean = false
        ): List<VoiceCommand> {
            parse(words, requireWake).let { if (it.isNotEmpty()) return it }
            val unsure = parse(words, requireWake, RELAXED_CONFIDENCE)
            if (unsure.isEmpty()) return unsure
            val rivals = alternatives.map { alt -> parse(alt.map { it.first to 1f }) }
            return if (rivals.any { it.isNotEmpty() && it != unsure }) emptyList() else unsure
        }

        /**
         * [words] as (word, confidence) for [parse], without short unknown noise at the start or end.
         * Unknown speech in the middle, or long at the edges ("please pause"), stays and rejects them.
         */
        fun trimEdgeNoise(words: List<HeardWord>): List<Pair<String, Float>> {
            fun isNoise(w: HeardWord) = w.word == UNKNOWN_WORD && w.duration <= MAX_EDGE_NOISE_SECONDS
            return words.dropWhile(::isNoise).dropLastWhile(::isNoise).map { it.word to it.confidence }
        }

        /** Whether [words] holds anything the grammar knows, not just unknown sound (noise, talking). */
        fun hasKnownWord(words: List<Pair<String, Float>>): Boolean = words.any { it.first != UNKNOWN_WORD }

        /** "Selah" said on its own (once or more), which arms the commands that follow. */
        fun isWakeOnly(words: List<Pair<String, Float>>): Boolean =
            words.isNotEmpty() && words.all { (w, conf) -> w.lowercase() in WAKE_WORDS && conf >= WAKE_MIN_CONFIDENCE }
    }
}
