package com.lumenchord.pianoweave.audio

import java.nio.ByteBuffer

object NativeAudioEngine {

    init {
        System.loadLibrary("native-audio-bridge")
    }

    @JvmStatic
    external fun initialize(): Boolean

    @JvmStatic
    external fun startCapture(): Boolean

    @JvmStatic
    external fun stopCapture()

    @JvmStatic
    external fun getSampleRate(): Int

    @JvmStatic
    external fun getAvailableFrames(): Long

    @JvmStatic
    external fun copyLatest(
        destination: ByteBuffer,
        frames: Int,
        startIndex: Long,
    ): Int

    /**
     * Voice mode opens the mic with the device's echo canceller (for voice commands alone), otherwise
     * unprocessed (for note detection). True if the open stream must be reopened to change.
     */
    @JvmStatic
    external fun setVoiceMode(voice: Boolean): Boolean

    /** The AAudio input preset voice mode opens with. True if the open stream must be reopened to change. */
    @JvmStatic
    external fun setVoicePreset(preset: Int): Boolean

    @JvmStatic
    external fun cleanup()
}
