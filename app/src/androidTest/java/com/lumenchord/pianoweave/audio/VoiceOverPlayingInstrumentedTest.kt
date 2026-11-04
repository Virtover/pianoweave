package com.lumenchord.pianoweave.audio

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/**
 * Voice commands said while someone plays the piano next to the phone (an acoustic piano: nothing the
 * app can turn down). The playing is rendered with the app's own grand piano SoundFont, as practice
 * looks: a melody over chords or broken chords, notes held like with the pedal, ~2-8 notes a second.
 * Every command phrase is spoken over it at several voice-to-piano levels, at several places in the
 * playing. None may ever be taken for another command. Misses are allowed only rarely, and more
 * where the piano is louder than the voice ([MAX_MISSED]).
 *
 * The rendering isn't bit-exact from run to run (the synth's own audio output takes a few frames
 * while it renders), so counts vary by one or two. Seen listening continuously ([CommandStream]):
 * 0-3 missed of 378 at +10, +5 and 0 dB, 10 at -5 dB; with the voice gate cutting utterances at 0.15:
 * 0-3 and 17-23; at its old 0.4: 2-5 and 51-59.
 */
@RunWith(AndroidJUnit4::class)
class VoiceOverPlayingInstrumentedTest {

    companion object {
        private const val TAG = "VoiceOverPlaying"
        private const val RATE = 16000
        private lateinit var model: Model
        private lateinit var playing: List<FloatArray>

        /** Voice level over the piano's (dB) -> most misses allowed, as a share of the tries. */
        private val MAX_MISSED = mapOf(10f to 0.01f, 5f to 0.01f, 0f to 0.02f, -5f to 0.08f)

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (!VoiceModelStore.isInstalled(context)) runBlocking { VoiceModelStore.download(context) { } }
            model = Model(VoiceModelStore.modelDir(context).absolutePath)
            VoiceActivityGate.initialize(context)
            playing = (0 until 3).map { renderPractice(context, seed = it, seconds = 40) }
        }

        @AfterClass
        @JvmStatic
        fun release() {
            model.close()
        }

