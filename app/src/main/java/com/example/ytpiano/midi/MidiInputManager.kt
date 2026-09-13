package com.example.ytpiano.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf

object MidiInputManager {

    val pressedKeys = mutableStateListOf<Int>()
    private var activeDevice: MidiDevice? = null

    fun initialize(context: Context) {
        val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return

        // Register for hotplug connectivity events
        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                autoConnectToDevice(midiManager, device)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                if (activeDevice?.info?.id == device.id) {
                    activeDevice?.close()
                    activeDevice = null
                    pressedKeys.clear()
                }
            }
        }, Handler(Looper.getMainLooper()))

        // Scan existing connected keyboards
        for (device in midiManager.devices) {
            autoConnectToDevice(midiManager, device)
        }
    }

    private fun autoConnectToDevice(midiManager: MidiManager, deviceInfo: MidiDeviceInfo) {
        if (activeDevice != null) return // Already connected to a keyboard

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
            
            if (type == 0x90) { // Note On Event
                if (i + 2 < offset + count) {
                    val pitch = msg[i + 1].toInt() and 0xFF
                    val velocity = msg[i + 2].toInt() and 0xFF
                    if (velocity > 0) {
                        if (!pressedKeys.contains(pitch)) {
                            pressedKeys.add(pitch)
                        }
                    } else {
                        pressedKeys.remove(pitch)
                    }
                    i += 3
                } else break
            } else if (type == 0x80) { // Note Off Event
                if (i + 2 < offset + count) {
                    val pitch = msg[i + 1].toInt() and 0xFF
                    pressedKeys.remove(pitch)
                    i += 3
                } else break
            } else {
                // Advance 1 byte if non-note byte stream encountered
                i++
            }
        }
    }

    fun simulateNoteOn(pitch: Int) {
        if (!pressedKeys.contains(pitch)) {
            pressedKeys.add(pitch)
        }
    }

    fun simulateNoteOff(pitch: Int) {
        pressedKeys.remove(pitch)
    }
}
