package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.google.gson.Gson
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.midi.StoredMidi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

data class CloudMidi(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val videoUrl: String,
    val metadata: VideoMetadata
)

data class CloudMetadataEnvelope(
    val videoUrl: String,
    val metadata: VideoMetadata
)

private data class DriveFileItem(
    val id: String?,
    val name: String?,
    val size: String?,
    val modifiedTime: String?,
    val description: String?
)

private data class DriveFileListResponse(
    val files: List<DriveFileItem>?
)

object GoogleDriveManager {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    suspend fun listCloudMidis(accessToken: String): Result<List<CloudMidi>> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_FILES_URL?spaces=appDataFolder&q='appDataFolder'+in+parents+and+trashed%3Dfalse&fields=files(id,name,size,modifiedTime,description)&pageSize=1000"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        Exception("Drive API error ${response.code}: ${response.message}. $errBody")
                    )
                }

                val json = response.body?.string() ?: ""
                val listResponse = gson.fromJson(json, DriveFileListResponse::class.java)

                val cloudList = listResponse.files?.mapNotNull { item ->
                    val fileId = item.id ?: return@mapNotNull null
                    val fileName = item.name ?: "Untitled.mid"
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
                                videoUrl = env.videoUrl
                                metadata = env.metadata
                            }
                        } catch (_: Exception) {
                            // Fallback to default
                        }
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

                Result.success(cloudList.sortedByDescending { it.modifiedTime })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMidi(accessToken: String, storedMidi: StoredMidi): Result<CloudMidi> = withContext(Dispatchers.IO) {
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

            val request = Request.Builder()
                .url(DRIVE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        Exception("Failed to upload to Google Drive (${response.code}): $errBody")
                    )
                }

                val json = response.body?.string() ?: ""
                val item = gson.fromJson(json, DriveFileItem::class.java)
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMidiFile(
        context: Context,
        accessToken: String,
        cloudMidi: CloudMidi
    ): Result<StoredMidi> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_FILES_URL/${cloudMidi.id}?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to download cloud MIDI file (${response.code})")
                    )
                }

                val responseBody = response.body ?: return@withContext Result.failure(
                    Exception("Empty response body from Drive download")
                )

                val bytes = responseBody.bytes()
                val cachedFile = CloudAccountCache.saveMidiBytes(context, cloudMidi.id, bytes)

                Result.success(
                    StoredMidi(
                        file = cachedFile,
                        videoUrl = cloudMidi.videoUrl,
                        metadata = cloudMidi.metadata
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCloudMidi(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_FILES_URL/$fileId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        Exception("Failed to delete file from Drive (${response.code}): $errBody")
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
