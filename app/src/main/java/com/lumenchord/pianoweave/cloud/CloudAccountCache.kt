package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import java.io.File

/**
 * The cloud library as of the last sync (the list only; the songs themselves are in the library).
 * Older versions also kept a second copy of every downloaded song here; [saveCloudSongs] removes them.
 */
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
            dir.listFiles()?.filter { it.extension == "mid" }?.forEach { it.delete() }
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
            val rawList: List<CloudMidi>? = gson.fromJson(json, type)
            if (rawList == null) return emptyList()

            return rawList.mapNotNull { item ->
                val safeId = (item.id as String?).orEmpty()
                if (safeId.isBlank()) return@mapNotNull null
                val safeName = (item.name as String?) ?: return@mapNotNull null
                val safeVideoUrl = (item.videoUrl as String?).orEmpty().ifBlank { "cloud://$safeId" }
                val safeMetadata = sanitizeVideoMetadata(item.metadata, safeVideoUrl)

                CloudMidi(
                    id = safeId,
                    name = safeName,
                    sizeBytes = item.sizeBytes,
                    modifiedTime = item.modifiedTime,
                    videoUrl = safeVideoUrl,
                    metadata = safeMetadata,
                    addedAt = item.addedAt,
                    createdTime = item.createdTime
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
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
