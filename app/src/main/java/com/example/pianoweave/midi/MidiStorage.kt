package com.example.pianoweave.midi

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.pianoweave.api.VideoMetadata
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest

data class StoredMidi(
    val file: File,
    val videoUrl: String,
    val metadata: VideoMetadata
)

object MidiStorage {

    private const val DIRECTORY = "midi"
    private const val METADATA_EXTENSION = ".meta"

    private fun directory(context: Context): File {
        return File(
            context.filesDir,
            DIRECTORY
        ).also {
            it.mkdirs()
        }
    }

    private fun idForUrl(url: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(url.trim().toByteArray())

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }

    fun find(
        context: Context,
        videoUrl: String
    ): File? {
        val id = idForUrl(videoUrl)

        val midiFile = File(
            directory(context),
            "$id.mid"
        )

        return midiFile.takeIf {
            it.exists() && it.length() > 0
        }
    }

    fun save(
        context: Context,
        videoUrl: String,
        metadata: VideoMetadata?,
        body: ResponseBody
    ): StoredMidi {
        val id = idForUrl(videoUrl)
        val dir = directory(context)
        val midiFile = File(dir, "$id.mid")
        val metadataFile = File(dir, "$id$METADATA_EXTENSION")

        body.byteStream().use { input ->
            midiFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val stableUrl = videoUrl.trim()
        val stableMetadata = metadata ?: VideoMetadata(
            title = "Piano Performance",
            author = "Unknown",
            channel = "Unknown",
            channel_id = "",
            channel_url = "",
            upload_date = "",
            duration = 0f,
            thumbnail = "",
            webpage_url = stableUrl,
            view_count = 0L,
            like_count = 0L
        )

        metadataFile.writeText(
            listOf(
                stableUrl,
                stableMetadata.title,
                stableMetadata.author,
                stableMetadata.channel,
                stableMetadata.channel_id,
                stableMetadata.channel_url,
                stableMetadata.upload_date,
                stableMetadata.duration.toString(),
                stableMetadata.thumbnail,
                stableMetadata.webpage_url,
                stableMetadata.view_count.toString(),
                stableMetadata.like_count.toString()
            ).joinToString("\n")
        )

        return StoredMidi(
            file = midiFile,
            videoUrl = stableUrl,
            metadata = stableMetadata
        )
    }

    fun list(context: Context): List<StoredMidi> {
        val dir = directory(context)

        return dir
            .listFiles()
            ?.filter {
                it.extension == "mid" &&
                        it.exists() &&
                        it.length() > 0
            }
            ?.mapNotNull { midiFile ->
                val metadataFile = File(
                    dir,
                    midiFile.nameWithoutExtension +
                            METADATA_EXTENSION
                )

                if (!metadataFile.exists()) return@mapNotNull null

                val lines = metadataFile.readLines()
                if (lines.isEmpty()) return@mapNotNull null

                val originalUrl = lines[0]
                val metadata = readMetadata(metadataFile, midiFile)

                StoredMidi(
                    file = midiFile,
                    videoUrl = originalUrl, // Fixed: Use the URL used for caching
                    metadata = metadata
                )
            }
            ?.sortedByDescending {
                it.file.lastModified()
            }
            ?: emptyList()
    }

    private fun readMetadata(
        metadataFile: File,
        midiFile: File
    ): VideoMetadata {
        if (!metadataFile.exists()) {
            return fallbackMetadata()
        }

        val lines = metadataFile.readLines()

        if (lines.size < 12) {
            return fallbackMetadata(
                videoUrl = lines.firstOrNull()?.trim().orEmpty().ifBlank { "Unknown source" }
            )
        }

        return try {
            VideoMetadata(
                title = lines[1],
                author = lines[2],
                channel = lines[3],
                channel_id = lines[4],
                channel_url = lines[5],
                upload_date = lines[6],
                duration = lines[7].toFloat(),
                thumbnail = lines[8],
                webpage_url = lines[9],
                view_count = lines[10].toLong(),
                like_count = lines[11].toLong()
            )
        } catch (e: Exception) {
            fallbackMetadata(
                videoUrl = lines.firstOrNull()?.trim().orEmpty().ifBlank { "Unknown source" }
            )
        }
    }

    private fun fallbackMetadata(
        videoUrl: String = "Unknown source"
    ): VideoMetadata {
        return VideoMetadata(
            title = "Piano Performance",
            author = "Unknown",
            channel = "Unknown",
            channel_id = "",
            channel_url = "",
            upload_date = "",
            duration = 0f,
            thumbnail = "",
            webpage_url = videoUrl,
            view_count = 0L,
            like_count = 0L
        )
    }

    fun delete(
        context: Context,
        storedMidi: StoredMidi
    ) {
        storedMidi.file.delete()

        File(
            directory(context),
            storedMidi.file.nameWithoutExtension +
                    METADATA_EXTENSION
        ).delete()
    }

    fun importFile(
        context: Context,
        uri: Uri
    ): StoredMidi {
        val fileName = getOriginalFileName(context, uri)

        // Read and verify the "MThd" magic header bytes (0x4D, 0x54, 0x68, 0x64)
        val headerBytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(4)
                val bytesRead = input.read(buffer)
                if (bytesRead < 4) ByteArray(0) else buffer
            } ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }

        val isValidMidiHeader = headerBytes.size == 4 &&
                headerBytes[0] == 'M'.code.toByte() &&
                headerBytes[1] == 'T'.code.toByte() &&
                headerBytes[2] == 'h'.code.toByte() &&
                headerBytes[3] == 'd'.code.toByte()

        if (!isValidMidiHeader) {
            throw IllegalArgumentException("Selected file '$fileName' is not a valid MIDI track (.mid / .midi).")
        }

        val title = fileName.substringBeforeLast('.')
        val id = idForUrl(uri.toString() + "_" + System.currentTimeMillis())
        val dir = directory(context)
        val midiFile = File(dir, "$id.mid")
        val metadataFile = File(dir, "$id$METADATA_EXTENSION")

        context.contentResolver.openInputStream(uri)?.use { input ->
            midiFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val stableUrl = "file://$fileName"
        val metadata = VideoMetadata(
            title = title.ifBlank { "Imported MIDI" },
            author = "Device Storage",
            channel = "Local Import",
            channel_id = "",
            channel_url = "",
            upload_date = "",
            duration = 0f,
            thumbnail = "",
            webpage_url = stableUrl,
            view_count = 0L,
            like_count = 0L
        )

        metadataFile.writeText(
            listOf(
                stableUrl,
                metadata.title,
                metadata.author,
                metadata.channel,
                metadata.channel_id,
                metadata.channel_url,
                metadata.upload_date,
                metadata.duration.toString(),
                metadata.thumbnail,
                metadata.webpage_url,
                metadata.view_count.toString(),
                metadata.like_count.toString()
            ).joinToString("\n")
        )

        return StoredMidi(
            file = midiFile,
            videoUrl = stableUrl,
            metadata = metadata
        )
    }

    private fun getOriginalFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "imported.mid"
    }
}
