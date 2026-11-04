package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.VoiceListeningMode
import com.lumenchord.pianoweave.audio.VoiceMicMode
import com.lumenchord.pianoweave.audio.VoiceModelDownloadException
import com.lumenchord.pianoweave.audio.VoiceModelError
import com.lumenchord.pianoweave.audio.VoiceModelStore
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Whether the voice commands' speech model is on the device. MISSING with voice commands on happens
 * after an interrupted download (app closed, phone offline): the UI then offers the download again.
 */
enum class VoiceModelState { MISSING, DOWNLOADING, READY, FAILED }

internal fun PianoWeaveViewModel.loadVoicePreferences(context: Context) {
    val prefs = AppPrefs.get(context)
    isVoiceCommandsEnabled = prefs.getBoolean(AppPrefs.IS_VOICE_COMMANDS_ENABLED, false)
    voiceListeningMode = VoiceListeningMode.entries.firstOrNull {
        it.name == prefs.getString(AppPrefs.VOICE_LISTENING_MODE, null)
    } ?: VoiceListeningMode.WORDS
    voiceMicMode = VoiceMicMode.entries.firstOrNull {
        it.name == prefs.getString(AppPrefs.VOICE_MIC_MODE, null)
    } ?: VoiceMicMode.CALL
    AcousticNoteDetector.setVoiceMicMode(voiceMicMode)
    if (voiceModelState == VoiceModelState.DOWNLOADING) return
    // A failure stays shown (with its reason) until it's retried, rather than turning back into MISSING
    if (VoiceModelStore.isInstalled(context)) {
        voiceModelState = VoiceModelState.READY
    } else if (voiceModelState != VoiceModelState.FAILED) {
        voiceModelState = VoiceModelState.MISSING
    }
    // Models left by an earlier app version would otherwise keep their ~70 MB forever
    val appContext = context.applicationContext
    viewModelScope.launch(Dispatchers.IO) { VoiceModelStore.deleteOldModels(appContext) }
}

/** Turning voice commands on also fetches the model if it isn't there yet. */
internal fun PianoWeaveViewModel.setVoiceCommandsEnabledImpl(context: Context, enabled: Boolean) {
    isVoiceCommandsEnabled = enabled
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_VOICE_COMMANDS_ENABLED, enabled).apply()
    if (enabled && voiceModelState != VoiceModelState.READY) downloadVoiceModelImpl(context)
}

internal fun PianoWeaveViewModel.setVoiceListeningModeImpl(context: Context, mode: VoiceListeningMode) {
    voiceListeningMode = mode
    AppPrefs.get(context).edit().putString(AppPrefs.VOICE_LISTENING_MODE, mode.name).apply()
}

internal fun PianoWeaveViewModel.setVoiceMicModeImpl(context: Context, mode: VoiceMicMode) {
    voiceMicMode = mode
    AppPrefs.get(context).edit().putString(AppPrefs.VOICE_MIC_MODE, mode.name).apply()
    AcousticNoteDetector.setVoiceMicMode(mode)
}

internal fun PianoWeaveViewModel.downloadVoiceModelImpl(context: Context) {
    if (voiceModelJob?.isActive == true) return
    val appContext = context.applicationContext
    if (VoiceModelStore.isInstalled(appContext)) {
        voiceModelState = VoiceModelState.READY
        return
    }
    voiceModelState = VoiceModelState.DOWNLOADING
    voiceModelError = null
    voiceModelProgress = 0f
    voiceModelJob = viewModelScope.launch {
        voiceModelState = try {
            VoiceModelStore.download(appContext) { progress ->
                viewModelScope.launch { voiceModelProgress = progress }
            }
            VoiceModelState.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VoiceCommands", "Voice model download failed", e)
            voiceModelError = (e as? VoiceModelDownloadException)?.reason ?: VoiceModelError.SERVER
            VoiceModelState.FAILED
        }
    }
}
