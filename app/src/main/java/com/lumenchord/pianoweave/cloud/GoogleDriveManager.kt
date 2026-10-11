package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.lumenchord.pianoweave.api.GoogleDriveApiFactory
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import kotlinx.coroutines.Dispatchers
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

fun sanitizeVideoMetadata(meta: VideoMetadata?, fallbackUrl: String = ""): VideoMetadata {
    if (meta == null) {
        return VideoMetadata(
            title = "Piano Performance",
            author = "Unknown",
            channel = "Unknown",
            channel_id = "",
            channel_url = "",
            upload_date = "",
            duration = 0f,
            thumbnail = "",
            webpage_url = fallbackUrl,
            view_count = 0L,
            like_count = 0L
        )
    }
    val safeTitle = (meta.title as String?).orEmpty().ifBlank { "Piano Performance" }
    val safeAuthor = (meta.author as String?).orEmpty().ifBlank { "Unknown" }
    val safeChannel = (meta.channel as String?).orEmpty().ifBlank { "Unknown" }
    val safeChannelId = (meta.channel_id as String?).orEmpty()
    val safeChannelUrl = (meta.channel_url as String?).orEmpty()
    val safeUploadDate = (meta.upload_date as String?).orEmpty()
    val safeDuration = (meta.duration as Float?) ?: 0f
    val safeThumbnail = (meta.thumbnail as String?).orEmpty()
    val safeWebpageUrl = (meta.webpage_url as String?).orEmpty().ifBlank { fallbackUrl }
    val safeViewCount = (meta.view_count as Long?) ?: 0L
    val safeLikeCount = (meta.like_count as Long?) ?: 0L

    return VideoMetadata(
        title = safeTitle,
        author = safeAuthor,
        channel = safeChannel,
        channel_id = safeChannelId,
        channel_url = safeChannelUrl,
        upload_date = safeUploadDate,
        duration = safeDuration,
        thumbnail = safeThumbnail,
        webpage_url = safeWebpageUrl,
        view_count = safeViewCount,
        like_count = safeLikeCount
    )
}

object GoogleDriveManager {

    private val gson = Gson()

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
            val envelope = CloudMetadataEnvelope(
                videoUrl = storedMidi.videoUrl,
                metadata = storedMidi.metadata
            )
            val envelopeJson = gson.toJson(envelope)

            val metadataMap = mapOf(
                "name" to storedMidi.file.name,
                "parents" to listOf("appDataFolder"),
                "description" to envelopeJson
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
