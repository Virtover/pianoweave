package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/** A setting's name and a one-line explanation, as every settings row shows them. */
@Composable
fun SettingLabel(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * The app's switch: on is a filled accent track with a check in the thumb; off is an outlined, quiet track,
 * so the two states differ in shape as well as color. Pass a null [onCheckedChange] when a surrounding
 * row handles the tap.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalAppTheme.current.primaryColor
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = surface,
            checkedIconColor = accent,
            checkedTrackColor = accent,
            checkedBorderColor = accent,
            uncheckedThumbColor = onSurface.copy(alpha = 0.55f),
            uncheckedTrackColor = onSurface.copy(alpha = 0.04f),
            uncheckedBorderColor = onSurface.copy(alpha = 0.3f),
            disabledCheckedTrackColor = accent.copy(alpha = 0.3f),
            disabledCheckedThumbColor = surface.copy(alpha = 0.8f),
            disabledUncheckedThumbColor = onSurface.copy(alpha = 0.25f),
            disabledUncheckedBorderColor = onSurface.copy(alpha = 0.15f)
        )
    )
}

/**
 * A setting that is on or off: optional [icon] badge, [title] and [subtitle], and an [AppSwitch]. The whole
 * row is the touch target, not just the switch.
 */
@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = LocalAppTheme.current.primaryColor
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            // Lets the press ripple reach a little past the text without shifting the row's alignment
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconBadge(icon, tint = accent, size = 32.dp)
            Spacer(Modifier.width(12.dp))
        }
        SettingLabel(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled, accent = accent)
    }
}
