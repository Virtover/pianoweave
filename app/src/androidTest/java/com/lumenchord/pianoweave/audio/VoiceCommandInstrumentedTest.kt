package com.lumenchord.pianoweave.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Voice commands on real speech (synthesized words in the test assets, 16 kHz mono): the Silero voice
 * gate + [UtteranceSegmenter] must pass single words and drop a sentence, and Vosk with the command
 * grammar must recognize each word, and never take one command for another.
 *
 * Downloads the voice model (~40 MB) into the app's files the first time, like turning the feature on.
 */
@RunWith(AndroidJUnit4::class)
class VoiceCommandInstrumentedTest {

    companion object {
        private lateinit var model: Model
        private const val QUIET = 0.03f
        // (gain, noise rms): clean, far away (-30 and -40 dB), and with steady room noise ~20 and ~10 dB under the voice
        private val CONDITIONS = listOf(1f to 0f, QUIET to 0f, 0.01f to 0f, 1f to 0.01f, 1f to 0.03f)
        // Noise as loud as the voice: a word may be missed there, but never taken for another
        private const val LOUD_NOISE = 0.1f

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

    /**
     * Runs [audio] with 0.6 s of silence around it through the voice gate and segmenter, with steady
     * white noise of [noiseRms] over all of it (a fan, room hiss).
     */
    private fun utterancesIn(audio: FloatArray, noiseRms: Float = 0f): List<FloatArray> {
        val pad = FloatArray(9600)
        val stream = pad + audio + pad + pad
        if (noiseRms > 0f) {
            val random = java.util.Random(7)
            for (i in stream.indices) stream[i] += (random.nextGaussian() * noiseRms).toFloat()
        }
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
    private fun commandsIn(
        file: String,
        gain: Float = 1f,
        selah: Boolean = false,
        noiseRms: Float = 0f
    ): Pair<List<VoiceCommand>, String> {
        val audio = readWav(file).also { a -> for (i in a.indices) a[i] *= gain }
        val utterances = utterancesIn(audio, noiseRms)
        val profile = probabilities.joinToString(" ") { "%.2f".format(it) }
        val commands = VoiceCommandDecoder(model).use { decoder ->
            utterances.flatMap { decoder.commands(it, requireWake = selah) }
        }
        return commands to "$file x$gain noise $noiseRms: ${utterances.size} utterances, voice per chunk: $profile"
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
    @Test
    fun selahAloneWakes() {
        for (gain in listOf(1f, QUIET)) {
            val audio = readWav("selah").also { a -> for (i in a.indices) a[i] *= gain }
            val utterances = utterancesIn(audio)
            assertEquals("selah x$gain utterances", 1, utterances.size)
            val words = VoiceCommandDecoder(model).use { VoiceCommand.trimEdgeNoise(it.hear(utterances[0]).words) }
            assertTrue("selah x$gain heard as $words", VoiceCommand.isWakeOnly(words))
        }
    }
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
        VoiceCommandDecoder(model).use { decoder ->
            assertEquals(emptyList<VoiceCommand>(), decoder.commands(readWav("sentence")))
        }
    }

    /**
     * Every phrase, in every synthesized voice (voice/matrix/<phrase>__v<n>.wav), normal and quiet, must
     * come out as exactly its own command: never missed, never taken for another. "no" must do nothing.
     */
    @Test
    fun everyPhraseIsItsOwnCommand() {
        val files = InstrumentationRegistry.getInstrumentation().context.assets.list("voice/matrix")!!.sorted()
        assertTrue(files.isNotEmpty())
        val failures = mutableListOf<String>()
        for (file in files) {
            val phrase = file.substringBefore("__").replace('_', ' ')
            val expected = VoiceCommand.entries.filter { phrase in it.phrases }
            val name = "matrix/" + file.removeSuffix(".wav")
            for ((gain, noise) in CONDITIONS) {
                val (commands, info) = commandsIn(name, gain, noiseRms = noise)
                if (commands != expected) failures += "\"$phrase\" -> $commands (expected $expected); $info"
            }
            val (loud, loudInfo) = commandsIn(name, noiseRms = LOUD_NOISE)
            if (loud.isNotEmpty() && loud != expected) failures += "\"$phrase\" -> $loud (expected $expected or nothing); $loudInfo"
        }
        assertTrue("${failures.size} of ${files.size * (CONDITIONS.size + 1)}:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun noiseAloneIsNothing() {
        VoiceCommandDecoder(model).use { decoder ->
            for (rms in listOf(0.003f, 0.01f, 0.05f)) {
                val words = utterancesIn(FloatArray(48000), rms).flatMap { VoiceCommand.trimEdgeNoise(decoder.hear(it).words) }
                assertTrue("noise $rms heard as $words", !VoiceCommand.hasKnownWord(words))
            }
        }
    }

    /**
     * The song from the phone's own speaker, under a voice that isn't loud (the echo canceller and the
     * ducking on the device take more of it away; this is what's left without them): still no command
     * may be taken for another, and nearly all must be caught.
     */
    @Test
    fun voiceOverPiano() {
        val files = InstrumentationRegistry.getInstrumentation().context.assets.list("voice/matrix")!!.sorted()
            .filter { !it.startsWith("no__") }
        val missed = mutableListOf<String>()
        val wrong = mutableListOf<String>()
        VoiceCommandDecoder(model).use { decoder ->
            for ((gain, pianoRms) in listOf(0.3f to 0.03f, 0.1f to 0.01f, 1f to 0.08f)) {
                for (file in files) {
                    val phrase = file.substringBefore("__").replace('_', ' ')
                    val expected = VoiceCommand.entries.filter { phrase in it.phrases }
                    val audio = readWav("matrix/" + file.removeSuffix(".wav")).also { a -> for (i in a.indices) a[i] *= gain }
                    val pad = FloatArray(9600)
                    val stream = pad + audio + pad + pad
                    val piano = piano(stream.size, pianoRms)
                    for (i in stream.indices) stream[i] += piano[i]
                    val commands = utterancesOf(stream).flatMap { decoder.commands(it) }
                    if (commands != expected) (if (commands.isEmpty()) missed else wrong) += "$file x$gain piano $pianoRms -> $commands"
                }
            }
        }
        assertTrue("taken for another command: $wrong", wrong.isEmpty())
        assertTrue("missed ${missed.size} of ${files.size * 3}: $missed", missed.size <= files.size * 3 / 10)
    }

    /** Piano-like chords (decaying harmonics, three notes every 0.2-0.5 s) at [rms]. */
    private fun piano(length: Int, rms: Float): FloatArray {
        val out = FloatArray(length)
        val random = java.util.Random(3)
        val notes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72, 48, 52, 55)
        var start = 0
        while (start < length) {
            repeat(3) {
                val f = 440.0 * Math.pow(2.0, (notes[random.nextInt(notes.size)] - 69) / 12.0)
                for (t in 0 until minOf(16000, length - start)) {
                    val sec = t / 16000.0
                    var v = 0.0
                    for (k in 1..8) v += Math.sin(2 * Math.PI * f * k * sec) / k * Math.exp(-sec * (1.5 + k * 0.6))
                    out[start + t] += (v * minOf(1.0, t / 80.0)).toFloat()
                }
            }
            start += 3000 + random.nextInt(5000)
        }
        val current = Math.sqrt(out.map { it.toDouble() * it }.average()).toFloat()
        for (i in out.indices) out[i] *= rms / current
        return out
    }

    /** The utterances voice commands would cut from [stream] (the level-boosted voice gate). */
    private fun utterancesOf(stream: FloatArray): List<FloatArray> {
        val segmenter = UtteranceSegmenter()
        val found = mutableListOf<FloatArray>()
        VoiceActivityGate.reset()
        VoiceActivityGate.chunkListener = VoiceActivityGate.ChunkListener { chunk, p -> segmenter.feed(chunk, p)?.let(found::add) }
        try {
            VoiceActivityGate.feed(stream, 0, stream.size)
        } finally {
            VoiceActivityGate.chunkListener = null
        }
        return found
    }

    @Test
    fun voiceOnlyListeningUsesTheEchoCanceller() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(AcousticNoteDetector.startVoice(context))
        try {
            Thread.sleep(300)
            val log = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "audiobridge")).inputStream.bufferedReader().readText()
            assertTrue("last opened: " + log.lines().lastOrNull { "opened" in it }, log.lines().last { "opened" in it }.contains("VOICE_COMMUNICATION"))
        } finally {
            AcousticNoteDetector.stopVoice()
        }
    }
}
