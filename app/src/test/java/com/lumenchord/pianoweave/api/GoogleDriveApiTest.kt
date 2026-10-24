package com.lumenchord.pianoweave.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** The Drive file list comes in pages; a page missing from a sync would make its songs count as deleted. */
class GoogleDriveApiTest {

    private val requests = mutableListOf<Request>()

    private fun api(vararg pages: String): GoogleDriveApi {
        var next = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requests += chain.request()
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pages[next++].toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("https://drive.test/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleDriveApi::class.java)
    }

    @Test
    fun listAsksForThePageTokenAndPassesItBack() = runBlocking {
        val drive = api(
            """{"nextPageToken": "p2", "files": [{"id": "a", "name": "a.mid"}]}""",
            """{"files": [{"id": "b", "name": "b.mid"}]}"""
        )
        val first = drive.listFiles(authHeader = "Bearer t")
        assertEquals("p2", first.nextPageToken)
        assertTrue(requests[0].url.queryParameter("fields")!!.contains("nextPageToken"))
        assertNull(requests[0].url.queryParameter("pageToken"))

        val second = drive.listFiles(authHeader = "Bearer t", pageToken = first.nextPageToken)
        assertEquals("p2", requests[1].url.queryParameter("pageToken"))
        assertNull(second.nextPageToken)
        assertEquals(listOf("a", "b"), (first.files!! + second.files!!).map { it.id })
    }
}
