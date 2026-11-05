# Voice commands: design notes

Loaded only when working in `audio/`. UI side: `ui/screens/pianoroll/VoiceCommand*.kt`,
`pianoroll/settings/VoiceCommandsSection.kt`, `ui/viewmodel/PianoWeaveViewModelVoice.kt`.

Piano roll, off by default: one-word commands, no wake word.

## Pipeline

- **Shared mic.** `AcousticNoteDetector` owns the capture: note detection (`start`/`stop`) and voice
  (`startVoice`/`stopVoice`) each want it; the note model only runs for note detection.
- **VAD path.** `UtteranceSegmenter` keeps only short utterances (≤ ~2.5 s) with pauses around them, from Silero
  VAD chunks; Vosk (`VoiceCommandRecognizer`, grammar = `VoiceCommand` phrases) decodes only those. Several
  commands in a row are one action ("full screen wait").
- **Speech enhancement.** Silero is trained to ignore music and rated commands over a piano 0.00 for minutes
  (phone recording), so the voice stream is first cleaned by DTLN (`SpeechEnhancer`, assets `dtln_*.tflite`,
  ~4 MB, MIT). Silero rates the cleaned audio, but Vosk decodes the raw audio (cleaned audio lost short words
  like "play"). On two phone recordings this caught 52 and 52 commands where the raw path caught 21 and 42.
  Speech starts at 0.15, raised over a steady background.
- **Stream path.** Even cleaned, Silero rated a voice from across the room over the piano 0.00, so Vosk also hears
  the whole raw stream (`CommandStream`); `CommandListener` runs both on one timeline and counts each stretch
  once (whichever gives commands first). The stream alone needs every word at 0.9 (no pause rule, and its unsure
  readings over the piano were mostly wrong). Stream-only (no VAD path) felt worse to the owner: slower
  (~0.6 s vs ~0.3 s) and noisier.
- **Voice gate.** A second Silero stream on level-boosted, cleaned audio (+30 dB max) for commands; the song is
  ducked 9 dB while a command may be spoken (`PianoPlayer.duckForVoice`).
- **Model** downloads to `files/voice/` (`VoiceModelStore`).

## Mic presets

Voice-only capture opens with VOICE_COMMUNICATION (device echo canceller removes the app's own song, ~25 dB
measured); note detection forces UNPROCESSED, so the stream reopens when wait mode toggles. Debug builds can
switch the preset in voice settings (`VoiceMicMode`: Call / Speech = VOICE_RECOGNITION / Raw). On the owner's
phone Call pulls piano and voice ~25 dB down and the VAD path still works; with Speech or Raw the piano stays loud
and mostly the stream catches commands. No clear winner; Call stays default.

## Accepting a command

- Any non-command word rejects the whole utterance. Optional "Selah" first; Settings can require it
  (`VoiceListeningMode.SELAH`, which also drops the pause-before rule).
- `VoiceCommandDecoder` decodes twice (word confidences + next-best readings): a command counts at confidence
  0.7, or from 0.5 if no other reading is a different command; a word under 0.9 that another reading has as a
  different known word drops the phrase ("stop 0.75 faster" / "start faster").
- "undo" takes back within 4 s.
- Left out because they sound alike to the model: "mark" (heard as "back"), "skip" (heard for "slower"), "no"
  for undo ("no" is a distractor word). "go" alone isn't play: "go forward" with its last word drowned out by the
  piano came out as "go". A sure "loop" is dropped when another reading is "loop start"/"loop end".
- Debug builds show a chip with what a near-miss utterance sounded like (never for noise, or in Selah mode
  unless addressed).

## Tests and debugging

- `VoiceCommandInstrumentedTest`: TTS words (every phrase x 2 voices: clean, -30 dB, room noise; in voice-loud
  noise a miss is allowed, a wrong command never); downloads the model if missing.
- `VoiceOverPlayingInstrumentedTest`: speaks every phrase over practice playing rendered with the app's SoundFont
  (acoustic piano the app can't duck), +10 to -5 dB.
- Debug builds record what voice commands hear (`VoiceDebugRecorder`,
  `files/voice-debug/<time>-<mic mode>.pcm`, raw 16 kHz PCM). Copy a session to `voice-debug/replay.pcm` on the
  emulator and run `ReplayVoiceSessionTest` to see what was said (full-vocabulary Vosk) next to what voice
  commands made of it. Read its output with `scripts/logcat.sh ReplayVoice`, not raw logcat.
