package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

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
            Icons.Default.Mic to "Works acoustically: it listens to your real piano through the microphone. ",
            Icons.Default.Hearing to "Keep your phone near the piano, in a quiet room, for the best experience.",
            //Icons.Default.Piano to "Have a MIDI keyboard? Connect it and it's used instead."
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

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = ColorSurface,
            border = BorderStroke(1.dp, ColorSlate)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colorGold.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(step.icon, contentDescription = null, tint = colorGold, modifier = Modifier.size(30.dp))
                }

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorGold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                if (step.highlights.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorGold.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        step.highlights.forEach { (icon, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, tint = colorGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tutorialSteps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == stepIndex) 8.dp else 6.dp)
                                .background(if (i == stepIndex) colorGold else ColorSlate, CircleShape)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLast) {
                        Spacer(Modifier.width(1.dp))
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(text = "Skip", fontWeight = FontWeight.Bold, color = ColorTextDim)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (stepIndex > 0) {
                            TextButton(onClick = { stepIndex-- }) {
                                Text(text = "Back", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Button(
                            onClick = { if (isLast) onDismiss() else stepIndex++ },
                            shape = RoundedCornerShape(12.dp),
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
        }
    }
}
