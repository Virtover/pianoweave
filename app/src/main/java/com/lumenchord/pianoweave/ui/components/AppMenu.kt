package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape

/**
 * The app's dropdown: a rounded panel with a faint edge, dressed like the round buttons that open it.
 * An optional [heading] names what the menu is about.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    heading: String? = null,
    headingIsName: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        containerColor = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        shadowElevation = 8.dp,
        offset = DpOffset(0.dp, 6.dp)
    ) {
        if (heading != null) {
            Text(
                // A label ("Playback speed") is set in small caps; a name (a song title) keeps its own case
                text = if (headingIsName) heading else heading.uppercase(),
                fontSize = if (headingIsName) 13.sp else 10.sp,
                fontWeight = if (headingIsName) FontWeight.SemiBold else FontWeight.Bold,
                letterSpacing = if (headingIsName) 0.sp else 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (headingIsName) 0.6f else 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 6.dp)
            )
        }
        content()
    }
}

/**
 * One choice in an [AppDropdownMenu]: a pill-shaped row with an optional [icon] in a small badge, the
 * [text], and an optional [hint] beside it. A [selected] row is tinted and checked.
 */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hint: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color = LocalAppTheme.current.primaryColor,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    role: Role = Role.Button
) {
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .widthIn(min = 148.dp)
            .clip(PillShape)
            .background(if (selected) tint.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = if (icon != null) 6.dp else 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconBadge(icon, tint = tint, size = 30.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) tint else textColor,
            // Leaves the row's height to the badge, so rows with and without icons line up
            modifier = Modifier.padding(vertical = if (icon != null) 0.dp else 3.dp)
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(18.dp)
            )
        }
    }
}

/** Sets a group of items apart, e.g. a destructive one, without running edge to edge. */
@Composable
fun AppMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}
