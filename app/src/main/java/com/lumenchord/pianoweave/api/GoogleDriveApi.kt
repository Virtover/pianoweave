package com.lumenchord.pianoweave.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

data class DriveFileItem(
    val id: String?,
    val name: String?,
    val size: String?,
    val modifiedTime: String?,
    val description: String?
)

data class DriveFileListResponse(
    val files: List<DriveFileItem>?
)

interface GoogleDriveApi {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String?,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("q") q: String = "'appDataFolder' in parents and trashed = false",
        @Query("fields") fields: String = "files(id,name,size,modifiedTime,description)",
        @Query("pageSize") pageSize: Int = 1000
    ): DriveFileListResponse

    @POST("upload/drive/v3/files")
    suspend fun uploadFile(
        @Header("Authorization") authHeader: String?,
        @Query("uploadType") uploadType: String = "multipart",
        @Body body: RequestBody
    ): DriveFileItem

    @Streaming
    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Path("fileId") fileId: String,
        @Header("Authorization") authHeader: String?,
        @Query("alt") alt: String = "media"
    ): ResponseBody

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Path("fileId") fileId: String,
        @Header("Authorization") authHeader: String?
    ): Response<ResponseBody>
}
