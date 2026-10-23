package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.lumenchord.pianoweave.api.GoogleDriveApiFactory
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.isSharedSourceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

data class CloudMidi(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val videoUrl: String,
    val metadata: VideoMetadata,
    /** When the song was first added to the library, from the description; 0 for copies uploaded before it was stored. */
    val addedAt: Long = 0L,
    /** When this Drive file was created. Stands in for [addedAt] on older copies. */
    val createdTime: Long = 0L,
    /** The description was written by an older release build, with its keys renamed by R8; rewritten on sync. */
    val hasLegacyDescription: Boolean = false
)

/** The date to order and save a cloud song by. */
val CloudMidi.effectiveAddedAt: Long
    get() = addedAt.takeIf { it > 0L } ?: createdTime.takeIf { it > 0L } ?: modifiedTime

/**
 * The song's details, stored as JSON in its Drive file's description. The names are fixed with
 * [SerializedName] (and kept in proguard-rules.pro): other builds and devices must read them back.
 */
data class CloudMetadataEnvelope(
    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("metadata") val metadata: VideoMetadata? = null,
    @SerializedName("addedAt") val addedAt: Long? = null
)

/** Author of the stand-in details given to a cloud song whose description couldn't be read. */
internal const val CLOUD_FALLBACK_AUTHOR = "Google Account Cloud"

/** Stand-in details from the Drive file name, given when the description couldn't be read. */
val VideoMetadata.isCloudFallback: Boolean
    get() = author == CLOUD_FALLBACK_AUTHOR

/**
 * Reads a description written by any build. Older release builds stored the envelope with the field
 * names R8 gave it ("a", "b", "c", different from one build to the next), so when the real names are
 * missing the values are told apart by type: the one string is the URL, the one object the metadata,
 * the one number the date. Returns the envelope and whether it used those renamed keys.
 */
internal fun parseCloudDescription(gson: Gson, description: String): Pair<CloudMetadataEnvelope, Boolean>? {
    val json = runCatching { JsonParser.parseString(description) }.getOrNull()
    if (json == null || !json.isJsonObject) return null
    val obj = json.asJsonObject
    val isNamed = obj.has("videoUrl") || obj.has("metadata")
    val values = obj.entrySet().map { it.value }.filterNot { it.isJsonNull }
    fun pick(name: String, matches: (JsonElement) -> Boolean): JsonElement? =
        if (isNamed) obj.get(name)?.takeUnless { it.isJsonNull } else values.firstOrNull(matches)

    val url = pick("videoUrl") { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    val metadata = pick("metadata") { it.isJsonObject }
        ?.let { runCatching { gson.fromJson(it, VideoMetadata::class.java) }.getOrNull() }
    val addedAt = pick("addedAt") { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asLong }.getOrNull() }
    if (url == null && metadata == null) return null
    return CloudMetadataEnvelope(url, metadata, addedAt) to !isNamed
}

object GoogleDriveManager {

    private val gson = Gson()

    /**
     * Held by a full library sync and by [backUpNewSong]. Without it a song finished during a sync
     * was uploaded twice (once by each), leaving duplicate copies in Drive.
     */
    val libraryMutex = Mutex()

    /** The song's metadata as stored in the Drive file description. */
    private fun descriptionFor(storedMidi: StoredMidi): String =
        gson.toJson(
            CloudMetadataEnvelope(
                videoUrl = storedMidi.videoUrl,
                metadata = storedMidi.metadata,
                addedAt = storedMidi.addedAt.takeIf { it > 0L }
            )
        )

    /**
     * Uploads a newly transcribed song unless it is already in the cloud, and links it to the
     * uploaded file so later syncs match it by id. Re-reads the song so a concurrent sync is seen.
     */
    suspend fun backUpNewSong(context: Context, song: StoredMidi, authHeader: String?): Result<Unit> = libraryMutex.withLock {
        val current = MidiStorage.listAll(context).firstOrNull { it.file == song.file }
        if (current == null || current.isDeleted || current.cloudFileId.isNotBlank()) return@withLock Result.success(Unit)
        uploadMidi(context, current, authHeader).map { MidiStorage.updateCloudFileId(context, current, it.id) }
    }

