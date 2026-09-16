package com.example.ytpiano.audio

import android.content.Context
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import dev.kotlinds.fluidsynthkmp.Interpolation
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-quality Grand Piano player using FluidSynth and SoundFonts.
 * Optimized for high stability on Android and Emulators.
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
        
        // Stabilized Audio Configuration
        // periodSize = 512 and periods = 8 are much safer for Emulator/Mobile environments
        // Interpolation.NORMAL is a good balance between quality and CPU.
        player = FluidSynthPlayer(
            AudioConfig(
                sampleRate = 44100,
                interpolation = Interpolation.NORMAL, 
                periodSize = 512, 
                periods = 8 
            )
        ).apply {
            val soundFontId = loadSoundFont(soundFontPath)
            if (soundFontId < 0) {
                isInitialized.set(false)
                return
            }

            programChange(CHANNEL, GRAND_PIANO)
            setGain(0.8f) 
            
            setReverb(
                roomSize = 0.65, 
                damping = 0.5, 
                width = 0.8, 
                level = 0.3
            )
            
            setChorus(
                voiceCount = 3,
                level = 0.5,
                speed = 0.3,
                depth = 4.0
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
     * Stop all notes instantly.
     */
    fun stopAllNotes() {
        for (pitch in 0..127) {
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
