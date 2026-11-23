package com.lumenchord.pianoweave.api

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GoogleDriveApiFactory {

    private const val BASE_URL = "https://www.googleapis.com/"

    /** Requests to Drive at once; cloud sync's own limit (SYNC_CONCURRENCY) stays at or below it. */
    const val DRIVE_MAX_REQUESTS = 16

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // OkHttp allows 5 requests per host by default, which capped cloud sync at 5 songs at a time.
            // Small MIDI files are all round-trip time, so more run at once (HTTP/2: one connection).
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = DRIVE_MAX_REQUESTS })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val api: GoogleDriveApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleDriveApi::class.java)
    }
}
