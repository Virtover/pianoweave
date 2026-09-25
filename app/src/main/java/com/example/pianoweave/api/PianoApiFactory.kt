package com.example.pianoweave.api

import android.app.Application
import android.content.Context
import com.example.pianoweave.api.config.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PianoApiFactory {

    private var application: Application? = null
    private var cachedBaseUrl: String? = null
    private var cachedApi: PianoApi? = null

    fun initialize(application: Application) {
        this.application = application
    }

    fun getApi(baseUrl: String): PianoApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (cachedApi == null || cachedBaseUrl != normalizedUrl) {
            cachedBaseUrl = normalizedUrl
            cachedApi = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PianoApi::class.java)
        }
        return cachedApi!!
    }

    fun getApi(context: Context): PianoApi {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean("use_custom_server", false)
        val customUrl = prefs.getString("custom_server_url", "") ?: ""
        val defaultUrl = AppConfig.loadDefaultBaseUrl(context)

        val activeUrl = if (useCustom && customUrl.isNotBlank()) {
            customUrl
        } else {
            defaultUrl
        }
        return getApi(activeUrl)
    }

    val api: PianoApi
        get() {
            val app = application ?: error("PianoApiFactory.initialize() must be called first")
            return getApi(app)
        }
}
