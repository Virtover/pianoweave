package com.lumenchord.pianoweave.audio

/** The commands voice commands take from [audio] (16 kHz), listening to all of it as the app does. */
internal fun commandsHeardIn(model: org.vosk.Model, audio: FloatArray, requireWake: Boolean = false, heard: MutableList<String>? = null): List<VoiceCommand> =
    CommandStream(model).use { stream ->
        val found = mutableListOf<VoiceCommand>()
        fun take(h: VoiceCommandDecoder.Heard?) {
            h ?: return
            heard?.add(h.words.joinToString(" ") { "${it.word}:${"%.2f".format(it.confidence)}" } +
                " (or ${h.alternatives.joinToString(" / ") { a -> a.joinToString(" ") { it.word } }})")
            found += VoiceCommandDecoder.decide(h, requireWake)
        }
        var off = 0
        while (off < audio.size) {
            val n = minOf(512, audio.size - off)
            take(stream.feed(audio.copyOfRange(off, off + n)))
            off += n
        }
        take(stream.finish())
        found
    }
