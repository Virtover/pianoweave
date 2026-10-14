package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.lumenchord.pianoweave.api.GoogleDriveApiFactory
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
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
    val metadata: VideoMetadata
)

data class CloudMetadataEnvelope(
    val videoUrl: String? = null,
    val metadata: VideoMetadata? = null
)

object GoogleDriveManager {

    private val gson = Gson()

    /**
     * Held by a full library sync and by [backUpNewSong]. Without it a song finished during a sync
     * was uploaded twice (once by each), leaving duplicate copies in Drive.
     */
    val libraryMutex = Mutex()

    /** The song's metadata as stored in the Drive file description. */
    private fun descriptionFor(storedMidi: StoredMidi): String =
        gson.toJson(CloudMetadataEnvelope(videoUrl = storedMidi.videoUrl, metadata = storedMidi.metadata))

    /**
     * Uploads a newly transcribed song unless it is already in the cloud, and links it to the
     * uploaded file so later syncs match it by id. Re-reads the song so a concurrent sync is seen.
     */
    suspend fun backUpNewSong(context: Context, song: StoredMidi, authHeader: String?): Result<Unit> = libraryMutex.withLock {
        val current = MidiStorage.listAll(context).firstOrNull { it.file == song.file }
        if (current == null || current.isDeleted || current.cloudFileId.isNotBlank()) return@withLock Result.success(Unit)
        uploadMidi(context, current, authHeader).map { MidiStorage.updateCloudFileId(context, current, it.id) }
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

                    var videoUrl = "cloud://$fileId"
                    var metadata = VideoMetadata(
                        title = fileName.substringBeforeLast('.'),
                        author = "Google Account Cloud",
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

                    if (!item.description.isNullOrBlank()) {
                        try {
                            val env = gson.fromJson(item.description, CloudMetadataEnvelope::class.java)
                            if (env != null) {
                                env.videoUrl?.takeIf { it.isNotBlank() }?.let { videoUrl = it }
                                metadata = sanitizeVideoMetadata(env.metadata, fallbackUrl = videoUrl)
                            }
                        } catch (_: Exception) {
                            // Fallback to default
                        }
                    } else {
                        metadata = sanitizeVideoMetadata(metadata, fallbackUrl = videoUrl)
                    }

                    CloudMidi(
                        id = fileId,
                        name = fileName,
                        sizeBytes = size,
                        modifiedTime = modTime,
                        videoUrl = videoUrl,
                        metadata = metadata
                    )
                } ?: emptyList()
            }

            Result.success(cloudList.sortedByDescending { it.modifiedTime })
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
                    metadata = storedMidi.metadata
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
                bytes = bytes
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
