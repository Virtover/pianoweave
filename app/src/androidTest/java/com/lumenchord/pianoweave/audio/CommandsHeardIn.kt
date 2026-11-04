package com.lumenchord.pianoweave.audio

/**
 * The commands voice commands take from [audio] (16 kHz), as the app does ([CommandListener]): the
 * voice gate's utterances and the whole stream, each stretch counted once. [heard] collects what each
 * reading sounded like, marked U (utterance) or S (stream) and with its time.
 */
internal fun commandsHeardIn(model: org.vosk.Model, audio: FloatArray, requireWake: Boolean = false, heard: MutableList<String>? = null): List<VoiceCommand> {
    val segmenter = UtteranceSegmenter()
    val found = mutableListOf<VoiceCommand>()
    CommandListener(model).use { listener ->
        fun take(phrase: CommandListener.Phrase?) {
            phrase ?: return
            val commands = VoiceCommandDecoder.decide(phrase.heard, requireWake)
            heard?.add("%s %.1f %s (or %s) -> %s".format(
                if (phrase.fromUtterance) "U" else "S", phrase.span.first / 16000f,
                phrase.heard.words.joinToString(" ") { "${it.word}:${"%.2f".format(it.confidence)}" },
                phrase.heard.alternatives.joinToString(" / ") { a -> a.joinToString(" ") { it.word } }, commands
            ))
            if (commands.isNotEmpty()) {
                listener.take(phrase)
                found += commands
            }
        }
        VoiceActivityGate.reset()
        VoiceActivityGate.chunkListener = VoiceActivityGate.ChunkListener { chunk, p ->
            val utterance = segmenter.feed(chunk, p)
            take(listener.chunk(chunk.copyOf()))
            if (utterance != null) take(listener.utterance(utterance))
        }
        try {
            VoiceActivityGate.feed(audio, 0, audio.size)
            // the stream's last phrase ends with some quiet
            val quiet = FloatArray(512)
            repeat(30) { take(listener.chunk(quiet)) }
        } finally {
            VoiceActivityGate.chunkListener = null
        }
    }
    return found
}
