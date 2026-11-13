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

Use the `scripts/` wrappers (Git Bash): they print only errors, failures and a one-line result instead of Gradle's
full output, and always target `emulator-5554`.

```bash
scripts/check.sh [-w]                      # compile check (-w also lists warnings)
scripts/test.sh ['*VoiceCommandsTest']     # JVM unit tests (app/src/test), optional filter
scripts/itest.sh <TestClass> [am args]     # install app + test APK on the emulator, run one class
scripts/logcat.sh <Tag>... [-n 150]        # emulator logcat for given tags only (-c clears)
scripts/ui.sh                              # on-screen texts + tap coordinates (cheap, text only)
scripts/shot.sh [out.png]                  # screenshot downscaled to 720 px (default build/shot.png)
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug -q
```

**Keeping agent context small:** check UI with `scripts/ui.sh` first, `scripts/shot.sh` only when looks matter;
never dump full logcat or raw Gradle/`am instrument` output; read large files by line range; don't read
assets/binaries. Simple "where is X" lookups can go to the `finder` agent (Haiku).

**Devices:** the owner's physical phone is often attached alongside the emulator
(`emulator-5554`). Plain `installDebug` installs on *every* connected device, so always set
`ANDROID_SERIAL=emulator-5554` and use `adb -s emulator-5554`. Never install to, clear, or change the phone without asking.

**Instrumented tests:** don't use `connectedDebugAndroidTest` (it uninstalls the app afterwards, wiping its data).
`scripts/itest.sh` installs both APKs to the emulator and runs `am instrument` (e.g.
`scripts/itest.sh MidiKeyboardInstrumentedTest`).

`MidiKeyboardInstrumentedTest` uses a test-only virtual MIDI keyboard (`LoopbackMidiService`, in the test APK).
While the test APK is installed that keyboard exists system-wide and the app treats it as a real MIDI keyboard
(wait mode stops using the mic), so uninstall `com.lumenchord.pianoweave.test` after testing on any real device.
Test-APK services run in the test APK's own process, which has no Kotlin runtime: write them in Java.

`ExampleInstrumentedTest` is stale (expects package `com.example.ytpiano`) and fails on its own.

**Checking UI on the emulator:** `scripts/ui.sh` for text, `scripts/shot.sh` and read the PNG for looks. Animations are faster than
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
- **Voice commands** (piano roll, off by default): one-word commands, no wake word. The mic is shared with
  wait-mode note detection (`AcousticNoteDetector`); speech is found by Silero VAD on DTLN-cleaned audio plus a
  raw Vosk stream, decoded by Vosk with a command grammar. Design notes, thresholds, measurements and test/replay
  tools are in `app/src/main/java/com/lumenchord/pianoweave/audio/CLAUDE.md` (read it before changing voice code,
  including the UI side in `pianoroll/VoiceCommand*.kt`).
- **Terms of Service versions**: each version is `docs/terms/<effective date>.html` + `_pl.html`, listed in
  `docs/terms_versions.json`. `docs/terms.js` picks by today's UTC date: the fixed URLs
  (`terms_of_service[_upcoming][_pl].html`, which the app links) redirect, and version pages show an upcoming /
  archived box. The app (`LegalConsent`) fetches the same list: from a version's date the transcription consent asks
  again; before it, people who already had the app see `TermsChangeDialog` once (fresh installs don't). To change the
  Terms: add both pages, the date (also in `LegalConsent.BUNDLED_VERSIONS`) and links in the fixed pages' fallback
  lists, at least 14 days ahead (the Terms promise that notice). The Privacy Policy isn't versioned.
- **Piano arrangements**: when server-info says `arrangement: true`, Transcribe opens `TranscriptionModeDialog`
  (transcription vs arrangement, one style: `level2`). An arrangement's library key is its source key plus
  `#piano-arrangement` (`libraryKey()` in `api/TranscriptionModels.kt`), so it never collides with the transcription
  of the same link and stays a working link; the mode is read back from the key (resume, cards, library tag).
- **In-app updates** (`update/AppUpdates.kt`, Play `app-update`): once per start, after the permission prompts, a
  newer Play version is offered as a flexible update (Restart snackbar when downloaded); priority 4–5 forces the
  immediate flow; a declined version rests 3 days. Debug builds: Settings → "Simulate app update (debug)" runs it
  against `FakeAppUpdateManager`. A real check only works on a Play install (internal app sharing).
- **Server announcements** (`api/Announcements.kt`, server `GET /api/announcements`): fetched at launch, on every
  resume (throttled 30 s) and on server change, from the selected server *and* the official one (a takedown
  notice comes from there). Each server's last good list is kept in prefs and stays shown on 404/503/offline.
  Critical non-dismissible → full-screen `Dialog` (Back can't close it; "Hide this message" goes through a
  warning with checkbox + 5 s countdown and moves it to the Learn banner, whose ✕ then moves it to the bottom of
  Settings, from where it can go back); critical dismissible → pop-up once per run; the rest → the Learn
  screen's `AnnouncementBanners`. Links open only `https` in the browser. To test against a local mock on the
  API 37 emulator use `adb reverse tcp:PORT tcp:PORT` + `http://127.0.0.1:PORT/`: `10.0.2.2` is a local-network
  address, which the app can't reach on API 37 without a local-network permission.
- **Piano roll tutorial**: 4-step dialog (incl. voice commands) on first open (`HAS_SEEN_PIANO_ROLL_TUTORIAL`); closes only via its buttons.
- **Verse of Light** (hidden): hold the round Piano Weave icon (top bar / rail) for 7 s the first time, 3 s the
  second, 2 s after (`VERSE_OF_LIGHT_OPEN_COUNT`); a normal tap still opens Settings. Shows a gold cross and a
  random verse from `bible_verses.json` with the matching Hebrew name of God, revealed by a shine sweep that copies
  the splash icon's animation (`res/drawable/splash_icon*.xml`). Tapping the name or verse copies it. Its look is
  meant to match the splash screen: palette `#0F0B09` background, `#FFDEB2` glow, ribbon golds.
