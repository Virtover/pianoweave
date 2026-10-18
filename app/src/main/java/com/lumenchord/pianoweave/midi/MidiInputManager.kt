package com.lumenchord.pianoweave.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var pendingDeviceId: Int? = null
    private var midiManager: MidiManager? = null
    private val pressedKeysMutex = ReentrantLock()

    /** True while a MIDI keyboard is connected and sending notes. Observable from Compose. */
    var isConnected by mutableStateOf(false)
        private set

    fun isMidiDeviceConnected(): Boolean = isConnected

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
                onNoteOn = { pitch, _ -> simulateExternalNoteOn(pitch) },
                onNoteOff = { pitch -> simulateExternalNoteOff(pitch) }
            )
            outputPort.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parser.feed(msg, offset, count)
                }
            })
            activeDevice = device
            activePort = outputPort
            isConnected = true
        }, Handler(Looper.getMainLooper()))
    }

    private fun disconnect() {
        runCatching { activePort?.close() }
        runCatching { activeDevice?.close() }
        activePort = null
        activeDevice = null
        isConnected = false
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

    private fun syncPressedKeys() {
        pressedKeysMutex.withLock {
            val combined = virtualPresses + externalPresses
            val toRemove = pressedKeys.filter { it !in combined }
            pressedKeys.removeAll(toRemove)
            combined.forEach { if (it !in pressedKeys) pressedKeys.add(it) }
        }
    }
}
