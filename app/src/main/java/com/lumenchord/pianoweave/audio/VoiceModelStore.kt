package com.lumenchord.pianoweave.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Why the voice model couldn't be downloaded, so the app can say what to do about it. */
enum class VoiceModelError { NO_CONNECTION, NO_SPACE, SERVER }

class VoiceModelDownloadException(val reason: VoiceModelError, message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * The speech model for voice commands (Vosk small English, Apache-2.0). It is ~40 MB, so it isn't
 * bundled: it's downloaded the first time voice commands are turned on and kept in the app's files.
 *
 * A new model needs a new [MODEL_NAME] (a new file on the server, never one replaced in place) and
 * [MODEL_SHA256]; the decoder's thresholds are tuned to this exact model, so retest voice commands.
 * Older models' folders are removed by [deleteOldModels].
 */
object VoiceModelStore {
    private const val TAG = "VoiceModelStore"
    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    // Our own copy first; Vosk's site (the original) if it can't be reached. Both serve the same zip.
    private val MODEL_URLS = listOf(
        "https://assets.pianoweave.com/voice/$MODEL_NAME.zip",
        "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    )
    private const val MODEL_SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
    private const val DOWNLOAD_BYTES = 41_205_931L
    const val DOWNLOAD_SIZE_MB = 40
    // Unpacked it is ~71 MB; the zip is streamed, never stored, so this leaves a little room
    const val REQUIRED_FREE_MB = 90
    // Written last, so a download cut off half way never counts as installed
    private const val COMPLETE_MARKER = ".complete"
    private const val STAGING_SUFFIX = ".partial"

    private fun voiceDir(context: Context) = File(context.filesDir, "voice")

    fun modelDir(context: Context) = File(voiceDir(context), MODEL_NAME)

    fun isInstalled(context: Context) = File(modelDir(context), COMPLETE_MARKER).exists()

    /** Deletes models from earlier app versions. Slow (tens of MB): call off the main thread. */
    fun deleteOldModels(context: Context) {
        val keep = setOf(MODEL_NAME, MODEL_NAME + STAGING_SUFFIX)
        voiceDir(context).listFiles()?.filter { it.name !in keep }?.forEach {
            Log.i(TAG, "Deleting old voice model ${it.name}")
            it.deleteRecursively()
        }
    }

    /**
     * Downloads, checks and unpacks the model, from each of [MODEL_URLS] in turn until one works;
     * [onProgress] gets 0..1 (starting over for a fallback). Throws [VoiceModelDownloadException].
     */
    suspend fun download(context: Context, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        deleteOldModels(context)
        val dir = voiceDir(context).apply { mkdirs() }
        val staging = File(dir, MODEL_NAME + STAGING_SUFFIX)
        staging.deleteRecursively()
        if (dir.usableSpace < REQUIRED_FREE_MB * 1_000_000L) {
            throw VoiceModelDownloadException(VoiceModelError.NO_SPACE, "Not enough free space for the voice model")
        }

        var lastError: VoiceModelDownloadException? = null
        var serverAnswered = false
        for (url in MODEL_URLS) {
            onProgress(0f)
            try {
                downloadFrom(url, staging, onProgress)
                File(staging, COMPLETE_MARKER).createNewFile()
                val target = modelDir(context)
                target.deleteRecursively()
                if (!staging.renameTo(target)) throw IOException("Couldn't install the voice model")
                return@withContext
            } catch (e: CancellationException) {
                staging.deleteRecursively()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Model download from $url failed", e)
                // Checked before the unpacked files are deleted, while they still take up the space
                val outOfSpace = dir.usableSpace < 8_000_000L
                staging.deleteRecursively()
                if (outOfSpace) throw VoiceModelDownloadException(VoiceModelError.NO_SPACE, "Ran out of space", e)
                val error = e as? VoiceModelDownloadException
                    ?: VoiceModelDownloadException(VoiceModelError.NO_CONNECTION, e.message ?: "Network error", e)
                if (error.reason == VoiceModelError.SERVER) serverAnswered = true
                lastError = error
            }
        }
        // Being offline explains it only if no server answered at all
        val reason = if (serverAnswered) VoiceModelError.SERVER else VoiceModelError.NO_CONNECTION
        throw VoiceModelDownloadException(reason, lastError?.message ?: "Download failed", lastError)
    }

    private suspend fun downloadFrom(url: String, staging: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        staging.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw VoiceModelDownloadException(VoiceModelError.SERVER, "HTTP $code from $url")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: DOWNLOAD_BYTES
            val digest = MessageDigest.getInstance("SHA-256")
            // Hashed as it arrives, so the zip never has to be stored
            val raw = DigestInputStream(
                CountingInputStream(connection.inputStream.buffered()) { read ->
                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                },
                digest
            )
            ZipInputStream(raw).use { zip ->
                val stagingPath = staging.canonicalPath + File.separator
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    // Entries sit in one top folder named like the model; drop it
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue
                    val out = File(staging, relative)
                    if (!out.canonicalPath.startsWith(stagingPath)) {
                        throw VoiceModelDownloadException(VoiceModelError.SERVER, "Bad entry in model archive")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
                // ZipInputStream stops before the archive's closing directory; the hash needs it too
                val buffer = ByteArray(64 * 1024)
                while (raw.read(buffer) >= 0) ensureActive()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != MODEL_SHA256) {
                throw VoiceModelDownloadException(VoiceModelError.SERVER, "Model from $url failed its check ($hash)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private class CountingInputStream(
        source: InputStream,
        private val onRead: (Long) -> Unit
    ) : FilterInputStream(source) {
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
