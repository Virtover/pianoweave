package com.example.ytpiano

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.ytpiano.midi.MidiInputManager
import com.example.ytpiano.ui.components.AdaptiveNavigation
import com.example.ytpiano.ui.screens.LearnScreen
import com.example.ytpiano.ui.screens.PianoRollScreen
import com.example.ytpiano.ui.screens.StorageScreen
import com.example.ytpiano.ui.theme.PianoLearnerTheme
import com.example.ytpiano.ui.viewmodel.PianoLearnerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PianoLearnerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize physical hardware MIDI listener framework at application launch
        MidiInputManager.initialize(applicationContext)

        setContent {
            PianoLearnerTheme {
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
                            onBack = { viewModel.activePracticeSong = null }
                        )
                    } else {
                        PianoLearnerApp(
                            viewModel = viewModel,
                            context = applicationContext
                        )
                    }
                }
            }
        }
    }
}

private enum class AppTab {
    Learn,
    Storage
}

@Composable
private fun PianoLearnerApp(
    viewModel: PianoLearnerViewModel,
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
