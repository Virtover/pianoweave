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
- **Piano roll tutorial**: 3-step dialog on first open (`HAS_SEEN_PIANO_ROLL_TUTORIAL`); closes only via its buttons.
- **Verse of Light** (hidden): hold the round Piano Weave icon (top bar / rail) for 7 s the first time, 3 s the
  second, 2 s after (`VERSE_OF_LIGHT_OPEN_COUNT`); a normal tap still opens Settings. Shows a gold cross and a
  random verse from `bible_verses.json` with the matching Hebrew name of God, revealed by a shine sweep that copies
  the splash icon's animation (`res/drawable/splash_icon*.xml`). Tapping the name or verse copies it. Its look is
  meant to match the splash screen: palette `#0F0B09` background, `#FFDEB2` glow, ribbon golds.
