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
 * another. Left out on purpose: "mark" (heard as "back"), "skip" (as "stop", and "slower" as "skip"), "no" for undo (as "go", and too common in
 * normal talk), and "go" alone for play: "go back" / "go forward" whose second word is drowned out by
 * the piano came out as "go" and started playback. Commands that still share sounds ("faster" /
 * "forward") need more confidence.
 */
enum class VoiceCommand(val phrases: List<String>, val minConfidence: Float) {
    PLAY(listOf("play"), 0.7f),
    PAUSE(listOf("pause", "stop"), 0.7f),
    WAIT(listOf("wait"), 0.7f),
    // "loop start" / "loop end" with the second word drowned out come out as "loop"
    LOOP(listOf("loop"), 0.75f),
    // "full" is soft and often lost (seen on a phone: "screen" alone, three times in a row)
    FULLSCREEN(listOf("full screen", "screen"), 0.7f),
    LOOP_START(listOf("loop start"), 0.7f),
    LOOP_END(listOf("loop end"), 0.7f),
    AGAIN(listOf("again"), 0.75f),
    BACK(listOf("back", "rewind", "go back"), 0.7f),
    FORWARD(listOf("forward", "go forward", "ahead"), 0.75f),
    SLOWER(listOf("slower"), 0.7f),
    FASTER(listOf("faster"), 0.75f),
    NORMAL(listOf("normal"), 0.7f),
    // Long, and like no other command, so it needs no more than the harmless ones
    METRONOME(listOf("metronome"), 0.7f),
    COUNT_IN(listOf("count"), 0.7f),
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

        /** Below this, a word another reading has differently is doubted, even past its command's bar. */
        const val SURE_CONFIDENCE = 0.9f

        /** Most commands taken from one utterance; more is talking, not commanding. */
        const val MAX_PER_UTTERANCE = 4

        private val byPhrase: Map<List<String>, VoiceCommand> =
            entries.flatMap { c -> c.phrases.map { it.split(' ') to c } }.toMap()
        private val longestPhrase = byPhrase.keys.maxOf { it.size }

        /**
         * Commands whose phrase begins a longer one ("loop" in "loop start"): heard alone, the longer
         * phrase may have been said with its last word drowned out (by the piano).
         */
        // The commands each word is part of
        private val commandsOfWord: Map<String, Set<VoiceCommand>> = entries
            .flatMap { c -> c.phrases.flatMap { it.split(' ') }.map { it to c } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

        /** Two readings of a word that mean the same: "stop" / "pause", or spellings of "Selah". */
        private fun sameMeaning(a: String, b: String): Boolean =
            a == b || (a in WAKE_WORDS && b in WAKE_WORDS) ||
                commandsOfWord[a].orEmpty().intersect(commandsOfWord[b].orEmpty()).isNotEmpty()

        private val longerPhrases: Map<VoiceCommand, Set<VoiceCommand>> = byPhrase.entries.associate { (words, command) ->
            command to byPhrase.filterKeys { it.size > words.size && it.subList(0, words.size) == words }.values.toSet()
        }.filterValues { it.isNotEmpty() }

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
         * rivals are only noise is just a voice in a loud room. A command that begins a longer phrase
         * ("loop" of "loop end") is dropped, however sure, when another reading has the longer one. And
         * any phrase is dropped where a word under [SURE_CONFIDENCE] is another known word in another
         * reading.
         */
        fun decide(
            words: List<Pair<String, Float>>,
            alternatives: List<List<Pair<String, Float>>>,
            requireWake: Boolean = false
        ): List<VoiceCommand> {
            val rivals by lazy { alternatives.map { alt -> parse(alt.map { it.first to 1f }) } }
            // "stop 0.75 faster", also read "start faster": the doubtful first word was something else
            // (the phrase was "faster" alone). Unknown sound read in a word's place doesn't count.
            val contested = alternatives.any { alt ->
                alt.size == words.size && words.indices.any { i ->
                    words[i].second < SURE_CONFIDENCE && alt[i].first != UNKNOWN_WORD && !sameMeaning(alt[i].first, words[i].first)
                }
            }
            if (contested) return emptyList()
            parse(words, requireWake).let { sure ->
                if (sure.isEmpty()) return@let
                val longer = sure.flatMap { longerPhrases[it].orEmpty() }.toSet()
                if (longer.isEmpty()) return sure
                return if (rivals.any { r -> r.any { it in longer } }) emptyList() else sure
            }
            val unsure = parse(words, requireWake, RELAXED_CONFIDENCE)
            if (unsure.isEmpty()) return unsure
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
