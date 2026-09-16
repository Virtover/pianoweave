package com.example.ytpiano.midi

import android.content.Context
import android.util.Log
import com.example.ytpiano.api.VideoMetadata
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest

data class StoredMidi(
    val file: File,
    val youtubeUrl: String,
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
        youtubeUrl: String
    ): File? {
        val id = idForUrl(youtubeUrl)

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
        youtubeUrl: String,
        metadata: VideoMetadata?,
        body: ResponseBody
    ): StoredMidi {
        Log.d(
            "MidiStorage",
            "Saving MIDI file for URL: $youtubeUrl"
        )

        val id = idForUrl(youtubeUrl)
        val dir = directory(context)

        Log.d(
            "MidiStorage",
            "Directory path: ${dir.absolutePath}"
        )

        val midiFile = File(
            dir,
            "$id.mid"
        )

        val metadataFile = File(
            dir,
            "$id$METADATA_EXTENSION"
        )

        body.byteStream().use { input ->
            midiFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val stableUrl = youtubeUrl.trim()

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
            youtubeUrl = stableUrl,
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
            ?.map { midiFile ->
                val metadataFile = File(
                    dir,
                    midiFile.nameWithoutExtension +
                            METADATA_EXTENSION
                )

                val metadata =
                    readMetadata(
                        metadataFile = metadataFile,
                        midiFile = midiFile
                    )

                StoredMidi(
                    file = midiFile,
                    youtubeUrl = metadata.webpage_url,
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
                youtubeUrl = lines
                    .firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank {
                        "Unknown source"
                    }
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
            Log.e(
                "MidiStorage",
                "Failed to read metadata for " +
                        midiFile.name,
                e
            )

            fallbackMetadata(
                youtubeUrl = lines
                    .firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank {
                        "Unknown source"
                    }
            )
        }
    }

    private fun fallbackMetadata(
        youtubeUrl: String = "Unknown source"
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
            webpage_url = youtubeUrl,
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
}