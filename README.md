<p align="center">
  <img src="screenshots/app_icon.png" width="112" alt="Piano Weave icon" />
</p>

<h1 align="center">Piano Weave</h1>

<p align="center">
  <b>Learn piano from the songs you love.</b><br>
  Piano Weave turns the piano in online videos into MIDI and lets you practice it on an interactive falling-notes piano roll.
</p>

<p align="center">
  <a href="https://discord.gg/vzMyRZewmb"><img src="https://img.shields.io/badge/Join%20the%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Discord" height="36" /></a>
  &nbsp;
  <a href="https://ko-fi.com/Y3G527Q9PN"><img src="https://img.shields.io/badge/Support%20on%20Ko--fi-29ABE0?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support on Ko-fi" height="36" /></a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#build-from-source">Build from source</a> •
  <a href="#project-structure">Project structure</a> •
  <a href="#license">License</a>
</p>

## Features

* **AI transcription:** Turns the piano part of a video link (e.g. YouTube), an audio/video file or a microphone recording into MIDI. Vocals and other instruments are left out, so you get the piano part, not a full arrangement. Piano covers give the best results.
* **Interactive piano roll:** Falling notes with A/B looping, speed control and transposition.
* **Wait mode:** Pauses at each note until you play it. It listens to your acoustic piano through the microphone, or to a MIDI keyboard, with the rest of the song playing along.
* **MIDI keyboards:** USB and virtual MIDI keyboards connect automatically and play through the app's piano sound, which can be turned off for keyboards with their own speakers.
* **Grand piano sound:** FluidSynth with a bundled SoundFont for a rich, realistic piano.
* **Cloud sync:** Back up and sync your song library with Google Drive.
* **Made for any screen:** Portrait and landscape layouts, and many color themes.

## Screenshots

<table>
  <tr>
    <td colspan="2" align="center">
      <img src="screenshots/landscape_01_hero.png" width="100%" alt="Piano Weave" /><br>
      <sub><b>Piano Weave</b></sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="screenshots/landscape_02_wait_mode.png" width="100%" alt="Wait mode" /><br>
      <sub><b>Wait mode</b></sub>
    </td>
    <td width="50%" align="center">
      <img src="screenshots/landscape_03_transcribe.png" width="100%" alt="AI transcription" /><br>
      <sub><b>AI transcription</b></sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="screenshots/landscape_04_practice.png" width="100%" alt="Practice" /><br>
      <sub><b>Practice</b></sub>
    </td>
    <td width="50%" align="center">
      <img src="screenshots/landscape_05_library.png" width="100%" alt="MIDI library" /><br>
      <sub><b>MIDI library</b></sub>
    </td>
  </tr>
</table>

## Build from source

### Requirements

* Android Studio (latest stable) or the Android command-line tools
* JDK 17+
* Android SDK Platform 37
* Android NDK `30.0.16248370` (pinned in `app/build.gradle.kts`)
* CMake `3.22.1`

Android Studio installs the SDK, NDK and CMake on demand. From the command line (Windows):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-37" "ndk;30.0.16248370" "cmake;3.22.1"
```

### Transcription server

Transcription runs on the [Piano Transcription Server](https://github.com/Virtover/piano-transcription-server), hosted locally or on a server. It is chosen in the app's server settings.

<p align="center">
  <img src="screenshots/landscape_06_server.png" width="70%" alt="Server settings" /><br>
  <sub><b>Server settings</b></sub>
</p>

The official server is the only server that ever receives the user's Google token. Custom servers chosen in the
app are always used anonymously and without in-app purchases.

### Configuration

1. Copy `app/src/main/assets/config/config.example.txt` to `app/src/main/assets/config/config.txt`.
2. Set your `DEFAULT_API_BASE_URL` in the config file (e.g., `http://10.0.2.2:8000/` for a local emulator).
3. Set `WEB_CLIENT_ID` to your Google OAuth "Web application" client ID, and optionally `DISCORD_URL` to show a Discord button in app settings.

### Build

Open the project in Android Studio and build the `:app` module, or build a debug APK from the command line:

```bash
./gradlew assembleDebug      # Windows: .\gradlew assembleDebug
```

Debug builds do not need a keystore. They are signed with the default Android debug key.

All dependencies resolve from Maven Central, Google Maven, and the bundled `local-maven/` folder (see [Third-party software](#third-party-software)). No extra steps are required after cloning.

<details>
<summary><b>Release build</b></summary>

<br>

Release builds are signed with a dedicated upload keystore for Google Play. If `keystore.properties` is missing (for example, on a fresh clone), release signing is skipped and only debug builds are available.

Create a local `keystore.properties` file in the project root:

```properties
storeFile=C:/Users/YourName/.android/pianoweave-upload.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=pianoweave
keyPassword=YOUR_KEY_PASSWORD
```

The keystore itself should be stored outside the repository. Keep both the keystore and its passwords secure and backed up.

The release build uses the `release` signing configuration defined in `app/build.gradle.kts`.

To generate a signed Android App Bundle for Google Play:

```bash
./gradlew bundleRelease      # Windows: .\gradlew bundleRelease
```

The resulting bundle is located at `app/build/outputs/bundle/release/app-release.aab`.

Keep the upload keystore safe. It is required for future release uploads to Google Play.

</details>

<details>
<summary><b>Account deletion page</b></summary>

<br>

`docs/delete_account.html` (served by GitHub Pages) lets users delete their server account and their Drive cloud library.
The production server URL and the Web client ID are set in `docs/config.js`.
Keep them in sync with `DEFAULT_API_BASE_URL` and `WEB_CLIENT_ID` in a release `config.txt`.

For the page to work:

* the Web client must list `https://virtover.github.io` under **Authorized JavaScript origins** and be in the same
  Google Cloud project as the Android client (Drive's app folder is per project);
* its client ID must be in the server's `GOOGLE_OAUTH_CLIENT_IDS`;
* the server must allow CORS requests from `https://virtover.github.io` (`DELETE /api/account` with an `Authorization` header).

</details>

## Project structure

* **`audio`:** Real-time FluidSynth integration for grand piano synthesis, plus microphone note detection for wait mode.
* **`midi`:** Binary MIDI parsing, the song library and hardware input management.
* **`api`:** Retrofit-based communication with the transcription backend.
* **`auth`, `billing`, `cloud`:** Google sign-in, Play Billing credits and Google Drive sync.
* **`ui`:** Jetpack Compose screens, components and themes.

## License

Piano Weave is licensed under the [PolyForm Noncommercial License](LICENSE).

Copyright (c) 2026 Krzysztof Olszak

The software may be used, modified, and distributed for noncommercial purposes subject to the terms of the license.

Commercial use is not permitted without prior permission from the copyright holder. For the full terms, see the LICENSE file.

### Third-party software

* **[FluidSynth](https://github.com/FluidSynth/fluidsynth)** is licensed under LGPL-2.1 and is dynamically linked.
* **[fluidsynth-kmp](https://github.com/kotlinds/fluidsynth-kmp)** (Kotlin wrapper) is licensed under Apache-2.0. The modified build in `local-maven/` is built from [this fork](https://github.com/Virtover/fluidsynth-kmp/tree/fix-16kb-alignment), and the changes are limited to linker flags and the NDK version.
* The bundled SoundFont and ML model have their own licenses. See the files in `app/src/main/assets/`.
