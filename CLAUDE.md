# CLAUDE.md

Quick orientation for Claude Code sessions in this repo. Read this first; README.md has the full setup.

## What this is

**Piano Weave** (brand: Lumenchord, package `com.lumenchord.pianoweave`): an Android app that turns piano
performances from online videos into MIDI and lets you practice them on an interactive falling-notes piano roll.
Kotlin + Jetpack Compose (Material 3), single module `:app`, minSdk 26, targetSdk 37. Owner/author: Krzysztof
Olszak (GitHub `Virtover`). Source is PolyForm Noncommercial (see `LICENSE`, `COMMERCIAL.MD`).

Transcription runs on a separate FastAPI server ([piano-transcription-server](https://github.com/Virtover/piano-transcription-server));
the app talks to it via Retrofit. Audio is FluidSynth (bundled SoundFont) through a small native bridge.

## Layout (`app/src/main/java/com/lumenchord/pianoweave/`)

- `MainActivity.kt` – entry; `PianoWeaveApp` switches Learn/Library tabs, hosts app-level dialogs and overlays.
- `ui/viewmodel/` – one `PianoWeaveViewModel`, split by area into `PianoWeaveViewModel<Area>.kt` files of
  extension functions (Playback, Upload, Cloud, Auth, Server, Recording, Transcription). Add new logic the same way.
- `ui/screens/` – `LearnScreen` (transcribe), `StorageScreen` (library), `PianoRollScreen` + `pianoroll/`
  (visualizer, keyboard, toolbar, settings, tutorial), `VerseOfLightScreen` (hidden, see below).
- `ui/components/` – shared dialogs and widgets (`AdaptiveNavigation`, `PianoWeaveWordmark`, `LongHoldClickable`, …).
- `ui/theme/` – `AppTheme`/`AppThemeManager` (many selectable accent themes, read via `LocalAppTheme.current`),
  `Type.kt` (fonts).
- `audio/` – `PianoPlayer` (FluidSynth), `AcousticNoteDetector` (mic + TFLite note model, used by wait mode),
  `NativeAudioEngine` (JNI, `app/src/main/cpp/`).
- `midi/` – MIDI file read/write/storage, `MidiInputManager` (USB/virtual MIDI keyboards) + `MidiNoteParser`.
- `api/`, `auth/`, `billing/`, `cloud/` – server API, Google sign-in, Play Billing, Google Drive sync.
- `util/AppPrefs.kt` – **every** SharedPreferences key lives here; add new keys here, not inline.

Assets (`app/src/main/assets/`): SoundFont, TFLite models, `bible_verses.json`, `config/config.txt` (gitignored;
copy from `config.example.txt`), `licenses/` (OFL texts for bundled fonts).

## Build & test

```bash
./gradlew :app:compileDebugKotlin          # fast compile check
./gradlew :app:testDebugUnitTest           # JVM unit tests (app/src/test)
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
```

**Devices:** the owner's physical phone is often attached alongside the emulator
(`emulator-5554`). Plain `installDebug` installs on *every* connected device, so always set
`ANDROID_SERIAL=emulator-5554` and use `adb -s emulator-5554`. Never install to, clear, or change the phone without asking.

**Instrumented tests:** don't use `connectedDebugAndroidTest` (it uninstalls the app afterwards, wiping its data).
Install both APKs to the emulator and run with `am instrument`:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug :app:installDebugAndroidTest
adb -s emulator-5554 shell am instrument -w -e class com.lumenchord.pianoweave.midi.MidiKeyboardInstrumentedTest \
  com.lumenchord.pianoweave.test/androidx.test.runner.AndroidJUnitRunner
```

`MidiKeyboardInstrumentedTest` uses a test-only virtual MIDI keyboard (`LoopbackMidiService`, in the test APK).
While the test APK is installed that keyboard exists system-wide and the app treats it as a real MIDI keyboard
(wait mode stops using the mic), so uninstall `com.lumenchord.pianoweave.test` after testing on any real device.
Test-APK services run in the test APK's own process, which has no Kotlin runtime: write them in Java.

`ExampleInstrumentedTest` is stale (expects package `com.example.ytpiano`) and fails on its own.

**Checking UI on the emulator:** `adb exec-out screencap -p` and read the PNG. Animations are faster than
screencap; to catch frames, temporarily `adb shell settings put global animator_duration_scale 10` (Compose
follows it), capture, then **set it back to 1**. In Git Bash, prefix adb commands that take device paths
(`/sdcard/...`) with `MSYS_NO_PATHCONV=1`. A fresh install shows notification/mic permission prompts; grant with
`adb shell pm grant com.lumenchord.pianoweave android.permission.POST_NOTIFICATIONS` (and `RECORD_AUDIO`).

## Conventions

- Match surrounding code: short KDoc on non-obvious composables/functions, comments explain *why*.
- Colors come from `LocalAppTheme.current` / `MaterialTheme.colorScheme`; piano-roll shorthands are in
  `pianoroll/PianoRollColors.kt`. Dialogs follow the pattern in `ui/components/MicPermissionDialog.kt`.
- Fonts: Commissioner (`DisplayFontFamily`) for wordmark/headings; Lora (`LetterFontFamily`) only on the verse
  screen. A bundled third-party font or asset needs its license text in `assets/licenses/`.
- Layouts must work in portrait (top bar + bottom nav) and landscape (navigation rail).
- Commit messages: short imperative summary, e.g. "Add onboarding tutorial dialog to PianoRollScreen".
- Never commit `keystore.properties`, `local.properties` or `assets/config/config.txt`.

## Feature notes (not obvious from the code)

- **Wait mode** (piano roll): pauses at each note until it's played. Input is a connected MIDI keyboard if any
  (`MidiInputManager.isConnected`, observable), otherwise the microphone (`AcousticNoteDetector`). The screen
  switches between them live when a keyboard is plugged in or out.
- **Voice commands** (piano roll, off by default): one-word commands with no wake word. The mic capture in
  `AcousticNoteDetector` is shared: note detection (`start`/`stop`) and voice (`startVoice`/`stopVoice`) each
  want it, and the note model only runs for note detection. `UtteranceSegmenter` keeps only short utterances
  (≤ ~2.5 s) with pauses around them, from Silero VAD chunks; Vosk (`VoiceCommandRecognizer`, grammar =
  `VoiceCommand` phrases) decodes only those; several commands in a row are one action ("full screen wait").
  Silero is trained to ignore music and rated commands over a piano 0.00 for minutes (phone recording), so
  the voice stream is first cleaned by DTLN (`SpeechEnhancer`, assets `dtln_*.tflite`, ~4 MB, MIT): Silero
  rates the cleaned audio, but Vosk decodes the raw audio (cleaned audio lost short words like "play").
  On two phone recordings this caught 52 and 52 commands where the raw path caught 21 and 42. Speech
  starts at 0.15, raised over a steady background. Even cleaned, Silero rated a voice from across the room
  over the piano 0.00, so Vosk also hears the whole raw stream (`CommandStream`); `CommandListener` runs both
  on one timeline and counts each stretch once (whichever gives commands first). The stream alone needs every
  word at 0.9 (it has no pause rule, and its unsure readings over the piano were mostly wrong). The stream
  alone (no VAD path) felt worse to the owner: slower (~0.6 s vs ~0.3 s) and noisier.
  Any non-command word rejects the whole utterance. Optional "Selah" first; Settings can require it
  (`VoiceListeningMode.SELAH`, which also drops the pause-before rule). No "mark": the small model confused it with "back". The model downloads to `files/voice/` (`VoiceModelStore`);
  Voice-only capture opens with the VOICE_COMMUNICATION preset (device echo canceller removes the app's own
  song, ~25 dB measured); note detection forces UNPROCESSED, so the stream reopens when wait mode toggles.
  Debug builds can switch that preset in the voice settings (`VoiceMicMode`: Call / Speech = VOICE_RECOGNITION
  / Raw). On the owner's phone Call pulls piano and voice ~25 dB down and the VAD path still works; with
  Speech or Raw the piano stays loud and mostly the stream catches commands. No clear winner; Call stays default.
  The voice gate runs a second Silero stream on level-boosted, cleaned audio (+30 dB max) for commands; the
  song is ducked 9 dB while a command may be spoken (`PianoPlayer.duckForVoice`).
  `VoiceCommandDecoder` decodes twice (word confidences + next-best readings): a command counts at
  confidence 0.7, or from 0.5 if no other reading is a different command; a word under 0.9 that another
  reading has as a different known word drops the phrase ("stop 0.75 faster" / "start faster"). "undo" takes back within 4 s. Words that sound alike to the model are left out ("mark", "skip" (heard for "slower"), "no" for
  undo; "no" is a distractor word). "go" alone isn't play: "go forward" with its last word drowned out by the
  piano came out as "go". Likewise a sure "loop" is dropped when another reading is "loop start"/"loop end". Debug builds show a chip with what a near-miss utterance sounded like
  (never for noise, or in Selah mode unless addressed). `VoiceCommandInstrumentedTest` checks TTS words (every phrase x 2 voices: clean,
  -30 dB, room noise; in voice-loud noise a miss is allowed, a wrong command never);
  it downloads the model if missing. `VoiceOverPlayingInstrumentedTest` speaks every phrase over practice
  playing rendered with the app's SoundFont (acoustic piano the app can't duck), +10 to -5 dB.
  Debug builds record what voice commands hear (`VoiceDebugRecorder`, `files/voice-debug/<time>-<mic mode>.pcm`, raw 16 kHz PCM);
  copy a session to `voice-debug/replay.pcm` on the emulator and run `ReplayVoiceSessionTest` (logs `ReplayVoice`)
  to see what was said (full-vocabulary Vosk) next to what voice commands made of it.
- **Piano roll tutorial**: 4-step dialog (incl. voice commands) on first open (`HAS_SEEN_PIANO_ROLL_TUTORIAL`); closes only via its buttons.
- **Verse of Light** (hidden): hold the round Piano Weave icon (top bar / rail) for 7 s the first time, 3 s the
  second, 2 s after (`VERSE_OF_LIGHT_OPEN_COUNT`); a normal tap still opens Settings. Shows a gold cross and a
  random verse from `bible_verses.json` with the matching Hebrew name of God, revealed by a shine sweep that copies
  the splash icon's animation (`res/drawable/splash_icon*.xml`). Tapping the name or verse copies it. Its look is
  meant to match the splash screen: palette `#0F0B09` background, `#FFDEB2` glow, ribbon golds.
