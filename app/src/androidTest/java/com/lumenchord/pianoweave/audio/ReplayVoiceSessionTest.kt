package com.lumenchord.pianoweave.audio

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Diagnostic, not a check: replays a session recorded by [VoiceDebugRecorder] (copied to the app's
 * files as voice-debug/replay.pcm) and logs (tag ReplayVoice) what was said, from the speech model
 * with its full vocabulary, next to what voice commands made of it: each utterance cut, why speech
 * was dropped, and the voice gate's rating around every word. Skipped when there's no recording.
 */
@RunWith(AndroidJUnit4::class)
class ReplayVoiceSessionTest {
    private val tag = "ReplayVoice"

    @Test
    fun replay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "voice-debug/replay.pcm")
        assumeTrue("no recording", file.exists() && VoiceModelStore.isInstalled(context))
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val audio = FloatArray(bytes.capacity() / 2) { bytes.getShort(it * 2) / 32768f }
        // -e model <folder in files/voice-test> replays with another speech model, to compare
        val modelDir = InstrumentationRegistry.getArguments().getString("model")
            ?.let { File(context.filesDir, "voice-test/$it") } ?: VoiceModelStore.modelDir(context)
        Log.i(tag, "MODEL ${modelDir.name}")
        Model(modelDir.absolutePath).use { model ->
            // What was said
            Recognizer(model, 16000f).use { rec ->
                rec.setWords(true)
                val pcm = ShortArray(audio.size) { (audio[it] * 32767f).toInt().toShort() }
                var off = 0
                fun log(json: String) {
                    val words = JsonParser.parseString(json).asJsonObject.getAsJsonArray("result") ?: return
                    Log.i(tag, "SAID " + words.joinToString(" ") { w ->
                        val o = w.asJsonObject
                        "%s@%.2f(%.2f)".format(o["word"].asString, o["start"].asFloat, o["conf"].asFloat)
                    })
                }
                while (off < pcm.size) {
                    val n = minOf(4000, pcm.size - off)
                    if (rec.acceptWaveForm(pcm.copyOfRange(off, off + n), n)) log(rec.result)
                    off += n
                }
                log(rec.finalResult)
            }

            // The command grammar listening all the time, cut only by its own endpoints
            Recognizer(model, 16000f, VoiceCommand.grammarJson).use { rec ->
                rec.setWords(true)
                val pcm = ShortArray(audio.size) { (audio[it] * 32767f).toInt().toShort() }
                var off = 0
                fun log(json: String) {
                    val words = JsonParser.parseString(json).asJsonObject.getAsJsonArray("result") ?: return
                    Log.i(tag, "GRAM " + words.joinToString(" ") { w ->
                        val o = w.asJsonObject
                        "%s@%.2f-%.2f(%.2f)".format(o["word"].asString, o["start"].asFloat, o["end"].asFloat, o["conf"].asFloat)
                    })
                }
                val started = System.nanoTime()
                while (off < pcm.size) {
                    val n = minOf(1600, pcm.size - off)
                    if (rec.acceptWaveForm(pcm.copyOfRange(off, off + n), n)) log(rec.result)
                    off += n
                }
                log(rec.finalResult)
                Log.i(tag, "GRAM took %d ms for %d s".format((System.nanoTime() - started) / 1_000_000, audio.size / 16000))
            }

            // What voice commands now make of it
            VoiceActivityGate.initialize(context)
            InstrumentationRegistry.getArguments().getString("enhance")?.let { VoiceActivityGate.enhanceVoice = it.toBoolean() }
            val heard = mutableListOf<String>()
            val started = System.nanoTime()
            Log.i(tag, "NOW " + commandsHeardIn(model, audio, heard = heard))
            Log.i(tag, "NOW took %d ms for %d s, enhance %b".format((System.nanoTime() - started) / 1_000_000, audio.size / 16000, VoiceActivityGate.enhanceVoice))
            heard.forEach { Log.i(tag, "NOW heard $it") }

            // What voice commands made of it before (the voice gate's utterances)
            VoiceActivityGate.initialize(context)
            VoiceActivityGate.reset()
            var chunk = 0
            val segmenter = UtteranceSegmenter { reason -> Log.i(tag, "DROP @%.2f %s".format(chunk * 0.032f, reason)) }
            val probs = mutableListOf<Float>()
            val utterances = mutableListOf<Pair<Int, FloatArray>>()
            VoiceActivityGate.chunkListener = VoiceActivityGate.ChunkListener { c, p ->
                probs += p
                segmenter.feed(c, p)?.let { utterances += chunk to it }
                chunk++
            }
            try {
                VoiceActivityGate.feed(audio, 0, audio.size)
            } finally {
                VoiceActivityGate.chunkListener = null
            }
            VoiceCommandDecoder(model).use { decoder ->
                for ((end, u) in utterances) {
                    val h = decoder.hear(u)
                    val c = decoder.decide(h, requireWake = false)
                    Log.i(tag, "UTT %.2f-%.2f %s (or %s) -> %s".format(
                        end * 0.032f - u.size / 16000f, end * 0.032f,
                        h.words.joinToString(" ") { "%s:%.2f".format(it.word, it.confidence) },
                        h.alternatives.joinToString(" / ") { a -> a.joinToString(" ") { it.word } }, c
                    ))
                }
            }
            // Voice rating per second, as max per 0.5 s, for lining up with SAID
            for (i in probs.indices step 16) {
                val window = probs.subList(i, minOf(probs.size, i + 16))
                Log.i(tag, "VAD %.1f %.2f".format(i * 0.032f, window.max()))
            }
        }
    }
}
