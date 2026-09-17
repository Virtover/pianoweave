# YT Piano 🎹

Upgrade your piano learning experience with high-fidelity practice tools. This app transcribes piano performances from online videos into interactive practice sessions with a professional piano roll and a high-quality Grand Piano audio engine.

YT Piano is an independent open-source project and is not affiliated with or endorsed by YouTube or Google.

## ✨ Features

- **AI Transcription:** Integrated with a FastAPI backend to transcribe piano performances into performance data.
- **Ultra-Pro Piano Roll:** Practice with a high-fidelity interactive roll featuring A/B looping, speed control, and transposition.
- **Grand Piano Sound:** Powered by FluidSynth and high-quality SoundFonts for a rich, realistic acoustic piano experience.
- **Adaptive UI:** Optimized for both landscape (tablet/practice mode) and portrait (browsing mode) orientations.
- **MIDI Integration:** Support for hardware MIDI input (USB/Bluetooth) and touch-simulated piano keys.
- **Wait Mode:** Intelligent onset detection that pauses playback until you strike the correct notes.

## 🛠️ Setup

### Transcription server
This app requires the [YT Piano To Midi Server](https://github.com/example/yt-piano-to-midi-server) running locally or on a server.

### Configuration
1.  Copy `app/src/main/assets/config.example.txt` to `app/src/main/assets/config/config.txt`.
2.  Set your `API_BASE_URL` in the config file (e.g., `http://10.0.2.2:8000/` for local emulator).

### Build
Open the project in Android Studio and build the `:app` module.

## 🚀 Key Modules

- **`audio`:** Real-time FluidSynth integration for grand piano synthesis.
- **`midi`:** Binary MIDI parsing and hardware input management.
- **`api`:** Retrofit-based communication with the transcription backend.
- **`ui`:** Modern Jetpack Compose implementation of the practicing dashboard.

## License

This project is licensed under the MIT License.