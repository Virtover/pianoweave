package com.example.ytpiano.midi

import android.content.Context
import android.util.Log
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredMidi(
    val file: File,
    val youtubeUrl: String,
    val songTitle: String
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
        songTitle: String?,
        body: ResponseBody
    ): File {
        Log.d("MidiStorage", "Saving MIDI file for URL: $youtubeUrl")
        val id = idForUrl(youtubeUrl)
        val dir = directory(context)
        Log.d("MidiStorage", "Directory path: ${dir.absolutePath}")
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
        val stableTitle = songTitle
            ?.trim()
            ?.takeIf {
                it.isNotBlank() &&
                        !it.equals("null", ignoreCase = true)
            }
            ?: "Piano Performance"

        metadataFile.writeText(
            "$stableUrl\n$stableTitle"
        )
        return midiFile
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

                var youtubeUrl = "Unknown source"
                var songTitle = "Piano Performance"

                if (metadataFile.exists()) {
                    val lines = metadataFile.readLines()

                    if (lines.isNotEmpty()) {
                        youtubeUrl = lines[0].trim()
                    }

                    if (lines.size > 1) {
                        songTitle = lines
                            .drop(1)
                            .joinToString("\n")
                            .trim()
                            .ifBlank {
                                "Piano Performance"
                            }
                    }
                }

                StoredMidi(
                    file = midiFile,
                    youtubeUrl = youtubeUrl,
                    songTitle = songTitle
                )
            }
            ?.sortedByDescending {
                it.file.lastModified()
            }
            ?: emptyList()
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