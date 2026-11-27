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
- **Legal document versions** (Terms of Service, Privacy Policy, "Above all"): each version is
  `docs/<terms|privacy|above_all>/<effective date>.html` (+ `_pl.html` for Terms and Above all), listed in
  `docs/terms_versions.json` (`versions` = Terms, the key older app releases read; `privacy_policy`; `above_all`).
  `docs/terms.js` picks by today's UTC date: the fixed URLs (`terms_of_service[_upcoming][_pl].html`,
  `privacy_policy[_upcoming].html`, `above_all[_pl].html`) redirect, and version pages show an upcoming / archived
  box. "Above all" is the developer's statement that Terms section 13 points to (users needn't agree with it); each Terms version links one Above-all version
  directly (several may link the same one), so a new Above-all version comes with a new Terms version. The app
  (`LegalConsent`) fetches the same list for Terms and Privacy Policy: from a Terms version's date the transcription
  consent asks again; before it, people who already had the app see `TermsChangeDialog` once per version (fresh
  installs don't), with Terms and Privacy changes in one dialog. To change a document: add its page(s), the date
  (Terms/Privacy also in `LegalDocument.bundledVersions`) and links in the fixed pages' fallback lists, at least 14
  days ahead (the Terms promise that notice). The Privacy Policy promises no lead time. All `docs/` pages share `docs/docs.css` (the look of
  pianoweave.com) and the site header and footer: start a new version from a copy of the previous one. **A new user must never get the change notice** (this bug has come
  back before): "new" means no preferences at all on the first launch, not install time = update time (they differ
  after an update before the first launch, or two installs in a row), and the user stays new until the version
  lists are first fetched (`LEGAL_NEW_USER`). `LegalConsentTest` covers this; run it after touching `LegalConsent`
  or anything that writes preferences before `LegalConsent.onAppStart`. Testing needs no made-up versions: debug builds have Settings → Debug →
  "Preview legal change notice" (each tap a different case), and any page takes `?today=YYYY-MM-DD`.
- **Modes** (`TranscriptionMode` in `api/TranscriptionModels.kt`): Piano (Transkun, request mode `piano`), Ensemble
  (every instrument its own track plus drums on channel index 9, `ensemble`) and, old servers only, Piano arrangement
  (`arrangement` + style `level2`). `offeredModes(arrangement, ensemble)` decides from server-info: `ensemble: true`
  gives Piano + Ensemble; no `ensemble` and `arrangement: true` (an old server) gives Piano + the arrangement; else
  Piano only. Piano sends no `mode` to old servers and `piano` to ones with `ensemble`; no style goes to new servers.
  Transcribe opens `TranscriptionModeDialog` when there are two. Library keys: the source key, plus `#piano-arrangement`
  or `#ensemble` (`libraryKey()`), so a link's piano, arrangement and ensemble songs are three songs and stay working
  links; the mode is read back from the key (resume, cards, library tag).
- **Multi-track MIDI**: `SimpleMidiReader` merges the notes of every track and channel (each `MidiNoteEvent` keeps its
  `channel`); the piano roll has no per-track UI, and everything plays as piano. Drums (channel index 9) are off by
  default and always in release builds (`drumNotesEnabled`): hidden, silent, not waited for. Only debug builds have
  Settings "Drum notes (debug)" (`includeDrumNotes`) to switch them on at once (`withoutDrumsUnless`). Export copies
  the server's file unchanged, drums included (an ensemble song with difficulty levels: one file per level, below).
- **Ensemble difficulty levels** (`midi/MidiVariants.kt`; server status `variants`, download `?variant=`): a job listing
  two or more gets every one downloaded into the song's one `.mid`: the primary (full) as the server sent it, then
  chunks of our own (index `PWvi`, one `PWvr` per other level) that every MIDI reader, older app versions included,
  skips. So the song stays one card, one Drive file and one delete, and old devices see full. Default and raw failing
  retry the download; other levels are tried twice and left out. Piano roll Settings → Difficulty (top, only with
  levels; Raw has an info tooltip) re-parses in place; the choice is line 17 of the song's `.meta`, per song and
  device (`MidiVariants.choose`: stored, else the mode's `defaultOrder`: ensemble full, then the hardest listed, then raw;
  piano raw first). Piano jobs get levels too: their raw is Transkun's own result, shown as "Original" (export
  `<title> - original.mid`) and kept as the primary, so older versions play what the pianist played. Piano jobs
  wait for late levels only on servers whose server-info says `piano_levels: true` (`midi/PianoLevels.kt`, kept by
  URL in prefs so the worker knows too). The server lists only levels that
  differ (a simple song: e.g. full and easy; drums alone: raw), and the menu offers exactly those; one alone is a plain file.
  `variants` is `[]` until completed and always ends with `raw` (piano jobs: `["raw"]`). An ensemble completed with only
  `["raw"]` is read again every 2 s for up to 20 s (`awaitLateLevels`: an older RunPod image's levels come late); a 404
  (`variant_unavailable`) leaves that level out.
- **Link preview** (server `GET api/billing/cost`: `title`, `channel`, `thumbnail`; billed servers, signed in, 450 ms
  after the field rests): no row of its own and nothing in the Transcribe button. A read link's field shows its title
  instead of the URL while unfocused (`ResolvedLinkTransformation`, cut to the field with an ellipsis), the thumbnail
  in the leading icon's place; focus brings the URL back with the cursor at its end. Channel and length are only in
  the screen-reader text. No title: the card is as it was.
- **Trim** (server-info `trimming: true`): the scissors end of the Transcribe button (`TrimSegment`) opens `TrimDialog`, and the chosen part shows under its label; the part (whole seconds,
  end null = to the end) is sent as `start_seconds`/`end_seconds` and only its length is charged. A part's library key
  is its source key plus `#part=<start>-<end>` before any `#piano-arrangement` (`api/Clips.kt`), so parts and the whole
  song are separate songs locally and on Drive (sync matches exact keys). A link's part that overlaps a part (or the
  whole) already in the library in the same mode asks first (`PartOverlapDialog`); uploads never do, as each is its
  own song. `ClipsTest` covers keys, overlaps, requests and the Drive description round trip.
