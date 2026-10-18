package com.lumenchord.pianoweave.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end check of MIDI keyboard input through Android's MIDI stack, using the test-only
 * [LoopbackMidiService] as the keyboard. Run on a device or emulator with no other MIDI devices attached.
 */
@RunWith(AndroidJUnit4::class)
class MidiKeyboardInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val toggleComponent =
        "${instrumentation.context.packageName}/${LoopbackToggleReceiver::class.java.name}"

    private var keyboard: MidiDevice? = null
    private var keyboardInput: MidiInputPort? = null

    @Before
    fun setUp() {
        setKeyboardPlugged(true)
        instrumentation.runOnMainSync { MidiInputManager.initialize(context) }
        // Calling it again (like an Activity recreation) must be harmless
        instrumentation.runOnMainSync { MidiInputManager.initialize(context) }
        assertTrue("App did not connect to the virtual keyboard", waitFor { MidiInputManager.isConnected })
        openKeyboardInput()
    }

    @After
    fun tearDown() {
        runCatching { keyboardInput?.close() }
        runCatching { keyboard?.close() }
        setKeyboardPlugged(true)
    }

    @Test
    fun singleNoteOnAndOff() {
        val before = System.currentTimeMillis()
        send(0x90, 60, 100)
        assertTrue(waitFor { MidiInputManager.pressedKeys.contains(60) })
        assertTrue((MidiInputManager.lastPressTimestamps[60] ?: 0L) >= before)

        send(0x80, 60, 0)
        assertTrue(waitFor { !MidiInputManager.pressedKeys.contains(60) })
    }

    @Test
    fun chordWithRunningStatusSplitAcrossPackets() {
        send(0x91, 60, 100, 64)
        send(90, 67, 80)
        assertTrue(waitFor { MidiInputManager.pressedKeys.containsAll(listOf(60, 64, 67)) })

        // Release with running status note-on velocity 0
        send(0x91, 60, 0, 64, 0, 67, 0)
        assertTrue(waitFor { MidiInputManager.pressedKeys.none { it in listOf(60, 64, 67) } })
    }

    @Test
    fun pedalAndClockAreNotNotes() {
        send(0xF8, 0xB0, 64, 127, 0xFE, 0xE0, 0, 64)
        send(0x90, 72, 90)
        assertTrue(waitFor { MidiInputManager.pressedKeys.contains(72) })
        assertEquals(listOf(72), MidiInputManager.pressedKeys.toList())
        send(0x80, 72, 0)
        assertTrue(waitFor { MidiInputManager.pressedKeys.isEmpty() })
    }

    @Test
    fun unplugAndReplug() {
        send(0x90, 65, 100)
        assertTrue(waitFor { MidiInputManager.pressedKeys.contains(65) })

        setKeyboardPlugged(false)
        assertTrue("Unplug not detected", waitFor { !MidiInputManager.isConnected })
        assertFalse("Held key stuck after unplug", MidiInputManager.pressedKeys.contains(65))

        setKeyboardPlugged(true)
        assertTrue("Did not reconnect after replug", waitFor { MidiInputManager.isConnected })
        openKeyboardInput()
        send(0x90, 66, 100)
        assertTrue(waitFor { MidiInputManager.pressedKeys.contains(66) })
        send(0x80, 66, 0)
    }

    private fun openKeyboardInput() {
        runCatching { keyboardInput?.close() }
        runCatching { keyboard?.close() }
        val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
        var info: MidiDeviceInfo? = null
        waitFor {
            @Suppress("DEPRECATION")
            info = manager.devices.firstOrNull {
                it.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) == "Test Keyboard"
            }
            info != null
        }
        assertNotNull("Virtual keyboard not found", info)

        val opened = CountDownLatch(1)
        manager.openDevice(info, { device ->
            keyboard = device
            opened.countDown()
        }, Handler(Looper.getMainLooper()))
        assertTrue(opened.await(5, TimeUnit.SECONDS))
        keyboardInput = keyboard?.openInputPort(0)
        assertNotNull("Could not open virtual keyboard input", keyboardInput)
    }

    private fun send(vararg bytes: Int) {
        val data = ByteArray(bytes.size) { bytes[it].toByte() }
        keyboardInput!!.send(data, 0, data.size)
    }

    private fun setKeyboardPlugged(plugged: Boolean) =
        shell("am broadcast -n $toggleComponent --ez ${LoopbackToggleReceiver.EXTRA_ENABLED} $plugged")

    private fun shell(command: String) {
        // Read to EOF so the command has finished before continuing
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
