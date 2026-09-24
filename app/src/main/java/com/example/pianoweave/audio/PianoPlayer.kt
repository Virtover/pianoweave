package com.example.pianoweave.audio

import android.content.Context
import android.util.Log
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

    private const val TAG = "PianoPlayer"
    private const val SOUND_FONT = "GeneralUser-GS.sf2"
    private const val CHANNEL = 0
    private const val GRAND_PIANO = 0

    private var player: FluidSynthPlayer? = null
    private val isInitialized = AtomicBoolean(false)
    private var appContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (isInitialized.get() && player != null) return

        try {
            val soundFontPath = copySoundFont(context)
            
            player?.close()
            player = null

            val newPlayer = FluidSynthPlayer(
                AudioConfig(
                    sampleRate = 44100,
                    interpolation = Interpolation.NORMAL, 
                    periodSize = 512, 
                    periods = 8 
                )
            ).apply {
                val soundFontId = loadSoundFont(soundFontPath)
                if (soundFontId < 0) {
                    Log.e(TAG, "Failed to load SoundFont, id: $soundFontId")
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
            player = newPlayer
            isInitialized.set(true)
            Log.i(TAG, "PianoPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PianoPlayer", e)
            player?.close()
            player = null
            isInitialized.set(false)
        }
    }

    fun noteOn(pitch: Int, velocity: Int = 80) {
        if (pitch !in 0..127) return
        if (player == null) {
            appContext?.let { initialize(it) }
        }
        try {
            player?.noteOn(CHANNEL, pitch, velocity.coerceIn(1, 127))
        } catch (e: Exception) {
            Log.e(TAG, "Error playing noteOn($pitch)", e)
            player = null
            isInitialized.set(false)
        }
    }

    fun noteOff(pitch: Int) {
        if (pitch !in 0..127) return
        try {
            player?.noteOff(CHANNEL, pitch)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing noteOff($pitch)", e)
        }
    }

    /**
     * Stop all notes instantly.
     */
    fun stopAllNotes() {
        for (pitch in 0..127) {
            try {
                player?.noteOff(CHANNEL, pitch)
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        try {
            player?.close()
        } catch (_: Exception) {}
        player = null
        isInitialized.set(false)
    }

    private fun copySoundFont(context: Context): String {
        val destination = File(context.filesDir, SOUND_FONT)
        if (!destination.exists() || destination.length() == 0L) {
            context.assets.open(SOUND_FONT).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destination.absolutePath
    }
}
