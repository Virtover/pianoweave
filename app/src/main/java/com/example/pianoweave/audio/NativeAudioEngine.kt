package com.example.pianoweave.audio

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
    external fun getAvailableFrames(): Int

    @JvmStatic
    external fun copyLatest(
        destination: ByteBuffer,
        frames: Int,
    ): Int

    @JvmStatic
    external fun cleanup()
}
