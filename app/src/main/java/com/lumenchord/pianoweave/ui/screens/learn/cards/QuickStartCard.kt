package com.lumenchord.pianoweave.ui.screens.learn.cards

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text-only Quick Start prompts: no container, outline, wash or icon. Flip [ActiveStyle] to compare;
 * once one is chosen, delete the rest and this enum.
 *
 *  - [Hint]       centred, same voice as the Terms footnote: "New here? See how it works · Hide"
 *  - [Link]       a left-aligned accent link, with "Hide" pushed to the far edge
 *  - [Sentence]   one muted line saying what the app does, then the link; the most inviting
 *  - [Underlined] a plain underlined phrase and a × glyph; the most invisible
 */
internal enum class QuickStartStyle { Hint, Link, Sentence, Underlined }

private val ActiveStyle = QuickStartStyle.Hint

/**
 * Quiet "How it works" prompt for new users on the Learn tab. Tapping the link opens the
 * walkthrough; "Hide" (or starting a transcription) dismisses it.
 */
@Composable
internal fun QuickStartCard(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: QuickStartStyle = ActiveStyle
) {
    when (style) {
        QuickStartStyle.Hint -> HintLine(onClick, onDismiss, modifier)
        QuickStartStyle.Link -> LinkLine(onClick, onDismiss, modifier)
        QuickStartStyle.Sentence -> SentenceLine(onClick, onDismiss, modifier)
        QuickStartStyle.Underlined -> UnderlinedLine(onClick, onDismiss, modifier)
    }
}

// -------------------------------------------------------------------------------------------------
// Hint:  New here? See how it works · Hide
// -------------------------------------------------------------------------------------------------
@Composable
private fun HintLine(onClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    val muted = muted()
    val labelStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint("New here? ", "See how it works"),
            style = labelStyle,
            color = muted,
            maxLines = 1,
            softWrap = false,
            // Steps down a little on the narrowest phones or with large fonts instead of wrapping
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = labelStyle.fontSize),
            modifier = Modifier
                .weight(1f, fill = false)
                .tappable("Open tutorial", onClick)
        )
        Separator()
        TextAction("Hide", onDismiss, color = muted, style = labelStyle)
    }
}

// -------------------------------------------------------------------------------------------------
// Link:  How it works ›                                  Hide
// -------------------------------------------------------------------------------------------------
@Composable
private fun LinkLine(onClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextAction(
            text = "How it works \u203A",
            onClick = onClick,
            label = "Open tutorial",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        TextAction("Hide", onDismiss, color = muted(), style = MaterialTheme.typography.labelMedium)
    }
}

// -------------------------------------------------------------------------------------------------
// Sentence:  Paste a link, get MIDI, start practicing.
//                      How it works · Hide
// -------------------------------------------------------------------------------------------------
@Composable
private fun SentenceLine(onClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    if (isLandscape()) {
        // Too little height for two lines; the one-liner says enough.
        HintLine(onClick, onDismiss, modifier)
        return
    }
    val muted = muted()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Paste a link, get MIDI, start practicing.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction(
                text = "How it works",
                onClick = onClick,
                label = "Open tutorial",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Separator()
            TextAction("Hide", onDismiss, color = muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Underlined:  How it works                                   ×
// -------------------------------------------------------------------------------------------------
@Composable
private fun UnderlinedLine(onClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    val muted = muted()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextAction(
            text = "How it works",
            onClick = onClick,
            label = "Open tutorial",
            color = muted,
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline)
        )
        Spacer(Modifier.weight(1f))
        TextAction(
            text = "\u00D7",
            onClick = onDismiss,
            label = "Dismiss Quick Start",
            color = muted.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleMedium,
            horizontalPadding = 14.dp
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Shared pieces
// -------------------------------------------------------------------------------------------------

/** A text-only button: rounded press area with generous vertical padding for an easy tap. */
@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    label: String? = null,
    horizontalPadding: Dp = 8.dp
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.tappable(label, onClick, horizontalPadding)
    )
}

private fun Modifier.tappable(
    label: String?,
    onClick: () -> Unit,
    horizontalPadding: Dp = 8.dp
): Modifier = this
    .clip(RoundedCornerShape(8.dp))
    .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
    .padding(horizontal = horizontalPadding, vertical = 10.dp)

@Composable
private fun Separator() {
    Text(
        text = "\u00B7",
        style = MaterialTheme.typography.labelMedium,
        color = muted().copy(alpha = 0.6f)
    )
}

/** "[lead]" in the surrounding muted colour, then "[link]" picked out in the accent. */
@Composable
private fun hint(lead: String, link: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.secondary
    return buildAnnotatedString {
        append(lead)
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(link) }
    }
}

@Composable
private fun muted(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

@Composable
private fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE