package com.example.ytpiano.audio

import android.content.Context
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import dev.kotlinds.fluidsynthkmp.Interpolation
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-quality Grand Piano player using FluidSynth and SoundFonts.
 * Optimized for ultra-low latency and a rich, professional grand piano sound.
 */
object PianoPlayer : AutoCloseable {

    private const val SOUND_FONT = "GeneralUser-GS.sf2"
    private const val CHANNEL = 0
    private const val GRAND_PIANO = 0

    private var player: FluidSynthPlayer? = null
    private val isInitialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return

        val soundFontPath = copySoundFont(context)
        
        player = FluidSynthPlayer(
            AudioConfig(
                sampleRate = 44100,
                interpolation = Interpolation.HIGH, 
                periodSize = 64, 
                periods = 2 
            )
        ).apply {
            val soundFontId = loadSoundFont(soundFontPath)
            if (soundFontId < 0) {
                isInitialized.set(false)
                return
            }

            programChange(CHANNEL, GRAND_PIANO)
            setGain(0.85f) 
            
            setReverb(
                roomSize = 0.75, 
                damping = 0.4, 
                width = 0.9, 
                level = 0.35 
            )
            
            setChorus(
                voiceCount = 3,
                level = 1.2,
                speed = 0.3,
                depth = 8.0
            )
        }
    }

    fun noteOn(pitch: Int, velocity: Int = 80) {
        if (pitch !in 0..127) return
        player?.noteOn(CHANNEL, pitch, velocity.coerceIn(1, 127))
    }

    fun noteOff(pitch: Int) {
        if (pitch !in 0..127) return
        player?.noteOff(CHANNEL, pitch)
    }

    /**
     * Instantly silence all active notes. 
     * Useful for pausing playback or clearing the stage.
     */
    fun stopAllNotes() {
        // FluidSynth kmp doesn't have a direct "all notes off" MIDI controller call exposed,
        // so we loop through the piano range (21-108) to silence hanging notes.
        for (pitch in 21..108) {
            player?.noteOff(CHANNEL, pitch)
        }
    }

    override fun close() {
        player?.close()
        player = null
        isInitialized.set(false)
    }

    private fun copySoundFont(context: Context): String {
        val destination = File(context.filesDir, SOUND_FONT)
        if (!destination.exists()) {
            context.assets.open(SOUND_FONT).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destination.absolutePath
    }
}
