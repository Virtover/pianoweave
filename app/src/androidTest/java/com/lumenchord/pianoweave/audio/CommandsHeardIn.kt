package com.lumenchord.pianoweave.audio

/**
 * The commands voice commands take from [audio] (16 kHz), as the app does: the voice gate cuts the
 * utterances, the decoder reads each. [heard] collects what each utterance sounded like.
 */
internal fun commandsHeardIn(model: org.vosk.Model, audio: FloatArray, requireWake: Boolean = false, heard: MutableList<String>? = null): List<VoiceCommand> {
    val segmenter = UtteranceSegmenter()
    val utterances = mutableListOf<FloatArray>()
    VoiceActivityGate.reset()
    VoiceActivityGate.chunkListener = VoiceActivityGate.ChunkListener { chunk, p -> segmenter.feed(chunk, p)?.let(utterances::add) }
    try {
        VoiceActivityGate.feed(audio, 0, audio.size)
    } finally {
        VoiceActivityGate.chunkListener = null
    }
    return VoiceCommandDecoder(model).use { decoder ->
        utterances.flatMap { u ->
            val h = decoder.hear(u)
            heard?.add(h.words.joinToString(" ") { "${it.word}:${"%.2f".format(it.confidence)}" } +
                " (or ${h.alternatives.joinToString(" / ") { a -> a.joinToString(" ") { it.word } }})")
            decoder.decide(h, requireWake)
        }
    }
}