- **In-app updates** (`update/AppUpdates.kt`, Play `app-update`): once per start, after the permission prompts, a
  newer Play version is offered as a flexible update (Restart snackbar when downloaded); priority 4–5 forces the
  immediate flow; a declined version rests 3 days. Debug builds: Settings → Debug → "Simulate app update" runs it
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
- **Developer accounts** (`ui/viewmodel/PianoWeaveViewModelDeveloper.kt`): the server's administrator makes an
  account a developer's (admin web page or `python -m app.admin user <who> developer on`); `GET /api/billing/balance`
  then says `developer: true` and the server never charges its jobs. The app shows "∞ DEV" instead of the credits pill
  (it opens `DeveloperPanelDialog`: `GET /api/developer/server` every 5 s, and the latest job's `developer` details),
  skips its own balance checks, swaps the round logo for the cat (`developer_icon`) and draws `developer_background`
  behind everything, dimmed (`AdaptiveNavigation`'s `backdrop`). Background "Regular" (Settings → Developer, or
  the panel) brings back the regular background, logo and launcher icon together (`wantsDeveloperIcons`). Signing out or
  changing servers clears it with the balance. The launcher icon is the cat too: the manifest's launcher entries
  are aliases of `MainActivity` (`LauncherDefault`, `LauncherDeveloper`, `LauncherLight`), one enabled; `LauncherIcon`
  remembers the wish on a definite answer (balance, sign-out) and swaps them in `onStop`, as a swap can close the app.
  The cat wins over the light icon.
- **Piano roll keys & notes** (Settings → More → Keyboard & notes): which keys are drawn (`KeyboardRange`: All 88
  by default, Song, Follow, Custom; `ui/screens/pianoroll/KeyboardWindow.kt`) and note names on notes and played keys.
  Only the drawing changes: wait mode always listens to all 88 keys. Notes outside the keys shown get edge markers, and
  a strip under the keys shows the whole keyboard. The song map (`SongMap.kt`) sits under Loop in Practice and is also
  switched by holding the loop button, as the metronome by holding the speed button (both say so in their subtitle).
- **Piano roll tutorial**: 4-step dialog (incl. voice commands) on first open (`HAS_SEEN_PIANO_ROLL_TUTORIAL`, account progress); closes only via its buttons.
- **Verse of Light** (hidden): hold the round Piano Weave icon (top bar / rail) for 7 s the first time, 3 s the
  second, 2 s after (`VERSE_OF_LIGHT_OPEN_COUNT`); a normal tap still opens Settings. Shows a gold cross and a
  random verse from `bible_verses.json` with the matching Hebrew name of God, revealed by a shine sweep that copies
  the splash icon's animation (`res/drawable/splash_icon*.xml`). Tapping the name or verse copies it. Its look is
  meant to match the splash screen: palette `#0F0B09` background, `#FFDEB2` glow, ribbon golds (by day the same gold
  cross on a page brightest around it, settling into warm shade at the edges, words in deep gold).
- **Light themes** (hidden): hold the Lamplight tile in any theme picker for 5 s (`IS_LIGHT_THEME_UNLOCKED`, and it switches to
  day at once); then a
  Night / Day toggle shows above the themes (`IS_LIGHT_THEME`). The day themes are a set of their own (`dayThemes` in
  `AppTheme.kt`, 12, one clear colour each; default Radiance), drawn from the design canvas "Piano Weave by Day"
  (claude.ai artifact). Switching keeps the same theme if it exists on the other side, else its partner
  (`dayPartners`, e.g. Night Watch ↔ Morning Watch), else that side's last choice (`SELECTED_THEME_ID` /
  `SELECTED_DAY_THEME_ID`), else that side's default. Style by Scripture: light, not glare and no darkness: warm
  parchment (`NeutralPalette.Sunlit`), cards lifted by light rather than shadow, gold lit from within, no rays or sun
  shapes (the owner rejected them). Key buttons are gold leaf by day (`goldLeaf`, `AppTheme.fill`, ink on it).
  `AppThemeManager.isLight` is snapshot state read by `Moods`, `SupportRose`, `Ink` (use it instead of `Color.White`
  for text and hairlines on surfaces; text on an accent keeps the `isLightAccent` rule) and `lampGlow`. Outside the
  app: `LauncherLight` icon, `Theme.PianoWeave.Light` (window background, and the splash via `setSplashScreenTheme` on
  API 33+), all in the chosen "Warm across, edges": the night drawables' ribbons in warm golds shaded across, a fine
  gold line, the bright edges; the launcher icon on the piano roll's sunlit stage.
