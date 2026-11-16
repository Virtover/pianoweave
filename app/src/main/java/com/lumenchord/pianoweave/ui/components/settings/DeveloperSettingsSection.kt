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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

/**
 * Background: Developer (the cat background, logo and launcher icon) or Regular (the usual ones). The developer
 * extras stay either way. The launcher icon changes once the app goes to the background.
 */
@Composable
fun DeveloperLookChoice(
    developerLook: Boolean,
    onChange: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Background", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(true to "Developer", false to "Regular").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = developerLook == value,
                    onClick = { if (developerLook != value) onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = accent.copy(alpha = 0.22f))
                ) { Text(label) }
            }
        }
        Text(
            text = if (developerLook) "The cat background, logo and app icon"
            else "The regular background, logo and app icon; developer extras stay on",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
                DeveloperLookChoice(
                    developerLook = viewModel.isDeveloperBackgroundEnabled,
                    onChange = { viewModel.setDeveloperBackgroundEnabled(context, it) },
                    accent = previewTheme.primaryColor,
                    modifier = Modifier.padding(vertical = 6.dp)
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
