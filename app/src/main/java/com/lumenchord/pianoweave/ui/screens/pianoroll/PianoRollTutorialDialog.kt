package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.components.DialogSurface

private data class TutorialStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val highlights: List<Pair<ImageVector, String>> = emptyList()
)

private val tutorialSteps = listOf(
    TutorialStep(
        icon = Icons.Default.TouchApp,
        title = "Play & scrub",
        body = "Tap the notes to play or pause. Drag up or down to move through the song. Double-tap to hide the bars."
    ),
    TutorialStep(
        icon = Icons.Default.Timer,
        title = "Wait mode",
        body = "The song pauses at each note until you play it, so you can learn at your own pace.",
        highlights = listOf(
            Icons.Default.Mic to "Works acoustically: it listens to your real piano through the microphone.",
            Icons.Default.Hearing to "Keep your phone near the piano, in a quiet room, for the best experience.",
            Icons.Default.Piano to "Have a USB MIDI keyboard? Plug it in and it's used instead, with the rest of the song playing along."
        )
    ),
    TutorialStep(
        icon = Icons.Default.Tune,
        title = "Make it yours",
        body = "Change the speed from the toolbar. Loop a section, transpose, or tweak the visuals in settings."
    )
)

/** Short walkthrough shown the first time a song is opened in the piano roll. Closes only via its own buttons. */
@Composable
internal fun PianoRollTutorialDialog(onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = tutorialSteps[stepIndex]
    val isLast = stepIndex == tutorialSteps.lastIndex
    // A landscape phone is too short for the roomy layout (the buttons, its only way out, fell off the
    // bottom), so there it's compact: icon beside the title, tighter spacing, step dots between the buttons
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val spacing = if (compact) 8.dp else 14.dp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        DialogSurface(
            light = colorGold,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = ColorSurface,
            border = BorderStroke(1.dp, ColorSlate)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                // Last resort for unusually short screens: only the step's content scrolls, the buttons stay
                // put. On ordinary phones, in either orientation, everything fits and nothing scrolls.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    if (compact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(step.icon, tint = colorGold, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            StepTitle(step, colorGold)
                        }
                    } else {
                        IconBadge(step.icon, tint = colorGold, size = 56.dp)
                        StepTitle(step, colorGold)
                    }
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = if (compact) 19.sp else 22.sp,
                        textAlign = TextAlign.Center
                    )
                    if (step.highlights.isNotEmpty()) {
                        StepHighlights(step, colorGold, compact)
                    }
                }
                if (!compact) StepDots(stepIndex, colorGold)
                StepButtons(stepIndex, isLast, showDots = compact, onSkip = onDismiss, onBack = { stepIndex-- }) {
                    if (isLast) onDismiss() else stepIndex++
                }
            }
        }
    }
}

@Composable
private fun StepTitle(step: TutorialStep, colorGold: Color) {
    Text(
        text = step.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = colorGold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StepHighlights(step: TutorialStep, colorGold: Color, compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorGold.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        step.highlights.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = colorGold, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = if (compact) 16.sp else 18.sp
                )
            }
        }
    }
}

@Composable
private fun StepDots(stepIndex: Int, colorGold: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tutorialSteps.indices.forEach { i ->
            Box(
                modifier = Modifier
                    .size(if (i == stepIndex) 8.dp else 6.dp)
                    .background(if (i == stepIndex) colorGold else ColorSlate, CircleShape)
            )
        }
    }
}

/** Skip on the left, Back and Next/Got it on the right; in the compact layout the step dots sit between them. */
@Composable
private fun StepButtons(
    stepIndex: Int,
    isLast: Boolean,
    showDots: Boolean,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (!isLast) {
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(text = "Skip", fontWeight = FontWeight.Bold, color = ColorTextDim)
            }
        }
        if (showDots) StepDots(stepIndex, colorGold)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (stepIndex > 0) {
                TextButton(onClick = onBack) {
                    Text(text = "Back", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(
                onClick = onNext,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorGold,
                    contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isLast) "Got it" else "Next",
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
