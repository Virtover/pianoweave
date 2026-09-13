package com.example.ytpiano.midi

import android.content.Context
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest

data class StoredMidi(
    val file: File,
    val youtubeUrl: String
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

        urlFile.writeText(youtubeUrl.trim())

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

                if (!urlFile.exists()) {
                    return@mapNotNull null
                }

                StoredMidi(
                    file = midiFile,
                    youtubeUrl = urlFile.readText()
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