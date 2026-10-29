package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.screens.pianoroll.FallingNotesVisualizer
import com.lumenchord.pianoweave.ui.screens.pianoroll.PianoKeyboardRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.isPitchBlack
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.theme.PillShape

// Small copies of the screens the welcome tutorial talks about, drawn like the real thing so the
// buttons are easy to recognise afterwards. The ones to tap are ringed in the theme's gold.

private val VideoBlack = Color(0xFF16161A)

/** Welcome: the piano roll the user ends up with, notes falling onto the keys as the song plays. */
@Composable
internal fun PianoRollPicture() {
    ScreenFrame {
        LivePianoRoll(Modifier.fillMaxWidth().height(170.dp), keyboardHeight = 44.dp)
    }
}

/** Step 1: a video app's page with its Share button, then the share sheet with Copy link. */
@Composable
internal fun CopyLinkPicture() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScreenFrame {
            // Piano covers are often falling-notes videos themselves; this one is playing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VideoBlack)
            ) {
                LivePianoRoll(Modifier.fillMaxSize(), keyboardHeight = 30.dp, rounded = false)
                // The video player's progress bar
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(Modifier.fillMaxWidth(0.35f).fillMaxHeight().background(Color.White))
                }
            }
            Column {
                Text(
                    "Beautiful piano cover",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text("Piano channel · 120K views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VideoAction(Icons.Default.ThumbUp, "1.2K")
                TapHint(shape = PillShape, number = 1) { VideoAction(Icons.Default.Share, "Share") }
                VideoAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Save")
            }
        }

        DownArrow()

        // The share sheet that opens from the bottom of the screen
        ScreenFrame {
            Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TapHint(shape = RoundedCornerShape(12.dp), number = 2) { ShareTarget(Icons.Default.ContentCopy, "Copy link") }
                ShareTarget(Icons.Default.Sms, "Messages")
                ShareTarget(Icons.Default.Email, "Email")
                ShareTarget(Icons.Default.MoreHoriz, "More")
            }
        }
    }
}

