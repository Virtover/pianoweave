package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object CloudAccountCache {

    private val gson = Gson()
    private const val CACHE_DIR = "account_cache_current"
    private const val INDEX_FILE = "index.json"

    private fun cacheFolder(context: Context): File {
        val dir = File(context.filesDir, CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveCloudSongs(context: Context, cloudSongs: List<CloudMidi>) {
        try {
            val dir = cacheFolder(context)
            val indexFile = File(dir, INDEX_FILE)
            val json = gson.toJson(cloudSongs)
            indexFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCloudSongs(context: Context): List<CloudMidi> {
        try {
            val dir = cacheFolder(context)
            val indexFile = File(dir, INDEX_FILE)
            if (!indexFile.exists()) return emptyList()

            val json = indexFile.readText()
            val type = object : TypeToken<List<CloudMidi>>() {}.type
            val list: List<CloudMidi>? = gson.fromJson(json, type)
            return list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun getCachedMidiFile(context: Context, fileId: String): File? {
        val dir = cacheFolder(context)
        val midiFile = File(dir, "$fileId.mid")
        return if (midiFile.exists() && midiFile.length() > 0) midiFile else null
    }

    fun saveMidiContent(context: Context, fileId: String, sourceFile: File): File {
        val dir = cacheFolder(context)
        val midiFile = File(dir, "$fileId.mid")
        sourceFile.copyTo(midiFile, overwrite = true)
        return midiFile
    }

    fun saveMidiBytes(context: Context, fileId: String, bytes: ByteArray): File {
        val dir = cacheFolder(context)
        val midiFile = File(dir, "$fileId.mid")
        midiFile.writeBytes(bytes)
        return midiFile
    }

    fun deleteCloudSong(context: Context, fileId: String) {
        try {
            val dir = cacheFolder(context)
            File(dir, "$fileId.mid").delete()

            val current = loadCloudSongs(context)
            val updated = current.filterNot { it.id == fileId }
            saveCloudSongs(context, updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearCache(context: Context) {
        try {
            val dir = cacheFolder(context)
            dir.deleteRecursively()
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