    /**
     * Rewrites a description that an older release build stored with renamed keys, from what was read
     * out of it, so every build can read it directly from now on.
     */
    suspend fun rewriteLegacyDescription(context: Context, cloud: CloudMidi, authHeader: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val description = gson.toJson(
                    CloudMetadataEnvelope(
                        videoUrl = cloud.videoUrl,
                        metadata = cloud.metadata,
                        addedAt = cloud.addedAt.takeIf { it > 0L }
                    )
                )
                GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                    GoogleDriveApiFactory.api.updateFileMetadata(
                        fileId = cloud.id,
                        authHeader = header,
                        metadata = mapOf("description" to description)
                    )
                }
                Unit
            }
        }

    /** Rewrites the metadata of an existing cloud copy (used to repair copies that lost it). */
    suspend fun updateCloudMetadata(
        context: Context,
        fileId: String,
        storedMidi: StoredMidi,
        authHeader: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                GoogleDriveApiFactory.api.updateFileMetadata(
                    fileId = fileId,
                    authHeader = header,
                    metadata = mapOf("description" to descriptionFor(storedMidi))
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listCloudMidis(
        context: Context,
        authHeader: String? = null
    ): Result<List<CloudMidi>> = withContext(Dispatchers.IO) {
        try {
            val cloudList = GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                val listResponse = GoogleDriveApiFactory.api.listFiles(authHeader = header)

                listResponse.files?.mapNotNull { item ->
                    val fileId = item.id ?: return@mapNotNull null
                    val fileName = item.name ?: return@mapNotNull null
                    val size = item.size?.toLongOrNull() ?: 0L
                    val modTime = try {
                        Instant.parse(item.modifiedTime).toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    val createdTime = try {
                        Instant.parse(item.createdTime).toEpochMilli()
                    } catch (_: Exception) {
                        0L
                    }
                    var addedAt = 0L
                    var hasLegacyDescription = false

                    var videoUrl = "cloud://$fileId"
                    var metadata = VideoMetadata(
                        title = fileName.substringBeforeLast('.'),
                        author = CLOUD_FALLBACK_AUTHOR,
                        channel = "Cloud Storage",
                        channel_id = "",
                        channel_url = "",
                        upload_date = "",
                        duration = 0f,
                        thumbnail = "",
                        webpage_url = videoUrl,
                        view_count = 0L,
                        like_count = 0L
                    )

                    val parsed = item.description?.takeIf { it.isNotBlank() }?.let { parseCloudDescription(gson, it) }
                    if (parsed != null) {
                        val (env, isLegacy) = parsed
                        env.videoUrl?.takeIf { it.isNotBlank() }?.let { videoUrl = it }
                        metadata = sanitizeVideoMetadata(env.metadata, fallbackUrl = videoUrl)
                        addedAt = env.addedAt ?: 0L
                        hasLegacyDescription = isLegacy
                    } else {
                        metadata = sanitizeVideoMetadata(metadata, fallbackUrl = videoUrl)
                    }

                    CloudMidi(
                        id = fileId,
                        name = fileName,
                        sizeBytes = size,
                        modifiedTime = modTime,
                        videoUrl = videoUrl,
                        metadata = metadata,
                        addedAt = addedAt,
                        createdTime = createdTime,
                        hasLegacyDescription = hasLegacyDescription
                    )
                } ?: emptyList()
            }

            Result.success(cloudList.sortedByDescending { it.effectiveAddedAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMidi(
        context: Context,
        storedMidi: StoredMidi,
        authHeader: String? = null
    ): Result<CloudMidi> = withContext(Dispatchers.IO) {
        try {
            val metadataMap = mapOf(
                "name" to storedMidi.file.name,
                "parents" to listOf("appDataFolder"),
                "description" to descriptionFor(storedMidi)
            )
            val metadataJson = gson.toJson(metadataMap)

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    storedMidi.file.asRequestBody("audio/midi".toMediaType())
                )
                .build()

            val item = GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                GoogleDriveApiFactory.api.uploadFile(authHeader = header, body = multipartBody)
            }

            val fileId = item.id ?: throw Exception("No file ID returned from Drive upload")

            Result.success(
                CloudMidi(
                    id = fileId,
                    name = storedMidi.file.name,
                    sizeBytes = storedMidi.file.length(),
                    modifiedTime = System.currentTimeMillis(),
                    videoUrl = storedMidi.videoUrl,
                    metadata = storedMidi.metadata,
                    addedAt = storedMidi.addedAt,
                    createdTime = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMidiFile(
        context: Context,
        cloudMidi: CloudMidi,
        authHeader: String? = null
    ): Result<StoredMidi> = withContext(Dispatchers.IO) {
        try {
            val bytes = GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                val responseBody = GoogleDriveApiFactory.api.downloadFile(fileId = cloudMidi.id, authHeader = header)
                responseBody.bytes()
            }

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Downloaded file from cloud is empty"))
            }

            val storedMidi = MidiStorage.saveBytes(
                context = context,
                videoUrl = cloudMidi.videoUrl,
                metadata = cloudMidi.metadata,
                bytes = bytes,
                addedAt = cloudMidi.effectiveAddedAt,
                // Songs sharing a key would overwrite each other's local file.
                storageKey = if (cloudMidi.videoUrl.isSharedSourceKey) "cloud://${cloudMidi.id}" else cloudMidi.videoUrl
            )
            CloudAccountCache.saveMidiBytes(context, cloudMidi.id, bytes)

            Result.success(storedMidi)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCloudMidi(
        context: Context,
        fileId: String,
        authHeader: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                val response = GoogleDriveApiFactory.api.deleteFile(fileId = fileId, authHeader = header)
                if (!response.isSuccessful && response.code() != 404) {
                    val errBody = response.errorBody()?.string() ?: ""
                    throw Exception("Failed to delete file from Drive (${response.code()}): $errBody")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
