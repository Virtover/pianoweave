package com.lumenchord.pianoweave.ui.components.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

/** Shown only to an account the server's administrator made a developer's (see PianoWeaveViewModelDeveloper). */
@Composable
fun DeveloperSettingsSection(
    viewModel: PianoWeaveViewModel,
    context: Context,
    previewTheme: AppTheme,
    onOpenPanel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "DEVELOPER",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                SettingToggleRow(
                    title = "Developer background",
                    subtitle = "The developer picture behind the app, dimmed",
                    checked = viewModel.isDeveloperBackgroundEnabled,
                    onCheckedChange = { viewModel.setDeveloperBackgroundEnabled(context, it) },
                    icon = Icons.Rounded.Wallpaper,
                    accent = previewTheme.primaryColor
                )
                OutlinedButton(
                    onClick = onOpenPanel,
                    shape = PillShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = previewTheme.primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Server state and job details", color = previewTheme.primaryColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
