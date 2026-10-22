package com.lumenchord.pianoweave.api

import android.app.Application
import android.content.Context
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.util.AppPrefs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PianoApiFactory {

    private const val AUTHORIZATION = "Authorization"

    private var application: Application? = null
    private var cachedBaseUrl: String? = null
    private var cachedApi: PianoApi? = null

    /**
     * Last line of defence: the user's Google token must never reach a server other than the
     * official one, whatever the call site passed in.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // The server checks an uploaded file (up to a minute) and a link's length before it answers.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                if (request.header(AUTHORIZATION) != null && !isOfficialServer(request.url)) {
                    chain.proceed(request.newBuilder().removeHeader(AUTHORIZATION).build())
                } else {
                    chain.proceed(request)
                }
            })
            // Every response says whether a restart or shutdown is planned
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request()).also(ServerMaintenance::onResponse)
            })
            .build()
    }

    fun initialize(application: Application, context: Context) {
        this.application = application
        AppConfig.initialize(context)
    }

    /**
     * True if [url] points at the official server from config.txt. Returns false while the
     * config is not loaded, so callers fail closed (no token is sent).
     */
    fun isOfficialServer(url: String): Boolean {
        val parsed = withTrailingSlash(url.trim()).toHttpUrlOrNull() ?: return false
        return isOfficialServer(parsed)
    }

    private fun isOfficialServer(url: HttpUrl): Boolean {
        val official = AppConfig.getConfigOrNull()?.baseUrl?.let { withTrailingSlash(it).toHttpUrlOrNull() }
            ?: return false
        return url.scheme == official.scheme &&
            url.host == official.host &&
            url.port == official.port &&
            url.encodedPath.startsWith(official.encodedPath)
    }

    fun getApi(baseUrl: String): PianoApi {
        val normalizedUrl = withTrailingSlash(baseUrl)
        if (cachedApi == null || cachedBaseUrl != normalizedUrl) {
            cachedBaseUrl = normalizedUrl
            cachedApi = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PianoApi::class.java)
        }
        return cachedApi!!
    }

    fun getApi(context: Context): PianoApi {
        val prefs = AppPrefs.get(context)
        val useCustom = prefs.getBoolean(AppPrefs.USE_CUSTOM_SERVER, false)
        val customUrl = prefs.getString(AppPrefs.CUSTOM_SERVER_URL, "") ?: ""
        AppConfig.initialize(context)
        val defaultUrl = AppConfig.getConfig().baseUrl

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

    private fun withTrailingSlash(url: String) = if (url.endsWith("/")) url else "$url/"
}