- **Account progress** (`util/AccountProgress.kt`): tutorials done, the light unlock and the verse count live in their
  own prefs file (`account_progress`), so Android Auto Backup keeps them with the Google account (the backup rules
  in `res/xml/` keep only that file and the MIDI library: settings stay per device); with cloud sync on,
  they are also merged with `piano_weave_progress.json` in the Drive app folder (`cloud/AccountProgressSync.kt`,
  after each library sync and on each change; later change wins, counts only grow unless reset later than the other side's last change). Only unlocks and things done belong
  there, never choices (theme, Night / Day stay per device). `listCloudMidis` skips that
  file, but **app versions before 55 list it as a song**. Debug builds: Settings → Debug → "Ignore account progress"
  uses an empty stand-in file that never syncs (a newcomer's view); off brings the real progress back.
- **Debug section** (Settings, debug builds only, heading included: `DebugSettingsSection`): every testing switch lives
  there; "Official server" swaps the official server for a dev server (address and name typed there, optional defaults
  `DEV_API_BASE_URL` / `DEV_SERVER_NAME` in config.txt) through `AppConfig.applyDebugServer`, which every user of the
  official server reads; release builds ignore it. Also e.g. "Learn header pill" forces the Learn header to the credits or the support pill on any server. "Reset light themes" / "Reset Verse of
  Light" make each hidden feature unfound again, on the account too.
