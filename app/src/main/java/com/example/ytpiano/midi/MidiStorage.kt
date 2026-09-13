package com.example.ytpiano.midi

import android.content.Context
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest

data class StoredMidi(
    val file: File,
    val youtubeUrl: String,
    val songTitle: String
)

object MidiStorage {

    private const val DIRECTORY = "midi"
    private const val URL_EXTENSION = ".url"

    private fun directory(context: Context): File {
        return File(
            context.filesDir,
            DIRECTORY
        ).also {
            it.mkdirs()
        }
    }

    private fun idForUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
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
        val id = idForUrl(youtubeUrl)
        val dir = directory(context)

        val midiFile = File(
            dir,
            "$id.mid"
        )

        val urlFile = File(
            dir,
            "$id$URL_EXTENSION"
        )

        body.byteStream().use { input ->
            midiFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Store both title and URL on separate lines inside the metadata descriptor block
        val stableTitle = if (songTitle.isNullOrBlank() || songTitle.trim().lowercase() == "null") "Piano Performance" else songTitle.trim()
        urlFile.writeText("${youtubeUrl.trim()}\n$stableTitle")

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
            ?.mapNotNull { midiFile ->
                val urlFile = File(
                    dir,
                    midiFile.nameWithoutExtension +
                            URL_EXTENSION
                )

                var youtubeUrl = "Unknown Source URL"
                var songTitle = "Piano Performance"

                if (urlFile.exists()) {
                    val lines = urlFile.readLines()
                    if (lines.isNotEmpty()) {
                        youtubeUrl = lines[0]
                    }
                    if (lines.size > 1) {
                        songTitle = lines[1]
                    } else {
                        // Fallback descriptor mapping for legacy or older cache links
                        songTitle = "Cached Performance (${midiFile.nameWithoutExtension.take(6)})"
                    }
                } else {
                    songTitle = "Cached Performance (${midiFile.nameWithoutExtension.take(6)})"
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
                    URL_EXTENSION
        ).delete()
    }
}