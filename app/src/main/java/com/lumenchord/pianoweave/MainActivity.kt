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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.lumenchord.pianoweave.ui.components.ServerConnectionDialog
import com.lumenchord.pianoweave.ui.screens.LearnScreen
import com.lumenchord.pianoweave.ui.screens.PianoRollScreen
import com.lumenchord.pianoweave.ui.viewmodel.*
import com.lumenchord.pianoweave.ui.screens.StorageScreen
import com.lumenchord.pianoweave.ui.screens.VerseOfLightScreen
import com.lumenchord.pianoweave.ui.screens.learn.ServerSettingsDialog
import com.lumenchord.pianoweave.ui.screens.learn.WelcomeTutorialDialog
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.ui.theme.PianoWeaveTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.util.AppPrefs

class MainActivity : ComponentActivity() {

    private val viewModel: PianoWeaveViewModel by viewModels()

    // One request for all of them: Android shows one permission request at a time and drops a second
    // one made while the first is open, so the notification prompt never appeared on a fresh install
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PianoApiFactory.initialize(application, applicationContext)
        PianoPlayer.initialize(applicationContext)
        // Loads a large model; off the main thread so it never holds up the first frame
        thread(name = "AcousticInit") { AcousticNoteDetector.initialize(applicationContext) }
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

        // Microphone for acoustic detection (wait mode), notifications for finished transcriptions and
        // exports, and on Android 9 and below storage, which saving an export to Downloads needs there
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissionsLauncher.launch(wanted.toTypedArray())

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
        // Only when leaving for good; a recreated activity (e.g. resized window) keeps the loaded model
        if (isFinishing) AcousticNoteDetector.cleanup()
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
    // Tabs are pages, so Learn and Library can also be reached by swiping left or right
    val pagerState = rememberPagerState { AppTab.entries.size }
    val scope = rememberCoroutineScope()
    val selectedTab = AppTab.entries[pagerState.currentPage]
    val selectTab = { tab: AppTab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) }; Unit }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showVerseOfLight by remember { mutableStateOf(false) }
    // The first discovery takes a longer hold; once found, shorter is enough
    var verseOfLightOpens by remember {
        val prefs = AppPrefs.get(context)
        val legacyOpened = if (prefs.getBoolean(AppPrefs.HAS_OPENED_VERSE_OF_LIGHT, false)) 1 else 0
        mutableIntStateOf(prefs.getInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, legacyOpened))
    }
    val snackbarHost = remember { SnackbarHostState() }

    // Automatically refresh local song lists whenever the user navigates to the Storage/Library tab
    LaunchedEffect(pagerState.settledPage) {
        if (AppTab.entries[pagerState.settledPage] == AppTab.Storage) {
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
        onLearnSelect = { selectTab(AppTab.Learn) },
        isStorageSelected = selectedTab == AppTab.Storage,
        onStorageSelect = { selectTab(AppTab.Storage) },
        onOpenSettingsDialog = { showSettingsDialog = true },
        logoHoldMillis = when (verseOfLightOpens) {
            0 -> 7_000L
            1 -> 3_000L
            else -> 2_000L
        },
        onLogoLongHold = {
            showVerseOfLight = true
            if (verseOfLightOpens < 2) {
                verseOfLightOpens++
                AppPrefs.get(context).edit().putInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, verseOfLightOpens).apply()
            }
        }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { AppTab.entries[it] }
        ) { page ->
            when (AppTab.entries[page]) {
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
    }

    if (showVerseOfLight) {
        VerseOfLightScreen(onClose = { showVerseOfLight = false })
    }

    LaunchedEffect(Unit) { viewModel.showWelcomeTutorialIfFirstOpen(context) }

    // The welcome tutorial goes first; a connection problem found meanwhile is explained after it
    val connectionProblem = viewModel.startupConnectionProblem
    if (viewModel.showWelcomeTutorial) {
        WelcomeTutorialDialog(onDismiss = { viewModel.dismissWelcomeTutorial(context) })
    } else if (connectionProblem != null) {
        ServerConnectionDialog(
            problem = connectionProblem,
            isCustomServer = !viewModel.isUsingDefaultServer,
            isChecking = viewModel.serverStatus == ServerStatus.CHECKING,
            onRetry = { viewModel.checkServerHealthAndInfo(context) },
            onOpenServerSettings = { viewModel.showServerSettingsDialog = true },
            onDismiss = { viewModel.startupConnectionProblem = null }
        )
    }

    if (viewModel.showServerSettingsDialog) {
        ServerSettingsDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { viewModel.showServerSettingsDialog = false }
        )
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
