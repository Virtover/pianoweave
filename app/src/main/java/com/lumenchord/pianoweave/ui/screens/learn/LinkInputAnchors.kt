package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where the link field and the Transcribe button are on screen, so the Learn screen can scroll
 * them above the keyboard while a link is typed.
 */
@Stable
class LinkInputAnchors {
    var isLinkFieldFocused by mutableStateOf(false)
    var linkFieldTopInRoot by mutableFloatStateOf(0f)
    var transcribeButtonBottomInRoot by mutableFloatStateOf(0f)
}
