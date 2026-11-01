package com.lumenchord.pianoweave.audio

/**
 * How voice commands are listened for. [WORDS]: just the command, said on its own. [SELAH]: "Selah"
 * first ("Selah, full screen wait"), for noisy rooms or lessons, where talking could sound like a
 * command; then it may also follow other speech closely.
 */
enum class VoiceListeningMode { WORDS, SELAH }

/**
 * The piano roll's spoken commands, each one or two words ([phrases]). Several can be said in a row
 * ("full screen wait", "faster faster"). [minConfidence] is the recognizer confidence each word needs;
 * commands that lose your place (jump, loop marks) ask for more than the harmless ones, since a false
 * "pause" costs nothing and a false "again" loses where you were.
 */
enum class VoiceCommand(val phrases: List<String>, val minConfidence: Float) {
    PLAY(listOf("play", "go", "continue"), 0.5f),
    PAUSE(listOf("pause", "stop"), 0.5f),
    WAIT(listOf("wait"), 0.55f),
    LOOP(listOf("loop"), 0.55f),
    FULLSCREEN(listOf("full screen"), 0.55f),
    // Not "mark": said quickly it is "back" to the small model, which then caught neither reliably
    LOOP_START(listOf("loop start"), 0.6f),
    LOOP_END(listOf("loop end"), 0.6f),
    AGAIN(listOf("again"), 0.7f),
    BACK(listOf("back", "rewind", "go back"), 0.5f),
    FORWARD(listOf("forward", "skip", "go forward"), 0.5f),
    SLOWER(listOf("slower"), 0.55f),
    FASTER(listOf("faster"), 0.55f),
    NORMAL(listOf("normal"), 0.6f),
    UNDO(listOf("no", "undo"), 0.55f);

    companion object {
        /** Vosk's word for speech outside the grammar. */
        const val UNKNOWN_WORD = "[unk]"

        /**
         * "Selah", the Psalms' mark for a pause or rest, before the commands. It's optional unless the
         * mode is [VoiceListeningMode.SELAH], and said alone it pauses. The other spellings are words of
         * the speech model that sound the same, so each way of saying it is caught.
         */
        val WAKE_WORDS = listOf("selah", "sela", "sella", "cela")
        private const val WAKE_MIN_CONFIDENCE = 0.55f

        /** Most commands taken from one utterance; more is talking, not commanding. */
        const val MAX_PER_UTTERANCE = 4

        private val byPhrase: Map<List<String>, VoiceCommand> =
            entries.flatMap { c -> c.phrases.map { it.split(' ') to c } }.toMap()
        private val longestPhrase = byPhrase.keys.maxOf { it.size }

        /**
         * Vosk grammar: the [WAKE_WORDS], every phrase and [UNKNOWN_WORD]. The full list is always active (not only what
         * makes sense right now), so a word that's out of place is still told apart from the others
         * instead of being forced onto the nearest allowed one.
         */
        val grammarJson: String =
            (WAKE_WORDS + entries.flatMap { it.phrases } + UNKNOWN_WORD).joinToString(",", "[", "]") { "\"$it\"" }

        /**
         * The commands in a recognized utterance of (word, confidence) pairs, in order, or empty. The
         * utterance must be only commands: anything else heard with them ([UNKNOWN_WORD], a stray word,
         * a word below its command's confidence) means it was normal speech, and nothing is taken.
         * With [requireWake], it must also start with one of the [WAKE_WORDS].
         */
        fun parse(words: List<Pair<String, Float>>, requireWake: Boolean = false): List<VoiceCommand> {
            var i = 0
            while (i < words.size && words[i].first.lowercase() in WAKE_WORDS && words[i].second >= WAKE_MIN_CONFIDENCE) i++
            val woke = i > 0
            if (requireWake && !woke) return emptyList()
            if (woke && i == words.size) return listOf(PAUSE)

            val result = mutableListOf<VoiceCommand>()
            while (i < words.size) {
                // Longest phrase first, so "full screen" isn't read as two unknown words
                val match = (minOf(longestPhrase, words.size - i) downTo 1).firstNotNullOfOrNull { n ->
                    byPhrase[words.subList(i, i + n).map { it.first.lowercase() }]?.let { it to n }
                } ?: return emptyList()
                val (command, n) = match
                if (words.subList(i, i + n).any { it.second < command.minConfidence }) return emptyList()
                result += command
                i += n
            }
            return if (result.size <= MAX_PER_UTTERANCE) result else emptyList()
        }
    }
}
