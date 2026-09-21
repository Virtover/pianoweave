package com.example.pianoweave

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.pianoweave.api.PianoApiFactory
import com.example.pianoweave.audio.AcousticNoteDetector
import com.example.pianoweave.audio.PianoPlayer
import com.example.pianoweave.midi.MidiInputManager
import com.example.pianoweave.ui.components.AdaptiveNavigation
import com.example.pianoweave.ui.screens.LearnScreen
import com.example.pianoweave.ui.screens.PianoRollScreen
import com.example.pianoweave.ui.screens.StorageScreen
import com.example.pianoweave.ui.theme.PianoWeaveTheme
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PianoWeaveViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PianoApiFactory.initialize(application)
        PianoPlayer.initialize(applicationContext)
        AcousticNoteDetector.initialize(applicationContext)
        enableEdgeToEdge()

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

        setContent {
            PianoWeaveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pre-load local MIDI history when screen mounts
                    LaunchedEffect(Unit) {
                        viewModel.loadSongs(applicationContext)
                    }

                    // Check if an active practice session is opened
                    val activeSong = viewModel.activePracticeSong
                    if (activeSong != null) {
                        PianoRollScreen(
                            song = activeSong,
                            viewModel = viewModel,
                            onBack = { viewModel.activePracticeSong = null }
                        )
                    } else {
                        PianoWeaveApp(
                            viewModel = viewModel,
                            context = applicationContext
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

    // Automatically refresh local song lists whenever the user navigates to the Storage/Library tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.Storage) {
            viewModel.loadSongs(context)
        }
    }

    AdaptiveNavigation(
        isLearnSelected = selectedTab == AppTab.Learn,
        onLearnSelect = { selectedTab = AppTab.Learn },
        isStorageSelected = selectedTab == AppTab.Storage,
        onStorageSelect = { selectedTab = AppTab.Storage }
    ) {
        when (selectedTab) {
            AppTab.Learn -> {
                LearnScreen(
                    viewModel = viewModel,
                    context = context,
                    onSongSelect = { selectedSong ->
                        viewModel.activePracticeSong = selectedSong
                        viewModel.readySong = null
                    }
                )
            }

            AppTab.Storage -> {
                StorageScreen(
                    songs = viewModel.songs,
                    onSongsChange = { /* Handled reactively by viewModel state updates */ },
                    context = context,
                    onSongSelect = { clickedSong ->
                        viewModel.activePracticeSong = clickedSong
                    },
                    onDeleteClick = { songToDelete ->
                        viewModel.deleteSong(context, songToDelete)
                    }
                )
            }
        }
    }
}
