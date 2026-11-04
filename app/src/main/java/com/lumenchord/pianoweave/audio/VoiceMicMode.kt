package com.lumenchord.pianoweave.audio

/**
 * How the phone processes the mic while only voice commands listen (note detection always needs it
 * unprocessed). [CALL] is what calls use: the echo canceller takes the app's own song out, but the noise
 * suppression also took much of a voice from across the room along with the piano (phone recordings at
 * -70 dBFS). [SPEECH] is the processing meant for speech recognition, lighter on many phones. [RAW] is
 * none at all: the app's own noise removal ([SpeechEnhancer]) only. A debug setting, to compare them.
 * [preset] is the AAudio input preset.
 */
enum class VoiceMicMode(val preset: Int) { CALL(7), SPEECH(6), RAW(9) }
