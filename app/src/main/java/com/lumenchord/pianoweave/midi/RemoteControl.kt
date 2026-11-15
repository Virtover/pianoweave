package com.lumenchord.pianoweave.midi

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The other way round from [RollSync]: a page showing the song (the website's piano, over Bluetooth or
 * USB) can drive the piano roll, so whoever sits at the computer doesn't have to reach for the phone.
 * Same SysEx framing and ID as RollSync, commands from 0x11 up:
 *
 *   F0 7D 50 57 11 <0|1>        pause / play
 *   F0 7D 50 57 12 <0|1>        wait mode off / on
 *   F0 7D 50 57 13 speed(2)     playback speed x100, 7-bit groups, high first (snapped to the app's speeds)
 *
 * Commands arrive on MIDI / Bluetooth threads; the open piano roll collects [commands] and applies them.
 * Nothing collects while no song is open, so they're simply dropped then.
 */
object RemoteControl {
    sealed interface Command {
        data class Play(val on: Boolean) : Command
        data class WaitMode(val on: Boolean) : Command
        data class Speed(val speed: Float) : Command
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    /** A whole SysEx message (F0 ... F7) from a connected device or computer. */
    fun receive(message: ByteArray) {
        parse(message)?.let { _commands.tryEmit(it) }
    }

    internal fun parse(m: ByteArray): Command? {
        if (m.size < 7 || m[0] != 0xF0.toByte() || m[1] != 0x7D.toByte() || m[2] != 0x50.toByte() || m[3] != 0x57.toByte()) return null
        val arg = m[5].toInt() and 0x7F
        return when (m[4].toInt()) {
            0x11 -> Command.Play(arg != 0)
            0x12 -> Command.WaitMode(arg != 0)
            0x13 -> {
                if (m.size < 8) return null
                val x100 = (arg shl 7) or (m[6].toInt() and 0x7F)
                if (x100 in 10..400) Command.Speed(x100 / 100f) else null
            }
            else -> null
        }
    }
}
