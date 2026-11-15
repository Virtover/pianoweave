package com.lumenchord.pianoweave.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumenchord.pianoweave.audio.PianoPlayer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object MidiInputManager {

    val pressedKeys = mutableStateListOf<Int>()
    val lastPressTimestamps = ConcurrentHashMap<Int, Long>()
    val consumedPressTimestamps = ConcurrentHashMap<Int, Long>()

    private val virtualPresses = ConcurrentHashMap.newKeySet<Int>()
    private val externalPresses = ConcurrentHashMap.newKeySet<Int>()

    private var activeDevice: MidiDevice? = null
    private var activePort: MidiOutputPort? = null
    /** Where the app sends notes to the device (e.g. a computer the phone is plugged into in USB MIDI mode). */
    @Volatile
    private var activeInputPort: MidiInputPort? = null
    private var pendingDeviceId: Int? = null
    private var midiManager: MidiManager? = null
    private val pressedKeysMutex = ReentrantLock()

    /** True while a MIDI keyboard is connected and sending notes. Observable from Compose. */
    var isConnected by mutableStateOf(false)
        private set

    fun isMidiDeviceConnected(): Boolean = isConnected

    /**
     * Whether keyboard notes are played through the app's piano. On by default: many MIDI keyboards have no
     * speakers and would be silent; one with its own sound would be heard twice, so it can be turned off.
     */
    @Volatile
    var isSoundEnabled = true
        set(value) {
            field = value
            if (!value) releaseSoundingNotes()
        }

    /**
     * Whether the song being played is also sent to the connected MIDI device, so e.g. the piano on the website
     * (phone plugged into a computer in USB "MIDI" mode) shows and plays it. Only the song's notes are sent, never
     * notes received from the device, so nothing echoes back.
     */
    @Volatile
    var isOutputEnabled = true
        set(value) {
            field = value
            if (!value) sendAllNotesOff()
        }

    /** Pitches sent as note on and not yet as note off, so stopping releases exactly those on the other side. */
    private val sentPitches = ConcurrentHashMap.newKeySet<Int>()

    /** Keyboard notes this app is sounding, so a note off (or turning the sound off) stops only those. */
    private val soundingPitches = ConcurrentHashMap.newKeySet<Int>()

    @Suppress("DEPRECATION")
    fun initialize(context: Context) {
        // Called on every Activity creation; register the device callback only once
        if (midiManager != null) return
        val manager = context.applicationContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return
        midiManager = manager

        manager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                autoConnectToDevice(manager, device)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                if (pendingDeviceId == device.id) pendingDeviceId = null
                if (activeDevice?.info?.id == device.id) {
                    disconnect()
                    // Fall back to another keyboard that is still plugged in, if any
                    connectFirstAvailable(manager)
                }
            }
        }, Handler(Looper.getMainLooper()))

        connectFirstAvailable(manager)
    }

    @Suppress("DEPRECATION")
    private fun connectFirstAvailable(manager: MidiManager) {
        for (device in manager.devices) {
            autoConnectToDevice(manager, device)
        }
    }

    private fun autoConnectToDevice(manager: MidiManager, deviceInfo: MidiDeviceInfo) {
        if (activeDevice != null || pendingDeviceId != null) return
        // Only devices that send notes to us (e.g. not a synth app that only accepts input)
        if (deviceInfo.outputPortCount == 0) return

        pendingDeviceId = deviceInfo.id
        manager.openDevice(deviceInfo, { device ->
            if (pendingDeviceId != deviceInfo.id) {
                // Removed while opening
                device?.close()
                return@openDevice
            }
            pendingDeviceId = null
            val outputPort = device?.openOutputPort(0)
            if (device == null || outputPort == null) {
                device?.close()
                return@openDevice
            }
            val parser = MidiNoteParser(
                onNoteOn = { pitch, velocity ->
                    if (isSoundEnabled) {
                        PianoPlayer.noteOn(pitch, velocity)
                        soundingPitches.add(pitch)
                    }
                    simulateExternalNoteOn(pitch)
                },
                onNoteOff = { pitch ->
                    if (soundingPitches.remove(pitch)) PianoPlayer.noteOff(pitch)
                    simulateExternalNoteOff(pitch)
                }
            )
            outputPort.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parser.feed(msg, offset, count)
                }
            })
            activeDevice = device
            activePort = outputPort
            activeInputPort = if (deviceInfo.inputPortCount > 0) runCatching { device.openInputPort(0) }.getOrNull() else null
            isConnected = true
        }, Handler(Looper.getMainLooper()))
    }

    private fun disconnect() {
        sendAllNotesOff()
        runCatching { activeInputPort?.close() }
        activeInputPort = null
        runCatching { activePort?.close() }
        runCatching { activeDevice?.close() }
        activePort = null
        activeDevice = null
        isConnected = false
        releaseSoundingNotes()
        externalPresses.clear()
        syncPressedKeys()
    }

    fun simulateNoteOn(pitch: Int) {
        lastPressTimestamps[pitch] = System.currentTimeMillis()
        virtualPresses.add(pitch)
        syncPressedKeys()
    }

    fun simulateNoteOff(pitch: Int) {
        virtualPresses.remove(pitch)
        syncPressedKeys()
    }

    fun simulateExternalNoteOn(pitch: Int) {
        lastPressTimestamps[pitch] = System.currentTimeMillis()
        externalPresses.add(pitch)
        syncPressedKeys()
    }

    fun simulateExternalNoteOff(pitch: Int) {
        externalPresses.remove(pitch)
        syncPressedKeys()
    }

    /** Sends a song note to the connected device (see [isOutputEnabled]). */
    fun sendNoteOn(pitch: Int, velocity: Int) {
        if (!isOutputEnabled || pitch !in 0..127) return
        val port = activeInputPort ?: return
        if (send(port, byteArrayOf(0x90.toByte(), pitch.toByte(), velocity.coerceIn(1, 127).toByte()))) sentPitches.add(pitch)
    }

    fun sendNoteOff(pitch: Int) {
        if (!sentPitches.remove(pitch)) return
        val port = activeInputPort ?: return
        send(port, byteArrayOf(0x80.toByte(), pitch.toByte(), 0))
    }

    /** Releases every note this app sent; not a MIDI "all notes off", which would also cut notes played on the other side. */
    fun sendAllNotesOff() {
        sentPitches.toList().forEach(::sendNoteOff)
    }

    private fun send(port: MidiInputPort, bytes: ByteArray): Boolean =
        runCatching { port.send(bytes, 0, bytes.size) }.isSuccess

    private fun releaseSoundingNotes() {
        soundingPitches.toList().forEach { pitch ->
            if (soundingPitches.remove(pitch)) PianoPlayer.noteOff(pitch)
        }
    }

    private fun syncPressedKeys() {
        pressedKeysMutex.withLock {
            val combined = virtualPresses + externalPresses
            val toRemove = pressedKeys.filter { it !in combined }
            pressedKeys.removeAll(toRemove)
            combined.forEach { if (it !in pressedKeys) pressedKeys.add(it) }
        }
    }
}
