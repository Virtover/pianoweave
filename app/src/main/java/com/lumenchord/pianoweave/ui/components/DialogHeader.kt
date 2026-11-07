package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/**
 * A dialog's title row: [icon] in a round badge, the title, and a close button when [onClose] is given.
 * Kin to the cards' badge-and-title headers, but larger. Sits under the light of a [DialogSurface],
 * which sets it apart from the content, so no divider is needed.
 */
@Composable
fun DialogHeader(
    title: String,
    icon: ImageVector,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleColor: Color = LocalAppTheme.current.primaryColor,
    iconTint: Color = titleColor
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint = iconTint, size = BadgeSize)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClose != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private val BadgeSize = 40.dp

/**
 * A dialog's title stacked under its [icon], centred, for titles too long to share a narrow phone's row
 * with the badge and close button: there, [DialogHeader] wraps them onto two lines. The title keeps to one
 * line, stepping its size down a little on the narrowest screens or with large fonts. [subtitle] says in a
 * line what the dialog is for. A wide dialog (landscape) has room for the usual row, so it gets that.
 */
@Composable
fun DialogHeroHeader(
    title: String,
    icon: ImageVector,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = LocalAppTheme.current.primaryColor
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= HeroMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogHeader(title = title, icon = icon, onClose = onClose, titleColor = titleColor)
                if (subtitle != null) HeroSubtitle(subtitle, Modifier.padding(start = BadgeSize + 12.dp))
            }
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(icon, tint = titleColor, size = HeroBadgeSize)
            Spacer(Modifier.height(12.dp))
            val titleStyle = MaterialTheme.typography.titleLarge
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = titleStyle.fontSize),
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                HeroSubtitle(subtitle, textAlign = TextAlign.Center)
            }
        }
        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun HeroSubtitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier
    )
}

private val HeroBadgeSize = 52.dp

/** From this width the title fits beside the badge, as in [DialogHeader]. */
private val HeroMaxWidth = 480.dp
