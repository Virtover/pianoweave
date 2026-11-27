package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.lumenchord.pianoweave.api.GoogleDriveApiFactory
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.util.AccountProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The account's copy of [AccountProgress]: one small JSON file in the app's Drive folder, next to the songs (which
 * [GoogleDriveManager.listCloudMidis] leaves it out of). Merged both ways: the account's later changes come here,
 * and this device's go up.
 */
object AccountProgressSync {
    const val FILE_NAME = "piano_weave_progress.json"

    private val json = "application/json; charset=UTF-8".toMediaType()
    private val mutex = Mutex()

    /** Merges with the account's copy. Returns whether this device's progress changed. Skipped while debug ignores the account. */
    suspend fun sync(context: Context, authHeader: String?): Result<Boolean> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (AccountProgress.isIgnoringAccount(context)) return@runCatching false
                GoogleAuthManager.executeWithAuthRetry(context, authHeader) { header ->
                    val api = GoogleDriveApiFactory.api
                    val copies = api.listFiles(
                        authHeader = header,
                        q = "name = '$FILE_NAME' and 'appDataFolder' in parents and trashed = false",
                        fields = "files(id,name,modifiedTime)"
                    ).files.orEmpty().filter { it.id != null }
                    // Two devices creating it at once leave two; each is merged, then all but one removed
                    var changed = false
                    for (copy in copies) {
                        val remote = api.downloadFile(fileId = copy.id!!, authHeader = header).string()
                        if (AccountProgress.mergeJson(context, remote)) changed = true
                    }
                    val merged = AccountProgress.toJson(context).toRequestBody(json)
                    val kept = copies.firstOrNull()?.id
                    if (kept != null) {
                        api.updateFileContent(fileId = kept, authHeader = header, body = merged)
                    } else {
                        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}""".toRequestBody(json)
                        api.uploadFile(
                            authHeader = header,
                            body = MultipartBody.Builder()
                                .setType("multipart/related".toMediaType())
                                .addPart(metadata)
                                .addPart(merged)
                                .build()
                        )
                    }
                    copies.drop(1).forEach { GoogleDriveManager.requireDeleted(api.deleteFile(fileId = it.id!!, authHeader = header)) }
                    changed
                }
            }
        }
    }
}