        /** [seconds] of practice-like playing, mono 16 kHz, normalized to rms 0.1. */
        private fun renderPractice(context: android.content.Context, seed: Int, seconds: Int): FloatArray {
            val sf2 = File(context.cacheDir, "test_piano.sf2")
            if (!sf2.exists()) context.assets.open("Full Grand Piano.sf2").use { i -> sf2.outputStream().use { i.copyTo(it) } }
            val random = Random(seed.toLong())
            // (time in samples, key, velocity or 0 for off)
            val events = mutableListOf<Triple<Int, Int, Int>>()
            val scale = intArrayOf(0, 2, 4, 5, 7, 9, 11)
            val root = 55 + random.nextInt(8)
            var t = 0
            val beat = RATE * 60 / (70 + random.nextInt(60))
            while (t < seconds * RATE) {
                // Left hand: a chord or broken chord per bar, held (pedal)
                val chordRoot = root - 12 + scale[random.nextInt(scale.size)]
                val chord = listOf(chordRoot, chordRoot + 4 + random.nextInt(2) - 1, chordRoot + 7)
                val broken = random.nextBoolean()
                chord.forEachIndexed { i, k ->
                    val on = t + if (broken) i * beat / 2 else 0
                    events += Triple(on, k, 45 + random.nextInt(40))
                    events += Triple(t + 4 * beat - 200, k, 0)
                }
                // Right hand: a melody over the bar
                var m = t
                while (m < t + 4 * beat) {
                    val len = beat / listOf(1, 2, 2, 2, 4)[random.nextInt(5)]
                    val key = root + 12 + scale[random.nextInt(scale.size)] + 12 * (random.nextInt(3) - 1).coerceAtMost(0)
                    events += Triple(m, key, 55 + random.nextInt(50))
                    events += Triple(m + len * 3 / 2, key, 0)
                    m += len
                }
                t += 4 * beat
            }
            events.sortBy { it.first }

            val player = FluidSynthPlayer(AudioConfig(sampleRate = RATE, periodSize = 512, periods = 2))
            try {
                player.setGain(0.5f)
                check(player.loadSoundFont(sf2.absolutePath) >= 0)
                player.programChange(0, 0)
                player.setReverb(0.55, 0.4, 1.0, 0.22)
                val out = FloatArray(seconds * RATE)
                var pos = 0
                var e = 0
                while (pos < out.size) {
                    while (e < events.size && events[e].first <= pos) {
                        val (_, key, vel) = events[e++]
                        if (vel > 0) player.noteOn(0, key, vel) else player.noteOff(0, key)
                    }
                    val next = if (e < events.size) minOf(events[e].first, out.size) else out.size
                    val frames = maxOf(64, minOf(next - pos, 1024)).coerceAtMost(out.size - pos)
                    val stereo = player.renderFloat(frames)
                    for (i in 0 until frames) out[pos + i] = (stereo[2 * i] + stereo[2 * i + 1]) / 2f
                    pos += frames
                }
                val rms = Math.sqrt(out.map { it.toDouble() * it }.average()).toFloat()
                for (i in out.indices) out[i] *= 0.1f / rms
                return out
            } finally {
                player.close()
            }
        }
    }

    private fun readWav(name: String): FloatArray {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("voice/$name.wav").readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            if (id == "data") return FloatArray(size / 2) { buf.getShort(pos + 8 + it * 2) / 32768f }
            pos += 8 + size + (size and 1)
        }
        error("No data chunk in $name.wav")
    }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
        var sum = 0.0
        for (i in from until to) sum += a[i].toDouble() * a[i]
        return Math.sqrt(sum / (to - from)).toFloat()
    }

    /** Result of one phrase over the playing: what came out, and what was heard. */
    private fun run(voice: FloatArray, piano: FloatArray, at: Int, snrDb: Float): Pair<List<VoiceCommand>, String> {
        // Voice level relative to the piano's level around it (the 2 s it's said in)
        val around = rms(piano, at, minOf(piano.size, at + 2 * RATE))
        val gain = around * Math.pow(10.0, snrDb / 20.0).toFloat() / rms(voice).coerceAtLeast(1e-6f)
        val start = at - 3 * RATE
        val stream = piano.copyOfRange(start, at + voice.size + 2 * RATE)
        for (i in voice.indices) stream[3 * RATE + i] += voice[i] * gain
        val heard = mutableListOf<String>()
        val commands = commandsHeardIn(model, stream, heard = heard)
        return commands to "heard $heard"
    }

    /**
     * The playing alone is never a command.
     */
    @Test
    fun playingAloneIsNoCommand() {
        val commands = mutableListOf<String>()
        for ((p, piano) in playing.withIndex()) {
            val heard = mutableListOf<String>()
            val c = commandsHeardIn(model, piano, heard = heard)
            if (c.isNotEmpty()) commands += "piano $p: $c from $heard"
        }
        Log.i(TAG, "SUMMARY playing alone: commands $commands")
        assertTrue("commands from playing alone: $commands", commands.isEmpty())
    }

    @Test
    fun commandsOverPracticePlaying() {
        val files = InstrumentationRegistry.getInstrumentation().context.assets.list("voice/matrix")!!.sorted()
            .filter { !it.startsWith("no__") }
        val snrs = MAX_MISSED.keys.toList()
        val missed = mutableMapOf<Float, MutableList<String>>()
        val wrong = mutableListOf<String>()
        var total = 0
        run {
            for (snr in snrs) {
                for (file in files) {
                    val phrase = file.substringBefore("__").replace('_', ' ')
                    val expected = VoiceCommand.entries.filter { phrase in it.phrases }
                    val voice = readWav("matrix/" + file.removeSuffix(".wav"))
                    for ((p, piano) in playing.withIndex()) {
                        for (k in 0 until 3) {
                            val at = 4 * RATE + (p * 7 + k * 11 + file.hashCode().and(7)) * RATE % (piano.size - 8 * RATE)
                            total++
                            val (commands, info) = run(voice, piano, at, snr)
                            if (commands != expected) {
                                val line = "$file snr $snr piano $p@${at / RATE}s -> $commands; $info"
                                if (commands.isEmpty()) missed.getOrPut(snr) { mutableListOf() } += line else wrong += line
                            }
                        }
                    }
                }
            }
        }
        val perSnr = total / snrs.size
        val summary = snrs.joinToString(", ") { "${it} dB: missed ${missed[it]?.size ?: 0}/$perSnr" } + ", wrong ${wrong.size}"
        Log.i(TAG, "SUMMARY $summary")
        for (line in wrong) Log.i(TAG, "WRONG $line")
        for (snr in snrs) for (line in missed[snr].orEmpty()) Log.i(TAG, "MISSED $line")
        assertTrue("taken for another command: $wrong", wrong.isEmpty())
        for (snr in snrs) assertTrue(summary, (missed[snr]?.size ?: 0) <= perSnr * MAX_MISSED.getValue(snr))
    }
}
