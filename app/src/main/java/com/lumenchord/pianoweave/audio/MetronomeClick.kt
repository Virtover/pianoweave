package com.lumenchord.pianoweave.audio

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The metronome's woodblock. The piano SoundFont has no percussion, so the two strokes (a plain beat and a
 * higher, stronger first beat of the bar) are synthesized once into a tiny SoundFont of their own, which
 * [PianoPlayer] loads beside the piano and plays on the drum channel. The click thus shares the piano's
 * output: the same latency (it stays on time with the notes), volume, voice ducking and hall reverb.
 */
object MetronomeClick {
    /** General MIDI's drum channel, where FluidSynth looks for bank 128, the bank this SoundFont's kit is in. */
    const val CHANNEL = 9
    /** The GM keys of a low and a high wood block. */
    const val BEAT_KEY = 77
    const val BAR_KEY = 76

    private const val SAMPLE_RATE = 44_100
    // Versioned: a changed sound gets a new file instead of a stale cached one
    private const val FILE_NAME = "metronome_v1.sf2"

    fun play(isBarStart: Boolean) {
        if (isBarStart) PianoPlayer.click(BAR_KEY, velocity = 112) else PianoPlayer.click(BEAT_KEY, velocity = 88)
    }

    /** The click SoundFont, written to app storage the first time. */
    fun soundFontFile(context: Context): File {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) {
            val temporary = File(context.filesDir, "$FILE_NAME.tmp")
            temporary.writeBytes(
                soundFont(
                    listOf(
                        ClickSample("Wood beat", BEAT_KEY, woodblock(fundamentalHz = 940.0, seed = 1)),
                        ClickSample("Wood bar", BAR_KEY, woodblock(fundamentalHz = 1240.0, seed = 2))
                    )
                )
            )
            temporary.renameTo(file)
        }
        return file
    }

    /**
     * One woodblock stroke, by modal synthesis: a few inharmonic modes of a hollow wooden block, the higher
     * ones dying faster, struck by a short burst of filtered noise (the stick), with a little low body.
     * The strike sits a hair sharp and settles within milliseconds, as a struck block does.
     */
    internal fun woodblock(fundamentalHz: Double, seed: Int): ShortArray {
        val ratios = doubleArrayOf(1.0, 2.32, 3.98, 6.1)
        val amplitudes = doubleArrayOf(1.0, 0.35, 0.18, 0.08)
        val decays = doubleArrayOf(0.050, 0.020, 0.010, 0.006)
        val length = (SAMPLE_RATE * 0.30).toInt()
        val fadeStart = length - (SAMPLE_RATE * 0.03).toInt()
        val random = Random(seed)
        val phases = DoubleArray(ratios.size)
        var previousNoise = 0.0
        var stick = 0.0
        val signal = DoubleArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            val glide = 1.0 + 0.015 * exp(-t / 0.004)
            var value = 0.0
            for (mode in ratios.indices) {
                phases[mode] += 2 * PI * fundamentalHz * ratios[mode] * glide / SAMPLE_RATE
                value += amplitudes[mode] * exp(-t / decays[mode]) * sin(phases[mode])
            }
            value += 0.12 * exp(-t / 0.02) * sin(2 * PI * 180.0 * t)
            // Stick: white noise, high-passed (difference) then softened (one-pole low-pass)
            val noise = random.nextDouble(-1.0, 1.0)
            stick += 0.45 * ((noise - previousNoise) - stick)
            previousNoise = noise
            value += 0.5 * exp(-t / 0.0007) * stick
            // Soft start (0.2 ms) against a click from the jump, and a fade at the very end
            value *= (t / 0.0002).coerceAtMost(1.0)
            if (i >= fadeStart) value *= 0.5 * (1 + kotlin.math.cos(PI * (i - fadeStart) / (length - fadeStart)))
            signal[i] = value
        }
        val peak = signal.fold(0.0) { acc, v -> max(acc, abs(v)) }
        // Headroom: the velocity sets the loudness, and the click plays over the piano
        val scale = 0.55 * Short.MAX_VALUE / peak
        return ShortArray(length) { (signal[it] * scale).toInt().toShort() }
    }

    internal class ClickSample(val name: String, val key: Int, val pcm: ShortArray)

    /**
     * A minimal SoundFont 2: one preset (bank 128, program 0, a drum kit) whose instrument plays each
     * sample on its own key at its recorded pitch, unlooped, with a little reverb.
     */
    internal fun soundFont(samples: List<ClickSample>): ByteArray {
        // Every sample is followed by 46 zero points, as the format requires
        val smpl = ByteArrayOutputStream()
        val starts = mutableListOf<Int>()
        var position = 0
        for (sample in samples) {
            starts += position
            smpl.write(le(sample.pcm.size * 2) { buffer -> sample.pcm.forEach { buffer.putShort(it) } })
            smpl.write(ByteArray(46 * 2))
            position += sample.pcm.size + 46
        }

        val phdr = le(38 * 2) {
            it.put(name20("Metronome")); it.putShort(0); it.putShort(128); it.putShort(0); it.putInt(0); it.putInt(0); it.putInt(0)
            it.put(name20("EOP")); it.putShort(0); it.putShort(0); it.putShort(1); it.putInt(0); it.putInt(0); it.putInt(0)
        }
        val pbag = le(4 * 2) { it.putShort(0); it.putShort(0); it.putShort(1); it.putShort(0) }
        val pmod = ByteArray(10)
        val pgen = le(4 * 2) { it.putShort(GEN_INSTRUMENT); it.putShort(0); it.putShort(0); it.putShort(0) }
        val inst = le(22 * 2) {
            it.put(name20("Metronome")); it.putShort(0)
            it.put(name20("EOI")); it.putShort(samples.size.toShort())
        }
        val gensPerZone = 3
        val ibag = le(4 * (samples.size + 1)) {
            for (zone in 0..samples.size) {
                it.putShort((zone * gensPerZone).toShort()); it.putShort(0)
            }
        }
        val imod = ByteArray(10)
        val igen = le(4 * (samples.size * gensPerZone + 1)) {
            samples.forEachIndexed { index, sample ->
                // Key range first and the sample last, as the format requires
                it.putShort(GEN_KEY_RANGE); it.put(sample.key.toByte()); it.put(sample.key.toByte())
                it.putShort(GEN_REVERB_SEND); it.putShort(120) // 12 %, in tenths of a percent
                it.putShort(GEN_SAMPLE_ID); it.putShort(index.toShort())
            }
            it.putShort(0); it.putShort(0)
        }
        val shdr = le(46 * (samples.size + 1)) {
            samples.forEachIndexed { index, sample ->
                val start = starts[index]
                val end = start + sample.pcm.size
                it.put(name20(sample.name)); it.putInt(start); it.putInt(end); it.putInt(start); it.putInt(end - 1)
                it.putInt(SAMPLE_RATE); it.put(sample.key.toByte()); it.put(0); it.putShort(0); it.putShort(1)
            }
            it.put(name20("EOS")); it.putInt(0); it.putInt(0); it.putInt(0); it.putInt(0); it.putInt(0)
            it.put(0); it.put(0); it.putShort(0); it.putShort(0)
        }

        val info = list(
            "INFO",
            chunk("ifil", le(4) { it.putShort(2); it.putShort(1) }),
            chunk("isng", zeroTerminated("EMU8000")),
            chunk("INAM", zeroTerminated("Piano Weave Metronome"))
        )
        val sdta = list("sdta", chunk("smpl", smpl.toByteArray()))
        val pdta = list(
            "pdta",
            chunk("phdr", phdr), chunk("pbag", pbag), chunk("pmod", pmod), chunk("pgen", pgen),
            chunk("inst", inst), chunk("ibag", ibag), chunk("imod", imod), chunk("igen", igen), chunk("shdr", shdr)
        )
        val body = "sfbk".toByteArray() + info + sdta + pdta
        return "RIFF".toByteArray() + le(4) { it.putInt(body.size) } + body
    }

    private const val GEN_REVERB_SEND: Short = 16
    private const val GEN_INSTRUMENT: Short = 41
    private const val GEN_KEY_RANGE: Short = 43
    private const val GEN_SAMPLE_ID: Short = 53

    private fun le(size: Int, fill: (ByteBuffer) -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).also(fill).array()

    private fun chunk(id: String, data: ByteArray): ByteArray {
        val padded = if (data.size % 2 == 0) data else data + 0
        return id.toByteArray() + le(4) { it.putInt(data.size) } + padded
    }

    private fun list(type: String, vararg chunks: ByteArray): ByteArray =
        chunk("LIST", type.toByteArray() + chunks.fold(ByteArray(0)) { acc, c -> acc + c })

    private fun name20(name: String): ByteArray = name.toByteArray().copyOf(20)

    /** A zero-terminated string padded to an even length, as INFO strings are stored. */
    private fun zeroTerminated(text: String): ByteArray {
        val bytes = text.toByteArray() + 0
        return if (bytes.size % 2 == 0) bytes else bytes + 0
    }
}
