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

    /** How the connected device is attached, for the settings' status card. */
    enum class Transport { Usb, Bluetooth, Other }

    /** A device opened through Android's MIDI service (USB, a Bluetooth keyboard, a computer over USB). */
    private var isDeviceConnected by mutableStateOf(false)

    /** The connected device's name and transport, or null; observable from Compose. */
    var connectedName by mutableStateOf<String?>(null)
        private set
    var connectedTransport by mutableStateOf<Transport?>(null)
        private set

    /**
     * True while something plays notes into the app: a MIDI keyboard (USB or Bluetooth), or a computer
     * connected to the phone's own Bluetooth MIDI ([BleMidiPeripheral]).
     * Observable from Compose.
     */
    val isConnected: Boolean
        get() = isDeviceConnected || BleMidiPeripheral.connectedCentralName != null

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
     * Whether the song being played is also sent to the connected MIDI device or computer (e.g. a computer the
     * phone is plugged into in USB "MIDI" mode), which can then show and play it. Only the song's notes are sent, never
     * notes received from the device, so nothing echoes back.
     */
    @Volatile
    var isOutputEnabled = true
        set(value) {
            field = value
            if (!value) sendAllNotesOff()
        }

    /**
     * Goes up each time something new starts listening to what the app sends (a device attached, a computer
     * subscribing over Bluetooth), so [RollSync] sends it the song from the start.
     */
    @Volatile
    var listenerGeneration = 0
        private set

    internal fun onNewListener() { listenerGeneration++ }

    /** Whether anyone hears what the app sends, and sending is on. */
    fun canSendSync(): Boolean = isOutputEnabled && (activeInputPort != null || BleMidiPeripheral.hasListeners)

    /** The largest SysEx message every listener can take whole (Bluetooth's packet size is the limit). */
    fun maxSysExSize(): Int = if (BleMidiPeripheral.hasListeners) BleMidiPeripheral.maxSysExSize() else 256

    /** A whole SysEx message (F0 ... F7) to every listener; see [RollSync]. */
    fun sendSysEx(message: ByteArray) {
        if (!isOutputEnabled) return
        activeInputPort?.let { send(it, message) }
        BleMidiPeripheral.send(message)
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
        // A Bluetooth keyboard only exists once BluetoothMidi has opened it, and it attaches that one itself
        if (deviceInfo.type == MidiDeviceInfo.TYPE_BLUETOOTH) return

        pendingDeviceId = deviceInfo.id
        manager.openDevice(deviceInfo, { device ->
            if (pendingDeviceId != deviceInfo.id) {
                // Removed while opening
                device?.close()
                return@openDevice
            }
            pendingDeviceId = null
            if (device == null || !attach(device)) device?.close()
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Starts listening to an opened device (and sending to it, if it takes input). Used for devices found
     * on their own (USB) and for Bluetooth keyboards opened by [BluetoothMidi]; a newly attached device
     * replaces the current one. Returns false if the device sends nothing, so it isn't kept open.
     */
    internal fun attach(device: MidiDevice): Boolean {
        val info = device.info
        if (info.outputPortCount == 0) return false
        val outputPort = device.openOutputPort(0) ?: return false
        if (activeDevice != null && activeDevice !== device) disconnect()
        val parser = MidiNoteParser(onNoteOn = ::receiveNoteOn, onNoteOff = ::receiveNoteOff)
        outputPort.connect(object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                parser.feed(msg, offset, count)
            }
        })
        activeDevice = device
        activePort = outputPort
        activeInputPort = if (info.inputPortCount > 0) runCatching { device.openInputPort(0) }.getOrNull() else null
        if (activeInputPort != null) onNewListener()
        connectedName = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI keyboard"
        connectedTransport = when (info.type) {
            MidiDeviceInfo.TYPE_USB -> Transport.Usb
            MidiDeviceInfo.TYPE_BLUETOOTH -> Transport.Bluetooth
            else -> Transport.Other
        }
        isDeviceConnected = true
        return true
    }

    /** True if [device] is the one currently attached (so [BluetoothMidi] knows whether it's still in use). */
    internal fun isAttached(device: MidiDevice?): Boolean = device != null && activeDevice === device

    /** Closes the attached device (e.g. "Disconnect" on a Bluetooth keyboard) and falls back to another one. */
    fun disconnectActive() {
        disconnect()
        midiManager?.let(::connectFirstAvailable)
    }

    /** A note from a keyboard or a connected computer: sounded (if on) and shown as pressed. */
    internal fun receiveNoteOn(pitch: Int, velocity: Int) {
        if (isSoundEnabled) {
            PianoPlayer.noteOn(pitch, velocity)
            soundingPitches.add(pitch)
        }
        simulateExternalNoteOn(pitch)
    }

    internal fun receiveNoteOff(pitch: Int) {
        if (soundingPitches.remove(pitch)) PianoPlayer.noteOff(pitch)
        simulateExternalNoteOff(pitch)
    }

    /** A computer disconnected from the phone's Bluetooth MIDI: let go of whatever it was holding. */
    internal fun releaseExternalNotes() {
        releaseSoundingNotes()
        externalPresses.clear()
        syncPressedKeys()
    }

    private fun disconnect() {
        sendAllNotesOff()
        runCatching { activeInputPort?.close() }
        activeInputPort = null
        runCatching { activePort?.close() }
        runCatching { activeDevice?.close() }
        activePort = null
        activeDevice = null
        isDeviceConnected = false
        connectedName = null
        connectedTransport = null
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
        val message = byteArrayOf(0x90.toByte(), pitch.toByte(), velocity.coerceIn(1, 127).toByte())
        val port = activeInputPort
        val sentToDevice = port != null && send(port, message)
        val sentToComputer = BleMidiPeripheral.send(message)
        if (sentToDevice || sentToComputer) sentPitches.add(pitch)
    }

    fun sendNoteOff(pitch: Int) {
        if (!sentPitches.remove(pitch)) return
        val message = byteArrayOf(0x80.toByte(), pitch.toByte(), 0)
        activeInputPort?.let { send(it, message) }
        BleMidiPeripheral.send(message)
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
