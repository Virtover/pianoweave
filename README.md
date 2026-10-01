<p align="center">
  <img src="docs/screenshots/app_icon.png" width="128" alt="Piano Weave Icon" />
</p>

<h1 align="center">Piano Weave</h1>

<p align="center">
  <b>Upgrade your piano learning experience with high-fidelity practice tools.</b><br>
  Piano Weave transcribes piano performances from online videos into interactive practice sessions with an ultra-pro piano roll and a high-quality Grand Piano audio engine.
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-community--support">Community & Support</a> •
  <a href="#-screenshots">Screenshots</a> •
  <a href="#-setup">Setup</a> •
  <a href="#-key-modules">Key Modules</a> •
  <a href="#license">License</a>
</p>

---

## 📱 Screenshots

|                                            AI Transcription                                           |                                          Piano Roll & Wait Mode                                          |
| :---------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------------------: |
| <img src="docs/screenshots/transcription_landscape.jpg" width="100%" alt="AI Transcription Screen" /> | <img src="docs/screenshots/piano_roll_landscape.jpg" width="100%" alt="Interactive Piano Roll Screen" /> |

## ✨ Features

* **AI Transcription:** Uses a FastAPI backend to transcribe piano performances from online video URLs (e.g. YouTube) into MIDI data.
* **Ultra-Pro Piano Roll:** Practice with a high-fidelity interactive roll featuring A/B looping, speed control, and transposition.
* **Grand Piano Sound:** Powered by FluidSynth and high-quality SoundFonts for a rich, realistic acoustic piano experience.
* **Adaptive UI:** Optimized for both landscape (tablet/practice mode) and portrait (browsing mode) orientations.
* **MIDI Integration:** Support for hardware MIDI input (USB/Bluetooth) and touch-simulated piano keys.
* **Wait Mode:** Acoustic note detection that pauses playback until you strike the correct notes.

## 💬 Community & Support

<p align="center">
  <a href="https://discord.gg/vzMyRZewmb">
    <img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
  </a>
  <a href="https://ko-fi.com/Y3G527Q9PN">
    <img src="https://img.shields.io/badge/Ko--fi-29ABE0?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi" />
  </a>
  <a href="https://www.patreon.com/cw/ChristopherOlszak/membership">
    <img src="https://img.shields.io/badge/Patreon-FF424D?style=for-the-badge&logo=patreon&logoColor=white" alt="Patreon" />
  </a>
</p>

## 🛠️ Setup

### Transcription server

This app requires the [Piano Transcription Server](https://github.com/Virtover/piano-transcription-server) running locally or on a server.

### Configuration

1. Copy `app/src/main/assets/config/config.example.txt` to `app/src/main/assets/config/config.txt`.
2. Set your `DEFAULT_API_BASE_URL` in the config file (e.g., `http://10.0.2.2:8000/` for a local emulator).

### Build

Open the project in Android Studio and build the `:app` module.

For a debug build from the command line:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew assembleDebug
```

### Release build

Release builds are signed with a dedicated upload keystore for Google Play.

Create a local `keystore.properties` file in the project root:

```properties
storeFile=C:/Users/YourName/.android/pianoweave-upload.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=pianoweave
keyPassword=YOUR_KEY_PASSWORD
```

The keystore itself should be stored outside the repository. Keep both the keystore and its passwords secure and backed up.

`keystore.properties` and keystore files must not be committed to Git. Add them to `.gitignore`:

```gitignore
keystore.properties
*.jks
*.keystore
```

The release build uses the `release` signing configuration defined in `app/build.gradle.kts`.

To generate a signed Android App Bundle for Google Play:

```bash
./gradlew bundleRelease
```

On Windows:

```powershell
.\gradlew bundleRelease
```

The resulting bundle is located at:

```text
app/build/outputs/bundle/release/app-release.aab
```

Keep the upload keystore safe. It is required for future release uploads to Google Play.

## 🚀 Key Modules

* **`audio`:** Real-time FluidSynth integration for grand piano synthesis.
* **`midi`:** Binary MIDI parsing and hardware input management.
* **`api`:** Retrofit-based communication with the transcription backend.
* **`ui`:** Modern Jetpack Compose implementation of the practicing dashboard.

## License

Piano Weave is licensed under the [PolyForm Noncommercial License](LICENSE).

Copyright (c) 2026 Krzysztof Olszak

The software may be used, modified, and distributed for noncommercial purposes subject to the terms of the license.

Commercial use is not permitted without prior permission from the copyright holder. For the full terms, see the LICENSE file.