/** Step 2: the Transcribe card as it looks on the Learn tab, paste button and Transcribe ringed. */
@Composable
internal fun TranscribePicture() {
    val accent = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outlineVariant
    ScreenFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.AutoAwesome, tint = accent, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "AI Transcription",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(7.dp).background(Moods.StillWaters.core, CircleShape))
                Icon(Icons.Default.Settings, null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }

        // Link / File / Mic, with Link chosen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(PillShape)
                .border(1.dp, outline, PillShape)
        ) {
            listOf(Icons.Default.Link to "Link", Icons.Default.AudioFile to "File", Icons.Default.Mic to "Mic")
                .forEachIndexed { index, (icon, label) ->
                    val isSelected = index == 0
                    val color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (isSelected) Modifier.background(accent.copy(alpha = 0.18f), PillShape) else Modifier),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
        }

        // The link box, its paste button ringed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, outline, RoundedCornerShape(12.dp))
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Piano cover link",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            TapHint(shape = CircleShape, number = 1) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ContentPaste, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }

        TapHint(shape = PillShape, number = 2, fillWidth = true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(accent, PillShape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Transcribe", color = MaterialTheme.colorScheme.onSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Step 3: the "Transcription ready" card, then the piano roll that Start practicing opens. */
@Composable
internal fun PlayAlongPicture() {
    val accent = MaterialTheme.colorScheme.secondary
    val glory = Moods.Glory.core
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScreenFrame(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VideoBlack)
                    .border(1.dp, glory.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            ) {
                LivePianoRoll(Modifier.fillMaxSize(), keyboardHeight = 18.dp, rounded = false, animate = false)
            }
            Text("Transcription ready", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glory)
            Text(
                "Beautiful piano cover",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            TapHint(shape = PillShape, fillWidth = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(accent, PillShape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start practicing", color = MaterialTheme.colorScheme.onSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        DownArrow()

        ScreenFrame {
            LivePianoRoll(Modifier.fillMaxWidth().height(120.dp), keyboardHeight = 36.dp)
        }
    }
}

/** A rounded patch of screen that a tutorial picture is drawn on. */
@Composable
private fun ScreenFrame(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(10.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/**
 * Rings [content] in gold as the thing to tap. [number] marks the order when a picture has two taps;
 * without it, a pointing finger.
 */
@Composable
private fun TapHint(
    shape: Shape,
    number: Int? = null,
    fillWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    val gold = LocalAppTheme.current.primaryColor
    val onGold = if (LocalAppTheme.current.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
    Box(modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .border(2.dp, gold, shape)
                .padding(3.dp)
        ) {
            content()
        }
        if (number != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(18.dp)
                    .background(gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", color = onGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = gold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-12).dp, y = 12.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun VideoAction(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), PillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun ShareTarget(icon: ImageVector, label: String) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun DownArrow() {
    Icon(
        Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}

private const val BEAT_MS = 400L
private const val LOOP_MS = 16 * BEAT_MS

/**
 * Ode to Joy (Beethoven, public domain): the tune in the upper octave over half-note chords, 16 beats
 * that loop seamlessly.
 */
private val demoSong: List<MidiNoteEvent> = buildList {
    val tune = listOf(
        76 to 1f, 76 to 1f, 77 to 1f, 79 to 1f, 79 to 1f, 77 to 1f, 76 to 1f, 74 to 1f,
        72 to 1f, 72 to 1f, 74 to 1f, 76 to 1f, 76 to 1.5f, 74 to 0.5f, 74 to 2f
    )
    var beat = 0f
    tune.forEach { (pitch, beats) ->
        add(MidiNoteEvent(pitch, (beat * BEAT_MS).toLong(), (beats * BEAT_MS).toLong() - 40, 90))
        beat += beats
    }
    val c = listOf(60, 64, 67)
    val g = listOf(62, 67, 71)
    listOf(c, c, g, g, c, c, g, c).forEachIndexed { half, chord ->
        chord.forEach { add(MidiNoteEvent(it, half * 2 * BEAT_MS, 2 * BEAT_MS - 60, 70)) }
    }
}

/** The song repeated before and after itself, so the view is the same when the playhead wraps around. */
private val demoEvents: List<MidiNoteEvent> = (-1..2).flatMap { loop ->
    demoSong.map { it.copy(startMs = it.startMs + loop * LOOP_MS) }
}

private const val DEMO_START_PITCH = 60 // C4
private const val DEMO_END_PITCH = 83   // B5
private val demoWhiteKeys = (DEMO_START_PITCH..DEMO_END_PITCH).count { !isPitchBlack(it) }

/**
 * The real piano roll (falling notes and keyboard, in the current theme) playing [demoSong] on two
 * octaves. [animate] false holds a still frame, for thumbnails.
 */
@Composable
private fun LivePianoRoll(
    modifier: Modifier,
    keyboardHeight: Dp,
    rounded: Boolean = true,
    animate: Boolean = true
) {
    val playhead by rememberInfiniteTransition(label = "tutorialRoll").animateFloat(
        initialValue = 0f,
        targetValue = LOOP_MS.toFloat(),
        animationSpec = infiniteRepeatable(tween(LOOP_MS.toInt(), easing = LinearEasing)),
        label = "tutorialPlayhead"
    )
    val head = if (animate) playhead.toLong() else 5 * BEAT_MS
    val sounding = demoEvents
        .filter { head >= it.startMs && head <= it.startMs + it.durationMs }
        .mapTo(HashSet()) { it.pitch }

    Column(modifier.then(if (rounded) Modifier.clip(RoundedCornerShape(8.dp)) else Modifier)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            FallingNotesVisualizer(
                events = demoEvents,
                head = head,
                start = DEMO_START_PITCH,
                numWhiteKeys = demoWhiteKeys
            )
        }
        PianoKeyboardRow(
            start = DEMO_START_PITCH,
            end = DEMO_END_PITCH,
            numWhiteKeys = demoWhiteKeys,
            sustainedPitches = sounding,
            keyboardHeight = keyboardHeight
        )
    }
}
