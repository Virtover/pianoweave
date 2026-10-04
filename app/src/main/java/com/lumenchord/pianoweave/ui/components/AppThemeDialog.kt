package com.lumenchord.pianoweave.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun AppThemeDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    AppSettingsDialog(
        viewModel = viewModel,
        context = context,
        onDismiss = onDismiss
    )
}
