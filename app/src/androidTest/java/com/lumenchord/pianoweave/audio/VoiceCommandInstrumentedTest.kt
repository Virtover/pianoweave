package com.lumenchord.pianoweave.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Voice commands on real speech (synthesized words in the test assets, 16 kHz mono): the Silero voice
 * gate + [UtteranceSegmenter] must pass single words and drop a sentence, and Vosk with the command
 * grammar must recognize each word above its confidence threshold.
 *
 * Downloads the voice model (~40 MB) into the app's files the first time, like turning the feature on.
 */
@RunWith(AndroidJUnit4::class)
class VoiceCommandInstrumentedTest {

    companion object {
        private lateinit var model: Model
        private const val QUIET = 0.03f

        @BeforeClass
        @JvmStatic
        fun loadModel() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (!VoiceModelStore.isInstalled(context)) runBlocking { VoiceModelStore.download(context) { } }
            model = Model(VoiceModelStore.modelDir(context).absolutePath)
            VoiceActivityGate.initialize(context)
        }

        @AfterClass
        @JvmStatic
        fun release() {
            model.close()
        }
    }

    /** The data chunk of a 16-bit mono WAV in the test assets, as -1..1 floats. */
    private fun readWav(name: String): FloatArray {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("voice/$name.wav").readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            if (id == "data") {
                return FloatArray(size / 2) { buf.getShort(pos + 8 + it * 2) / 32768f }
            }
            pos += 8 + size + (size and 1)
        }
        error("No data chunk in $name.wav")
    }

    // Voice probability per 32 ms chunk of the last run, for failure messages
    private val probabilities = mutableListOf<Float>()

    /** Runs [audio] with 0.6 s of silence around it through the voice gate and segmenter. */
    private fun utterancesIn(audio: FloatArray): List<FloatArray> {
        val pad = FloatArray(9600)
        val stream = pad + audio + pad + pad
        val segmenter = UtteranceSegmenter()
        val found = mutableListOf<FloatArray>()
        probabilities.clear()
        VoiceActivityGate.reset()
        VoiceActivityGate.chunkListener = VoiceActivityGate.ChunkListener { chunk, p ->
            probabilities.add(p)
            segmenter.feed(chunk, p)?.let(found::add)
        }
        try {
            VoiceActivityGate.feed(stream, 0, stream.size)
        } finally {
            VoiceActivityGate.chunkListener = null
        }
        return found
    }

    /** All commands heard in [file] (an utterance each, or one per pause between words). */
    private fun commandsIn(file: String, gain: Float = 1f, selah: Boolean = false): Pair<List<VoiceCommand>, String> {
        val audio = readWav(file).also { a -> for (i in a.indices) a[i] *= gain }
        val utterances = utterancesIn(audio)
        val profile = probabilities.joinToString(" ") { "%.2f".format(it) }
        val commands = VoiceCommandRecognizer.createRecognizer(model).use { rec ->
            utterances.flatMap { VoiceCommandRecognizer.recognize(rec, it, requireWake = selah) }
        }
        return commands to "$file x$gain: ${utterances.size} utterances, voice per chunk: $profile"
    }

    private fun assertHeard(file: String, vararg expected: VoiceCommand, selah: Boolean = false) {
        val (commands, info) = commandsIn(file, selah = selah)
        assertEquals(info, expected.toList(), commands)
        // The same words ~30 dB quieter, like a voice across the room on the unprocessed mic
        val (quiet, quietInfo) = commandsIn(file, QUIET, selah)
        assertEquals(quietInfo, expected.toList(), quiet)
    }

    @Test fun pause() = assertHeard("pause", VoiceCommand.PAUSE)
    @Test fun play() = assertHeard("play", VoiceCommand.PLAY)
    @Test fun loop() = assertHeard("loop", VoiceCommand.LOOP)
    @Test fun again() = assertHeard("again", VoiceCommand.AGAIN)
    @Test fun slower() = assertHeard("slower", VoiceCommand.SLOWER)
    @Test fun waitMode() = assertHeard("wait", VoiceCommand.WAIT)
    @Test fun fullscreen() = assertHeard("fullscreen", VoiceCommand.FULLSCREEN)
    @Test fun fullscreenWait() = assertHeard("fullscreen_wait", VoiceCommand.FULLSCREEN, VoiceCommand.WAIT)
    @Test fun fullscreenPlay() = assertHeard("fullscreen_play", VoiceCommand.FULLSCREEN, VoiceCommand.PLAY)
    @Test fun fasterThreeTimes() = assertHeard("faster3", VoiceCommand.FASTER, VoiceCommand.FASTER, VoiceCommand.FASTER)

    @Test fun back() = assertHeard("back", VoiceCommand.BACK)
    @Test fun goBack() = assertHeard("go_back", VoiceCommand.BACK)
    @Test fun rewind() = assertHeard("rewind", VoiceCommand.BACK)
    @Test fun forward() = assertHeard("forward", VoiceCommand.FORWARD)
    @Test fun backBack() = assertHeard("back_back", VoiceCommand.BACK, VoiceCommand.BACK)
    @Test fun loopStart() = assertHeard("loop_start", VoiceCommand.LOOP_START)
    @Test fun loopEnd() = assertHeard("loop_end", VoiceCommand.LOOP_END)
    @Test fun selahAlone() = assertHeard("selah", VoiceCommand.PAUSE, selah = true)
    @Test fun selahFullscreenWait() =
        assertHeard("selah_fullscreen_wait", VoiceCommand.FULLSCREEN, VoiceCommand.WAIT, selah = true)
    @Test fun selahPlay() = assertHeard("selah_play", VoiceCommand.PLAY, selah = true)
    // In Selah mode a bare command is ignored
    @Test fun bareCommandInSelahMode() = assertHeard("pause", selah = true)

    @Test
    fun sentenceIsNotACommand() {
        val (commands, info) = commandsIn("sentence")
        assertEquals(info, emptyList<VoiceCommand>(), commands)
        // Even given to the recognizer whole, it doesn't come out as commands
        VoiceCommandRecognizer.createRecognizer(model).use { rec ->
            assertEquals(emptyList<VoiceCommand>(), VoiceCommandRecognizer.recognize(rec, readWav("sentence")))
        }
    }
}
