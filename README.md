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
  <a href="#%EF%B8%8F-setup">Setup</a> •
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
    <img src="https://img.shields.io/badge/Discord-Join_the_community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Piano Weave Discord" />
  </a>
  <a href="https://ko-fi.com/Y3G527Q9PN">
    <img src="[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Y3G527Q9PN)" />
  </a>
  <a href="https://www.patreon.com/cw/ChristopherOlszak/membership">
    <img src="https://img.shields.io/badge/Patreon-Support_Piano_Weave-F96854?style=for-the-badge&logo=patreon&logoColor=white" alt="Support Piano Weave on Patreon" />
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
