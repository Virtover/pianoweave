package com.lumenchord.pianoweave

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.PianoPlayer
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.components.AdaptiveNavigation
import com.lumenchord.pianoweave.ui.components.AppSettingsDialog
import com.lumenchord.pianoweave.ui.components.BilledServerGoogleAccountDialog
import com.lumenchord.pianoweave.ui.components.NoGoogleAccountDialog
import com.lumenchord.pianoweave.ui.screens.LearnScreen
import com.lumenchord.pianoweave.ui.screens.PianoRollScreen
import com.lumenchord.pianoweave.ui.viewmodel.*
import com.lumenchord.pianoweave.ui.screens.StorageScreen
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.ui.theme.PianoWeaveTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PianoWeaveViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PianoApiFactory.initialize(application, applicationContext)
        PianoPlayer.initialize(applicationContext)
        AcousticNoteDetector.initialize(applicationContext)
        enableEdgeToEdge()

        // Load saved preferences (including theme & server settings) early
        viewModel.loadPreferences(applicationContext)

        // Enable full immersive mode to hide navigation and status bars
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Initialize physical hardware MIDI listener framework at application launch
        MidiInputManager.initialize(applicationContext)

        // Request audio permission for acoustic detection (used in Wait Mode)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val currentTheme = AppThemeManager.getTheme(viewModel.selectedThemeId)
            PianoWeaveTheme(theme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pre-load local MIDI history when screen mounts
                    LaunchedEffect(Unit) {
                        viewModel.loadSongs(this@MainActivity)
                        viewModel.checkServerHealthAndInfo(this@MainActivity)
                    }

                    // Check if an active practice session is opened
                    val activeSong = viewModel.activePracticeSong
                    if (activeSong != null) {
                        PianoRollScreen(
                            song = activeSong,
                            viewModel = viewModel,
                            onBack = {
                                viewModel.activePracticeSong = null
                                viewModel.isPlaying = false
                            }
                        )
                    } else {
                        PianoWeaveApp(
                            viewModel = viewModel,
                            context = this@MainActivity
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        AcousticNoteDetector.cleanup()
        super.onDestroy()
    }
}

private enum class AppTab {
    Learn,
    Storage
}

@Composable
private fun PianoWeaveApp(
    viewModel: PianoWeaveViewModel,
    context: Context
) {
    var selectedTab by remember { mutableStateOf(AppTab.Learn) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    // Automatically refresh local song lists whenever the user navigates to the Storage/Library tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.Storage) {
            viewModel.loadSongs(context)
        }
    }

//    LaunchedEffect(viewModel.signInCancelEvent) {
//        if (viewModel.signInCancelEvent > 0) {
//            snackbarHost.showSnackbar("Sign in to continue", duration = SnackbarDuration.Short)
//        }
//    }

    AdaptiveNavigation(
        isLearnSelected = selectedTab == AppTab.Learn,
        onLearnSelect = { selectedTab = AppTab.Learn },
        isStorageSelected = selectedTab == AppTab.Storage,
        onStorageSelect = { selectedTab = AppTab.Storage },
        onOpenSettingsDialog = { showSettingsDialog = true }
    ) {
        when (selectedTab) {
            AppTab.Learn -> {
                LearnScreen(
                    viewModel = viewModel,
                    context = context,
                    onSongSelect = { selectedSong ->
                        viewModel.openPracticeSession(context, selectedSong)
                        viewModel.readySong = null
                    }
                )
            }

            AppTab.Storage -> {
                StorageScreen(
                    viewModel = viewModel,
                    songs = viewModel.songs,
                    onSongsChange = { /* Handled reactively by viewModel state updates */ },
                    context = context,
                    onSongSelect = { clickedSong ->
                        viewModel.openPracticeSession(context, clickedSong)
                    },
                    onDeleteClick = { songToDelete ->
                        viewModel.deleteSong(context, songToDelete)
                    },
                    onImportMidi = { uri ->
                        viewModel.importMidiFile(context, uri)
                    },
                    importError = viewModel.importError,
                    onClearImportError = {
                        viewModel.clearImportError()
                    }
                )
            }
        }
    }

    if (showSettingsDialog) {
        AppSettingsDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (viewModel.showNoGoogleAccountDialog) {
        NoGoogleAccountDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = {
                viewModel.signInCancelEvent++
                viewModel.showNoGoogleAccountDialog = false
            }
        )
    }

    if (viewModel.showBilledServerGoogleAccountDialog) {
        BilledServerGoogleAccountDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = {
                viewModel.dismissBilledServerGoogleAccountDialog()
            }
        )
    }
}
