package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.Icons
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumenchord.pianoweave.audio.VoiceCommand
import com.lumenchord.pianoweave.ui.viewmodel.PLAYBACK_SPEEDS
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What a voice command (or another quick control, with its own [icon]) just did, shown briefly over the falling notes. */
internal data class VoiceFeedback(
    val command: VoiceCommand,
    val text: String,
    val canUndo: Boolean,
    val icon: ImageVector = Icons.Default.Mic,
    val id: Long = System.nanoTime()
)

/**
 * Applies [VoiceCommand]s to the piano roll. Commands said in a row ("full screen wait") are one action:
 * saying "undo" (or tapping its chip) within [UNDO_WINDOW_MS] takes all of it back, since without a wake
 * word a misheard utterance must cost one word to fix. "loop start" ... "loop end" sets a loop between
 * the two moments, hands-free. Full screen (hidden bars) is the screen's own state, reached through
 * [isFullscreen] / [setFullscreen].
 */
internal class VoiceCommandController(
    private val viewModel: PianoWeaveViewModel,
    private val isFullscreen: () -> Boolean,
    private val setFullscreen: (Boolean) -> Unit,
    /** Whether the song has beats; without them there's no metronome to turn on. */
    private val hasBeats: () -> Boolean,
    /** Whether the song's tempo moves a lot, so turning the metronome on warns that the clicks will too. */
    private val isTempoUnstable: () -> Boolean = { false }
) {
    companion object {
        const val UNDO_WINDOW_MS = 4000L
        private const val SEEK_MS = 5000L
        private const val MIN_LOOP_MS = 500L
    }

    private data class Snapshot(
        val isPlaying: Boolean,
        val playheadMs: Long,
        val isWaitModeEnabled: Boolean,
        val isLoopingEnabled: Boolean,
        val loopStartMs: Long,
        val loopEndMs: Long,
        val speed: Float,
        val isMetronomeEnabled: Boolean,
        val isCountInEnabled: Boolean,
        val isFullscreen: Boolean,
        val markMs: Long?,
        val restoresPlayhead: Boolean,
        val takenAt: Long = System.currentTimeMillis()
    )

    var feedback by mutableStateOf<VoiceFeedback?>(null)
        private set

    private var markMs: Long? = null
    private var undo: Snapshot? = null

    /**
     * Applies the [commands] of one utterance in order. True if the playhead jumped, so the caller
     * resets wait and sound state. A leading "undo" takes back the previous action first ("undo, loop").
     */
    fun apply(context: Context, commands: List<VoiceCommand>, songDurationMs: Long): Boolean {
        var jumped = false
        var rest = commands
        if (rest.firstOrNull() == VoiceCommand.UNDO) {
            jumped = undoLast(context)
            rest = rest.drop(1)
        }
        // "undo" anywhere else isn't a correction of anything said in the same breath
        rest = rest.filter { it != VoiceCommand.UNDO }
        if (rest.isEmpty()) return jumped

        val before = snapshot(restoresPlayhead = rest.any { it == VoiceCommand.AGAIN || it == VoiceCommand.BACK || it == VoiceCommand.FORWARD })
        // Repeats ("faster faster") show only where they ended up
        val texts = mutableListOf<Pair<VoiceCommand, String>>()
        var changed = false
        for (command in rest) {
            val step = applyOne(context, command, songDurationMs) ?: continue
            jumped = jumped || step.jumped
            changed = changed || step.changed
            if (texts.lastOrNull()?.first == command) texts.removeAt(texts.lastIndex)
            texts += command to step.text
        }
        if (texts.isEmpty()) return jumped
        if (changed) undo = before
        feedback = VoiceFeedback(texts.last().first, texts.joinToString(" · ") { it.second }, canUndo = changed)
        return jumped
    }

    /** "Selah" alone was heard: the next words within a few seconds are taken as commands. */
    fun showWoke() {
        feedback = VoiceFeedback(VoiceCommand.UNDO, "Selah · listening…", canUndo = false)
    }

    /** A note from a control that isn't spoken (holding the speed button), in the same place as command feedback. */
    fun showNote(text: String, icon: ImageVector) {
        feedback = VoiceFeedback(VoiceCommand.UNDO, text, canUndo = false, icon = icon)
    }

    /** Shows what was heard when it wasn't a command (debug builds, to tune recognition). */
    fun showUnmatched(heard: String) {
        feedback = VoiceFeedback(VoiceCommand.UNDO, "Not a command: $heard", canUndo = false)
    }

    private class Step(val text: String, val changed: Boolean = true, val jumped: Boolean = false)

    /** One command's effect, or null if it had nothing to do (play while playing). */
    private fun applyOne(context: Context, command: VoiceCommand, songDurationMs: Long): Step? {
        var jumped = false
        val text = when (command) {
            VoiceCommand.PLAY -> {
                if (viewModel.isPlaying) return null
                viewModel.isPlaying = true
                "Playing"
            }
            VoiceCommand.PAUSE -> {
                if (!viewModel.isPlaying) return null
                viewModel.isPlaying = false
                "Paused"
            }
            VoiceCommand.WAIT -> {
                // PianoRollScreen starts or stops the microphone note detection when this changes
                viewModel.isWaitModeEnabled = !viewModel.isWaitModeEnabled
                if (viewModel.isWaitModeEnabled) "Wait mode on" else "Wait mode off"
            }
            VoiceCommand.FULLSCREEN -> {
                setFullscreen(!isFullscreen())
                if (isFullscreen()) "Full screen" else "Bars shown"
            }
            VoiceCommand.LOOP -> {
                viewModel.isLoopingEnabled = !viewModel.isLoopingEnabled
                if (viewModel.isLoopingEnabled) {
                    "Loop on · ${formatTime(viewModel.loopStartMs)}–${formatTime(viewModel.loopEndMs)}"
                } else {
                    "Loop off"
                }
            }
            VoiceCommand.LOOP_START -> {
                markMs = viewModel.playheadMs
                "Loop start ${formatTime(viewModel.playheadMs)} · say “loop end” at its end"
            }
            VoiceCommand.LOOP_END -> {
                val start = markMs ?: return Step("Say “loop start” first", changed = false)
                val head = viewModel.playheadMs
                if (abs(head - start) < MIN_LOOP_MS) return Step("Too short for a loop", changed = false)
                markMs = null
                viewModel.loopStartMs = min(start, head)
                viewModel.loopEndMs = max(start, head)
                viewModel.isLoopingEnabled = true
                "Looping ${formatTime(viewModel.loopStartMs)}–${formatTime(viewModel.loopEndMs)}"
            }
            VoiceCommand.AGAIN -> {
                val fromLoop = viewModel.isLoopingEnabled
                viewModel.playheadMs = if (fromLoop) viewModel.loopStartMs else 0L
                jumped = true
                if (fromLoop) "From the loop start" else "From the top"
            }
            VoiceCommand.BACK, VoiceCommand.FORWARD -> {
                val back = command == VoiceCommand.BACK
                viewModel.playheadMs = calculateSeekPosition(
                    currentMs = viewModel.playheadMs,
                    deltaMs = if (back) -SEEK_MS else SEEK_MS,
                    songDurationMs = songDurationMs,
                    isLoopingEnabled = viewModel.isLoopingEnabled,
                    loopStartMs = viewModel.loopStartMs,
                    loopEndMs = viewModel.loopEndMs
                )
                jumped = true
                if (back) "Back 5 s" else "Forward 5 s"
            }
            VoiceCommand.SLOWER, VoiceCommand.FASTER -> {
                val current = PLAYBACK_SPEEDS.indices.minBy { abs(PLAYBACK_SPEEDS[it] - viewModel.speedMultiplier) }
                val next = (current + if (command == VoiceCommand.FASTER) 1 else -1).coerceIn(PLAYBACK_SPEEDS.indices)
                if (next == current) {
                    return Step(if (next == 0) "Already the slowest" else "Already the fastest", changed = false)
                }
                viewModel.selectPlaybackSpeed(context, PLAYBACK_SPEEDS[next])
                "Speed ${formatSpeed(PLAYBACK_SPEEDS[next])}"
            }
            VoiceCommand.NORMAL -> {
                // Shown even when nothing changes, so the word is seen to have been heard
                if (viewModel.speedMultiplier == 1.0f) return Step("Already normal speed", changed = false)
                viewModel.selectPlaybackSpeed(context, 1.0f)
                "Normal speed"
            }
            VoiceCommand.METRONOME -> {
                val on = !viewModel.isMetronomeEnabled
                when {
                    !hasBeats() -> return Step("No beat information for this piece", changed = false)
                    // Wait mode stops at every chord, so it has no metronome (see PianoPlayerContent)
                    on && viewModel.isWaitModeEnabled -> return Step("No metronome in wait mode", changed = false)
                }
                viewModel.setMetronomeEnabled(context, on)
                when {
                    !on -> "Metronome off"
                    isTempoUnstable() -> "Metronome on · $UNSTABLE_TEMPO_NOTE"
                    else -> "Metronome on"
                }
            }
            VoiceCommand.COUNT_IN -> {
                val on = !viewModel.isCountInEnabled
                // Wait mode starts at each chord on its own, so there's nothing to count into. Without beats
                // it still counts, on screen
                if (on && viewModel.isWaitModeEnabled) return Step("No count-in in wait mode", changed = false)
                viewModel.setCountInEnabled(context, on)
                if (on) "Count-in on" else "Count-in off"
            }
            VoiceCommand.UNDO -> return null
        }
        return Step(text, jumped = jumped)
    }

    /** Takes back the last action if it's recent. True if the playhead jumped back. */
    fun undoLast(context: Context): Boolean {
        val last = undo ?: return false
        undo = null
        if (System.currentTimeMillis() - last.takenAt > UNDO_WINDOW_MS) return false

        viewModel.isPlaying = last.isPlaying
        viewModel.isWaitModeEnabled = last.isWaitModeEnabled
        viewModel.isLoopingEnabled = last.isLoopingEnabled
        viewModel.loopStartMs = last.loopStartMs
        viewModel.loopEndMs = last.loopEndMs
        if (viewModel.speedMultiplier != last.speed) viewModel.selectPlaybackSpeed(context, last.speed)
        // Also brings back a metronome that "wait" turned off
        if (viewModel.isMetronomeEnabled != last.isMetronomeEnabled) viewModel.setMetronomeEnabled(context, last.isMetronomeEnabled)
        if (viewModel.isCountInEnabled != last.isCountInEnabled) viewModel.setCountInEnabled(context, last.isCountInEnabled)
        setFullscreen(last.isFullscreen)
        markMs = last.markMs
        // Only a jump is undone in time; undoing "loop" must not also rewind the seconds played since
        if (last.restoresPlayhead) viewModel.playheadMs = last.playheadMs
        feedback = VoiceFeedback(VoiceCommand.UNDO, "Undone", canUndo = false)
        return last.restoresPlayhead
    }

    private fun snapshot(restoresPlayhead: Boolean) = Snapshot(
        isPlaying = viewModel.isPlaying,
        playheadMs = viewModel.playheadMs,
        isWaitModeEnabled = viewModel.isWaitModeEnabled,
        isLoopingEnabled = viewModel.isLoopingEnabled,
        loopStartMs = viewModel.loopStartMs,
        loopEndMs = viewModel.loopEndMs,
        speed = viewModel.speedMultiplier,
        isMetronomeEnabled = viewModel.isMetronomeEnabled,
        isCountInEnabled = viewModel.isCountInEnabled,
        isFullscreen = isFullscreen(),
        markMs = markMs,
        restoresPlayhead = restoresPlayhead
    )

    private fun formatSpeed(speed: Float) =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}
