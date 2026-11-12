package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.ui.components.rememberWindowRoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class PathStep(val icon: ImageVector, val label: String)

private val pathSteps = listOf(
    PathStep(Icons.Rounded.Link, "Paste a link"),
    PathStep(Icons.Rounded.AutoAwesome, "AI transcribes"),
    PathStep(Icons.Rounded.Piano, "Play along")
)

/** The steps light up once per app run, not every time the user comes back to the Learn tab. */
private var pathLightPlayed = false

/**
 * The app in three glances for new users, under the Convert card where the empty library leaves room:
 * three quiet steps joined by a dotted thread, and a gold link into the full walkthrough. Shortly after
 * the screen opens a light runs along the thread once, waking each step in turn, then everything settles
 * back to muted. The whole thing is one tap target; it stays until the walkthrough has been finished, or
 * until [onHide] from the small close mark in its corner.
 */
@Composable
internal fun HowItWorksPath(
    onClick: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = LocalAppTheme.current.primaryColor
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val light = remember { Animatable(if (pathLightPlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (pathLightPlayed) return@LaunchedEffect
        delay(700)
        light.animateTo(1f, tween(durationMillis = 1800, easing = LinearEasing))
        pathLightPlayed = true
    }
    // The light's position along the path: 0 at the first step, 2 at the last, past both ends while idle
    val at = -0.6f + light.value * 3.2f
    fun glowAt(position: Float) = (1f - abs(at - position) / 0.7f).coerceIn(0f, 1f)

    val isShort = rememberWindowRoom().isShort
    val tappable = Modifier
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {
            contentDescription = "How it works: paste a link, AI transcribes, play along. Opens a short walkthrough"
            role = Role.Button
        }

    val steps = @Composable { stepsModifier: Modifier ->
        Row(modifier = stepsModifier.clearAndSetSemantics { }, verticalAlignment = Alignment.Top) {
            pathSteps.forEachIndexed { i, step ->
                if (i > 0) {
                    Thread(
                        color = lerp(muted.copy(alpha = 0.25f), gold.copy(alpha = 0.7f), glowAt(i - 0.5f)),
                        modifier = Modifier
                            .weight(0.3f)
                            .padding(top = StepCircle / 2)
                    )
                }
                Step(step, glow = glowAt(i.toFloat()), gold = gold, muted = muted, modifier = Modifier.weight(1f))
            }
        }
    }
    val link = @Composable { linkModifier: Modifier ->
        Text(
            text = "See how it works ›",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = gold.copy(alpha = 0.9f),
            maxLines = 1,
            softWrap = false,
            modifier = linkModifier.clearAndSetSemantics { }
        )
    }

    // The close mark is its own small target, so hiding never opens the walkthrough by mistake. It hangs
    // a little past the corner, clear of the steps, so they keep their full width for the captions.
    val close = @Composable { closeModifier: Modifier ->
        Box(
            modifier = closeModifier
                .size(CloseTarget)
                .clip(CircleShape)
                .clickable(onClick = onHide, onClickLabel = "Hide")
                .semantics { contentDescription = "Hide How it works" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = muted.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (isShort) {
        // The Convert card fills most of a landscape screen, so the link sits beside the steps instead of
        // below them: whenever the steps peek over the bottom edge, the way into the walkthrough does too.
        // The close mark takes the top-left corner, away from the link.
        Box(
            modifier = modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = tappable
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                steps(Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                // Level with the circles' centres
                link(Modifier.height(StepCircle).wrapContentHeight(Alignment.CenterVertically))
            }
            close(Modifier.align(Alignment.TopStart).offset(x = -CloseOverhang, y = -CloseOverhang))
        }
    } else {
        Box(
            modifier = modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = tappable
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                steps(Modifier.fillMaxWidth())
                link(Modifier)
            }
            close(Modifier.align(Alignment.TopEnd).offset(x = CloseOverhang, y = -CloseOverhang))
        }
    }
}

private val CloseTarget = 36.dp
private val CloseOverhang = 8.dp

private val StepCircle = 34.dp

@Composable
private fun Step(step: PathStep, glow: Float, gold: Color, muted: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(StepCircle)
                .background(gold.copy(alpha = 0.06f + 0.12f * glow), CircleShape)
                .border(BorderStroke(1.dp, lerp(muted.copy(alpha = 0.22f), gold.copy(alpha = 0.6f), glow)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = lerp(muted.copy(alpha = 0.7f), gold, glow),
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = step.label,
            style = MaterialTheme.typography.labelSmall,
            color = muted.copy(alpha = 0.75f + 0.25f * glow),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** The dotted thread between two steps, level with the circles' centres. */
@Composable
private fun Thread(color: Color, modifier: Modifier) {
    Canvas(modifier.height(1.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 4.dp.toPx()))
        )
    }
}
