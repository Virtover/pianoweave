package com.example.ytpiano.midi

import android.content.Context
import okhttp3.ResponseBody
import java.io.File

object MidiStorage {

    fun save(
        context: Context,
        jobId: String,
        body: ResponseBody
    ): File {
        val directory = File(
            context.filesDir,
            "midi"
        )

        directory.mkdirs()

        val file = File(
            directory,
            "$jobId.mid"
        )

        body.byteStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file
    }
}