package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import kotlin.math.roundToInt

private data class WelcomeStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** A small copy of the screen the step talks about, so its buttons are easy to spot afterwards. */
    val picture: @Composable () -> Unit
)

private val welcomeSteps = listOf(
    WelcomeStep(
        icon = Icons.Default.Piano,
        title = "Welcome to Piano Weave",
        body = "Turn a piano video into notes that fall onto the keys, so you can play along. It takes three simple steps.",
        picture = { PianoRollPicture() }
    ),
    WelcomeStep(
        icon = Icons.Default.Link,
        title = "1. Copy a video's link",
        body = "Find a piano video you like, for example on YouTube. Tap Share under the video, then tap Copy link.",
        picture = { CopyLinkPicture() }
    ),
    WelcomeStep(
        icon = Icons.Default.AutoAwesome,
        title = "2. Paste it & tap Transcribe",
        body = "Come back to Piano Weave. Tap the paste button in the link box, then tap Transcribe. In a few minutes, the music turns into notes.",
        picture = { TranscribePicture() }
    ),
    WelcomeStep(
        icon = Icons.Default.PlayArrow,
        title = "3. Play along",
        body = "When it's ready, tap Start practicing and play the falling notes on your piano. Your songs are kept in the Library tab.",
        picture = { PlayAlongPicture() }
    )
)

/** Below this the pictures get too small to read; they scroll instead. */
private const val MIN_PICTURE_SCALE = 0.6f

/**
 * First-open walkthrough for people new to apps like this: copy a link, transcribe it, open the piano roll.
 * Skip (or Back) closes it for good, as does finishing it.
 *
 * Meant to fit the screen without scrolling: text above the picture in portrait, beside it in landscape,
 * and the picture shrinks into the space left. Only when it would get too small does the picture scroll,
 * with the step's buttons staying in place.
 */
@Composable
internal fun WelcomeTutorialDialog(onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = welcomeSteps[stepIndex]

    // Back skips like the Skip button; a stray tap outside shouldn't lose the walkthrough.
    // The platform's default width is too narrow for the side-by-side landscape layout.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val isWide = maxWidth > maxHeight && maxWidth >= 560.dp
            val isShort = maxHeight < 640.dp
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .widthIn(max = if (isWide) 760.dp else 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                val controls = @Composable {
                    StepControls(
                        stepIndex = stepIndex,
                        onSkip = onDismiss,
                        onBack = { stepIndex-- },
                        onNext = { if (stepIndex == welcomeSteps.lastIndex) onDismiss() else stepIndex++ }
                    )
                }
                if (isWide) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(0.42f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // On a very short screen the text gives way before the buttons do
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StepText(step, iconSize = 44.dp)
                            }
                            controls()
                        }
                        StepPicture(stepIndex, step, Modifier.weight(0.58f))
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StepText(step, iconSize = if (isShort) 40.dp else 52.dp)
                        StepPicture(stepIndex, step, Modifier.weight(1f, fill = false))
                        controls()
                    }
                }
            }
        }
    }
}

@Composable
private fun StepText(step: WelcomeStep, iconSize: Dp) {
    val colorGold = LocalAppTheme.current.primaryColor
    IconBadge(step.icon, tint = colorGold, size = iconSize)
    Text(
        text = step.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = colorGold,
        textAlign = TextAlign.Center
    )
    Text(
        text = step.body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 23.sp,
        textAlign = TextAlign.Center
    )
}

/**
 * The step's picture, shrunk to fit the space it's given. If it would fall below [MIN_PICTURE_SCALE] it
 * scrolls instead: the cut-off edge fades out and an arrow below shows there's more.
 */
@Composable
private fun StepPicture(stepIndex: Int, step: WelcomeStep, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // A new step starts at its top
        val scroll = key(stepIndex) { rememberScrollState() }
        val availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        ScaledToFit(
            availableHeight = availableHeight,
            modifier = Modifier
                .fadingEdges(scroll)
                .verticalScroll(scroll)
        ) {
            step.picture()
        }
        AnimatedVisibility(
            visible = scroll.canScrollForward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MoreBelowHint()
        }
    }
}

/**
 * Lays out [content] at full width and natural height, then scales it down (never up) to fit
 * [availableHeight], but not below [MIN_PICTURE_SCALE]. It reports its scaled size, so whatever
 * still doesn't fit can scroll.
 */
@Composable
private fun ScaledToFit(availableHeight: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val scale = if (availableHeight == Constraints.Infinity || placeable.height <= availableHeight) 1f
        else (availableHeight.toFloat() / placeable.height).coerceAtLeast(MIN_PICTURE_SCALE)
        val height = (placeable.height * scale).roundToInt()
        layout(placeable.width, height) {
            // Scaling is about the center, so center the full-size placeable on the scaled box
            placeable.placeWithLayer(0, (height - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** Fades out the top or bottom edge while there is more to scroll that way. */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn
            )
        }
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** A gently bobbing arrow: the picture goes on below. */
@Composable
private fun MoreBelowHint() {
    val bob by rememberInfiniteTransition(label = "moreBelow").animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "moreBelowBob"
    )
    Box(
        modifier = Modifier
            .offset(y = bob.dp)
            .size(28.dp)
            .background(LocalAppTheme.current.primaryColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Scroll for more",
            tint = if (LocalAppTheme.current.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun StepControls(stepIndex: Int, onSkip: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val isLast = stepIndex == welcomeSteps.lastIndex
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            welcomeSteps.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == stepIndex) 8.dp else 6.dp)
                        .background(if (i == stepIndex) colorGold else MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
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
                TextButton(onClick = onSkip) {
                    Text(text = "Skip", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
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
                        text = when {
                            isLast -> "Let's start"
                            stepIndex == 0 -> "Show me"
                            else -> "Next"
                        },
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
