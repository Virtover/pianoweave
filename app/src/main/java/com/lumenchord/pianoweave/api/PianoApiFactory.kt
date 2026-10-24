package com.lumenchord.pianoweave.api

import android.app.Application
import android.content.Context
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.SecretStore
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PianoApiFactory {

    private const val AUTHORIZATION = "Authorization"
    private const val SERVER_PASSWORD_HEADER = "X-Server-Password"

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
            // Each server's password goes to that server alone, never to another one or a link's host
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val password = serverPasswordFor(request.url)
                if (password != null && request.header(SERVER_PASSWORD_HEADER) == null) {
                    chain.proceed(request.newBuilder().header(SERVER_PASSWORD_HEADER, password).build())
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

    /** For background work in a process the app's screen hasn't started; never throws. */
    fun initializeInBackground(context: Context) {
        val app = context.applicationContext as? Application ?: return
        if (application == null) application = app
        runCatching { AppConfig.initialize(app) }
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
        return url.isUnder(official)
    }

    /**
     * The official server's password from config.txt for the official server; the user's saved one for
     * the custom server it belongs to. The official server never gets the custom one, even when the
     * custom URL points at the same address.
     */
    private fun serverPasswordFor(url: HttpUrl): String? =
        if (isOfficialServer(url)) AppConfig.getConfigOrNull()?.serverPassword
        else customServerPasswordFor(url)

    private fun customServerPasswordFor(url: HttpUrl): String? {
        val app = application ?: return null
        val prefs = AppPrefs.get(app)
        val password = SecretStore.get(app, AppPrefs.CUSTOM_SERVER_PASSWORD) ?: return null
        val custom = prefs.getString(AppPrefs.CUSTOM_SERVER_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { withTrailingSlash(it.trim()).toHttpUrlOrNull() }
            ?: return null
        return password.takeIf { url.isUnder(custom) && !isOfficialServer(url) }
    }

    private fun HttpUrl.isUnder(base: HttpUrl) =
        scheme == base.scheme && host == base.host && port == base.port && encodedPath.startsWith(base.encodedPath)

    /**
     * True if the server turned a request away for lack of its password (or a wrong one): 401 with the
     * Basic challenge it sends only then. A 401 for Google sign-in has no such challenge.
     */
    fun isServerPasswordRequired(code: Int, headers: okhttp3.Headers?): Boolean =
        code == 401 && headers?.get("WWW-Authenticate")?.trim()?.startsWith("Basic", ignoreCase = true) == true

    fun isServerPasswordRequired(error: Throwable): Boolean {
        val response = (error as? retrofit2.HttpException)?.response() ?: return false
        return isServerPasswordRequired(response.code(), response.headers())
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
