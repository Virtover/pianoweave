package com.lumenchord.pianoweave.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * The speech model for voice commands (Vosk small English, Apache-2.0). It is ~40 MB, so it isn't
 * bundled: it's downloaded the first time voice commands are turned on and kept in the app's files.
 */
object VoiceModelStore {
    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    const val DOWNLOAD_SIZE_MB = 40
    // Written last, so a download cut off half way never counts as installed
    private const val COMPLETE_MARKER = ".complete"

    private fun voiceDir(context: Context) = File(context.filesDir, "voice")

    fun modelDir(context: Context) = File(voiceDir(context), MODEL_NAME)

    fun isInstalled(context: Context) = File(modelDir(context), COMPLETE_MARKER).exists()

    /** Downloads and unpacks the model; [onProgress] gets 0..1. Throws on failure (and cleans up). */
    suspend fun download(context: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val target = modelDir(context)
        val staging = File(voiceDir(context), "$MODEL_NAME.partial")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Model download failed (HTTP ${connection.responseCode})")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: (DOWNLOAD_SIZE_MB * 1_000_000L)
                val counting = CountingInputStream(connection.inputStream.buffered()) { read ->
                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
                ZipInputStream(counting).use { zip ->
                    val stagingPath = staging.canonicalPath + File.separator
                    while (true) {
                        ensureActive()
                        val entry = zip.nextEntry ?: break
                        // Entries sit in one top folder named like the model; drop it
                        val relative = entry.name.substringAfter('/', "")
                        if (relative.isEmpty()) continue
                        val out = File(staging, relative)
                        if (!out.canonicalPath.startsWith(stagingPath)) throw IOException("Bad entry in model archive")
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            File(staging, COMPLETE_MARKER).createNewFile()
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("Couldn't install the voice model")
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    private class CountingInputStream(
        private val source: java.io.InputStream,
        private val onRead: (Long) -> Unit
    ) : java.io.FilterInputStream(source) {
        private var count = 0L
        private var lastReported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        private fun advance(n: Long) {
            count += n
            // Report every ~256 KB, not every buffer
            if (count - lastReported >= 262_144) {
                lastReported = count
                onRead(count)
            }
        }
    }
}
