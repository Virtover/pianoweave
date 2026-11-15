package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteControlTest {

    private fun msg(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    @Test
    fun playAndPause() {
        assertEquals(RemoteControl.Command.Play(true), RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x11, 1, 0xF7)))
        assertEquals(RemoteControl.Command.Play(false), RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x11, 0, 0xF7)))
    }

    @Test
    fun waitMode() {
        assertEquals(RemoteControl.Command.WaitMode(true), RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x12, 1, 0xF7)))
    }

    @Test
    fun speedInSevenBitGroups() {
        // 125 = 0x00 0x7D; 150 = 0x01 0x16
        assertEquals(RemoteControl.Command.Speed(1.25f), RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x13, 0, 125, 0xF7)))
        assertEquals(RemoteControl.Command.Speed(1.5f), RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x13, 1, 22, 0xF7)))
    }

    @Test
    fun nonsenseIsIgnored() {
        assertNull(RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x13, 0, 0, 0xF7)))  // speed 0
        assertNull(RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x03, 1, 0xF7)))     // the app's own transport
        assertNull(RemoteControl.parse(msg(0xF0, 0x7E, 0x50, 0x57, 0x11, 1, 0xF7)))     // someone else's ID
        assertNull(RemoteControl.parse(msg(0xF0, 0x7D, 0x50, 0x57, 0x13, 1, 0xF7)))     // speed cut short
        assertNull(RemoteControl.parse(msg(0xF0, 0x7D, 0xF7)))
    }
}
