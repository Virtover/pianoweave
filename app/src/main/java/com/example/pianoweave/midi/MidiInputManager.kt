package com.example.pianoweave.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.util.concurrent.ConcurrentHashMap

object MidiInputManager {

    val pressedKeys = mutableStateListOf<Int>()
    val lastPressTimestamps = ConcurrentHashMap<Int, Long>()
    val consumedPressTimestamps = ConcurrentHashMap<Int, Long>()
    
    private val virtualPresses = mutableSetOf<Int>()
    private val externalPresses = mutableSetOf<Int>()
    
    private var activeDevice: MidiDevice? = null

    fun isMidiDeviceConnected(): Boolean {
        return activeDevice != null
    }

    fun initialize(context: Context) {
        val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return

        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                autoConnectToDevice(midiManager, device)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                if (activeDevice?.info?.id == device.id) {
                    activeDevice?.close()
                    activeDevice = null
                    externalPresses.clear()
                    syncPressedKeys()
                }
            }
        }, Handler(Looper.getMainLooper()))

        for (device in midiManager.devices) {
            autoConnectToDevice(midiManager, device)
        }
    }

    private fun autoConnectToDevice(midiManager: MidiManager, deviceInfo: MidiDeviceInfo) {
        if (activeDevice != null) return

        midiManager.openDevice(deviceInfo, { device ->
            if (device != null) {
                activeDevice = device
                val outputPort = device.openOutputPort(0)
                if (outputPort != null) {
                    outputPort.connect(object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                            parseMidiMessage(msg, offset, count)
                        }
                    })
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun parseMidiMessage(msg: ByteArray, offset: Int, count: Int) {
        var i = offset
        while (i < offset + count) {
            val statusByte = msg[i].toInt() and 0xFF
            val type = statusByte and 0xF0
            
            if (type == 0x90) { // Note On
                if (i + 2 < offset + count) {
                    val pitch = msg[i + 1].toInt() and 0xFF
                    val velocity = msg[i + 2].toInt() and 0xFF
                    if (velocity > 0) simulateExternalNoteOn(pitch) else simulateExternalNoteOff(pitch)
                    i += 3
                } else break
            } else if (type == 0x80) { // Note Off
                if (i + 2 < offset + count) {
                    val pitch = msg[i + 1].toInt() and 0xFF
                    simulateExternalNoteOff(pitch)
                    i += 3
                } else break
            } else {
                i++
            }
        }
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
        val combined = virtualPresses + externalPresses
        val toRemove = pressedKeys.filter { it !in combined }
        pressedKeys.removeAll(toRemove)
        combined.forEach { if (it !in pressedKeys) pressedKeys.add(it) }
    }
}
