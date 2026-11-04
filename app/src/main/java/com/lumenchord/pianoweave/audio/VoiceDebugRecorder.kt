package com.lumenchord.pianoweave.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Debug builds only: records what voice commands listen to, to find commands that were said but
 * never reached the recognizer (the voice gate didn't rate them as speech, which leaves no other
 * trace). Per session, in files/voice-debug/: `<time>.pcm` (the 16 kHz mic stream the gate gets,
 * 16-bit mono) and `<time>.prob` (the gate's voice probability per 32 ms chunk, float32), so the
 * session can be replayed through the pipeline off the device. Kept on the device only, at most
 * [MAX_MINUTES] per session and the last [KEEP_SESSIONS] sessions.
 */
internal object VoiceDebugRecorder {
    private const val TAG = "VoiceDebugRecorder"
    private const val MAX_MINUTES = 30
    private const val KEEP_SESSIONS = 6
    private const val CHUNK = 512
    private const val MAX_CHUNKS = MAX_MINUTES * 60 * 16000 / CHUNK

    // File writes off the audio thread
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "VoiceDebugRecorder").apply { isDaemon = true } }

    private var pcm: BufferedOutputStream? = null
    private var prob: BufferedOutputStream? = null

    /** Chunks recorded this session; touched on the audio thread. */
    @Volatile
    var chunks = 0
        private set

    @Volatile
    private var recording = false

    fun start(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val dir = File(context.filesDir, "voice-debug")
        chunks = 0
        recording = true
        writer.execute {
            closeFiles()
            try {
                dir.mkdirs()
                dir.listFiles { f -> f.name.endsWith(".pcm") }?.sortedBy { it.name }?.dropLast(KEEP_SESSIONS - 1)?.forEach {
                    it.delete()
                    File(dir, it.name.removeSuffix(".pcm") + ".prob").delete()
                }
                // The mic processing in the name, to compare sessions recorded each way
                val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
                    "-" + AcousticNoteDetector.voiceMicMode.name.lowercase()
                pcm = BufferedOutputStream(FileOutputStream(File(dir, "$name.pcm")), 1 shl 16)
                prob = BufferedOutputStream(FileOutputStream(File(dir, "$name.prob")), 1 shl 12)
                Log.i(TAG, "Recording voice session $name")
            } catch (e: Exception) {
                Log.w(TAG, "Can't record", e)
                recording = false
            }
        }
    }

    /** One 32 ms chunk and its voice probability (audio thread). */
    fun write(chunk: FloatArray, probability: Float) {
        if (!recording) return
        if (chunks >= MAX_CHUNKS) {
            stop()
            return
        }
        chunks++
        val bytes = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in chunk) bytes.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val p = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(probability)
        writer.execute {
            try {
                pcm?.write(bytes.array())
                prob?.write(p.array())
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (!recording) return
        recording = false
        writer.execute { closeFiles() }
    }

    private fun closeFiles() {
        try { pcm?.close() } catch (_: Exception) {}
        try { prob?.close() } catch (_: Exception) {}
        pcm = null
        prob = null
    }
}
