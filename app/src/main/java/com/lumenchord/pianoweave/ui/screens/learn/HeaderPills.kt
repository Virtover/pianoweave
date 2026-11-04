package com.lumenchord.pianoweave.ui.screens.learn

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.components.cardWash
import com.lumenchord.pianoweave.ui.theme.Moods

/** The Support heart's rose, softened so it sits with the page: "God loves a cheerful giver" (2 Corinthians 9:7). */
private val SupportRose = Color(0xFFE57373)

/**
 * The pill both corner actions share, made of the same stuff as the screen's cards: their surface,
 * their hairline outline and their wash of [accent] from the top, so it reads as a small card of the
 * set rather than a badge laid on the page. Fixed height keeps the two the same size, so swapping one
 * for the other doesn't shift the screen; landscape tightens it as the cards tighten.
 */
@Composable
private fun HeaderPill(
    onClick: () -> Unit,
    description: String,
    accent: Color,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    content: @Composable RowScope.() -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(if (isLandscape) 26.dp else 28.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .cardWash(accent)
                .padding(start = 8.dp, end = 10.dp)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * The credits balance with a "+" to open the shop. The number carries the weight and the word stays
 * quiet, so the balance reads at a glance. An owed balance (after a refund of used minutes) turns
 * lamp-oil amber ([Moods.Watch]), never alarm. A balance not known yet (loading, signed out) shows "—".
 */
@Composable
fun CreditsBalancePill(
    credits: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val owed = owedMinutes(credits)
    val shown = if (owed > 0) owed else credits
    val unit = if (shown == 1) "Credit" else "Credits"
    val accent = MaterialTheme.colorScheme.secondary
    val numberColor = if (owed > 0) Moods.Watch.light else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
    val wordColor = if (owed > 0) Moods.Watch.light.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val style = MaterialTheme.typography.labelMedium

    HeaderPill(
        onClick = onClick,
        description = (if (owed > 0) "Owes $owed $unit" else "${shown ?: "Unknown"} $unit") + ". Add credits",
        accent = if (owed > 0) Moods.Watch.core else accent,
        borderColor = if (owed > 0) Moods.Watch.core.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.MonetizationOn,
            contentDescription = null,
            tint = if (owed > 0) Moods.Watch.core else accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        if (owed > 0) {
            Text("Owes ", style = style.copy(fontWeight = FontWeight.Medium), color = wordColor, maxLines = 1)
        }
        Text(
            text = shown?.toString() ?: "—",
            // Equal-width digits, so the pill keeps its width as the balance changes by a digit
            style = style.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = numberColor,
            maxLines = 1
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = unit,
            style = style.copy(fontWeight = FontWeight.Medium),
            color = wordColor,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** The invitation to support the app, where the balance would be on servers without billing. */
@Composable
fun SupportPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeaderPill(onClick = onClick, description = "Support Piano Weave", accent = SupportRose, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = SupportRose.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Support",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false
        )
    }
}
