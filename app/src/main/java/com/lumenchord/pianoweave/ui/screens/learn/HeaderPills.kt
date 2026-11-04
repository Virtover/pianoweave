package com.lumenchord.pianoweave.ui.screens.learn

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.billing.owedMinutes
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods

/** The Support heart's rose, softened so it sits with the page: "God loves a cheerful giver" (2 Corinthians 9:7). */
private val SupportRose = Color(0xFFE57373)

/** Depth of the swallowtail notch cut into the ribbon's end. */
private val NotchDepth = 9.dp

/**
 * The corner of the Learn screen as a bookmark ribbon, like the satin marker in a Bible, hanging from the
 * top bar's line (or the screen's top edge in landscape). Muted satin of [tint] with a fine edge of it,
 * and a soft sheen across, so the shape catches the eye while the colour stays quiet beside the
 * Transcribe card. Tapping opens what the ribbon marks.
 */
@Composable
private fun Ribbon(
    onClick: () -> Unit,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val notchPx = with(LocalDensity.current) { NotchDepth.toPx() }
    val shape = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(size.width / 2f, size.height - notchPx)
        lineTo(0f, size.height)
        close()
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = 46.dp, height = if (isLandscape) 50.dp else 58.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Satin: a little brighter down the middle than at the folds
                .background(
                    Brush.horizontalGradient(
                        listOf(tint.copy(alpha = 0.12f), tint.copy(alpha = 0.24f), tint.copy(alpha = 0.12f))
                    )
                )
                .padding(start = 4.dp, end = 4.dp, bottom = NotchDepth)
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * The credits balance on a gold ribbon; tapping it opens the shop. The number carries the weight and
 * the small word under it stays quiet. An owed balance (after a refund of used minutes) turns the
 * ribbon lamp-oil amber ([Moods.Watch]), never alarm.
 */
@Composable
fun CreditsBalancePill(
    credits: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val owed = owedMinutes(credits)
    val shown = if (owed > 0) owed else credits
    val unit = if (shown == 1) "Credit" else "Credits"
    val gold = LocalAppTheme.current.primaryColor
    val tint = if (owed > 0) Moods.Watch.core else gold
    val numberStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

    Ribbon(
        onClick = onClick,
        description = (if (owed > 0) "Owes $owed $unit" else "$credits $unit") + ". Add credits",
        tint = tint,
        modifier = modifier
    ) {
        Text(
            text = shown.toString(),
            style = numberStyle,
            color = if (owed > 0) Moods.Watch.light else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            maxLines = 1,
            softWrap = false,
            // Large balances step down rather than overflow the ribbon
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = numberStyle.fontSize)
        )
        Text(
            text = (if (owed > 0) "owed" else unit).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
            color = tint.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The invitation to support the app on a rose ribbon, where the balance would be on servers without billing. */
@Composable
fun SupportPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Ribbon(onClick = onClick, description = "Support Piano Weave", tint = SupportRose, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = SupportRose.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "SUPPORT",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false
        )
    }
}
