package com.lumenchord.pianoweave.api.config

import android.content.Context
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.SecretStore
import com.lumenchord.pianoweave.util.isDebuggable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ServerConfig(
    val baseUrl: String,
    val name: String,
    val supportMeLink: String? = null,
    val webClientId: String? = null,
    val discordLink: String? = null,
    /** The official server's password, if it has one; sent to that server alone. */
    val serverPassword: String? = null,
    /** Optional (config.txt DEV_API_BASE_URL / DEV_SERVER_NAME): what the debug dev server's fields start with. */
    val devBaseUrl: String? = null,
    val devName: String? = null
)

/**
 * A dev server's address as typed: scheme added (https) when missing, a trailing slash, null when it is no URL.
 * Type "http://" yourself for a local server.
 */
fun normalizeDevServerUrl(text: String): String? {
    var url = text.trim()
    if (url.isEmpty()) return null
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
    if (!url.endsWith("/")) url += "/"
    return url.takeIf { it.toHttpUrlOrNull() != null }
}

/**
 * [official] with the debug dev server in its place when [enabled] and [url] is an address. The name falls back to
 * "<official name> (dev)". The official server's password is never sent to the dev server; it gets only its own [password], if any.
 */
fun debugServerConfig(official: ServerConfig, enabled: Boolean, url: String, name: String, password: String = ""): ServerConfig {
    if (!enabled) return official
    val dev = normalizeDevServerUrl(url) ?: return official
    return official.copy(baseUrl = dev, name = name.trim().ifBlank { official.name + " (dev)" }, serverPassword = password.takeIf { it.isNotEmpty() })
}

object AppConfig {

    private const val CONFIG_FILE = "config/config.txt"
    private const val DEFAULT_API_BASE_URL = "DEFAULT_API_BASE_URL"
    private const val LEGACY_API_BASE_URL = "API_BASE_URL"
    private const val DEFAULT_SERVER_NAME = "DEFAULT_SERVER_NAME"
    private const val SUPPORT_ME_LINK = "SUPPORT_ME_LINK"
    private const val SUPPORT_ME_URL = "SUPPORT_ME_URL"
    private const val WEB_CLIENT_ID = "WEB_CLIENT_ID"
    private const val GOOGLE_CLIENT_ID = "GOOGLE_CLIENT_ID"
    private const val CLIENT_ID = "CLIENT_ID"
    private const val DISCORD_URL = "DISCORD_URL"
    private const val DISCORD_LINK = "DISCORD_LINK"
    private const val DEFAULT_SERVER_PASSWORD = "DEFAULT_SERVER_PASSWORD"
    private const val DEV_API_BASE_URL = "DEV_API_BASE_URL"
    private const val DEV_SERVER_NAME = "DEV_SERVER_NAME"
    /** The config.txt values, whatever the debug switch says. */
    private var base: ServerConfig? = null
    /** What the app reads: [base], or in a debug build with the dev server switched on, that server (see [debugServerConfig]). */
    private var config: ServerConfig? = null

    /**
     * Debug builds only: "official server" is the dev server of the Settings → Debug switch. Everything that asks
     * for the official server (its address and name, who gets the Google token, announcements) reads this config,
     * so the switch reaches all of it. Release builds always get config.txt's server.
     */
    fun applyDebugServer(context: Context) {
        val official = base ?: return
        if (!context.isDebuggable) {
            config = official
            return
        }
        val prefs = AppPrefs.get(context)
        config = debugServerConfig(
            official,
            enabled = prefs.getBoolean(AppPrefs.DEBUG_SERVER_ENABLED, false),
            url = prefs.getString(AppPrefs.DEBUG_SERVER_URL, null) ?: official.devBaseUrl.orEmpty(),
            name = prefs.getString(AppPrefs.DEBUG_SERVER_NAME, null) ?: official.devName.orEmpty(),
            password = SecretStore.get(context, AppPrefs.DEBUG_SERVER_PASSWORD).orEmpty()
        )
    }

    fun initialize(
        context: Context
    ) {
        if (base != null) return
        val values = context.assets
            .open(CONFIG_FILE)
            .bufferedReader()
            .useLines { lines ->
                lines
                    .map(String::trim)
                    .filter {
                        it.isNotEmpty() &&
                                !it.startsWith("#")
                    }
                    .mapNotNull { line ->
                        val separator = line.indexOf('=')

                        if (separator <= 0) {
                            null
                        } else {
                            val key = line
                                .substring(0, separator)
                                .trim()

                            val value = line
                                .substring(separator + 1)
                                .trim()
                                .removeSurrounding("\"")

                            key to value
                        }
                    }
                    .toMap()
            }

        val url = values[DEFAULT_API_BASE_URL]
            ?: values[LEGACY_API_BASE_URL]

        require(!url.isNullOrBlank()) {
            "Missing DEFAULT_API_BASE_URL in $CONFIG_FILE"
        }

        require(url.endsWith("/")) {
            "DEFAULT_API_BASE_URL must end with '/'"
        }

        val name = values[DEFAULT_SERVER_NAME]

        require(!name.isNullOrBlank()) {
            "Missing DEFAULT_SERVER_NAME in $CONFIG_FILE"
        }

        val supportMeLink = values[SUPPORT_ME_LINK] ?: values[SUPPORT_ME_URL]
        val webClientId = values[WEB_CLIENT_ID] ?: values[GOOGLE_CLIENT_ID] ?: values[CLIENT_ID]
        val discordLink = (values[DISCORD_URL] ?: values[DISCORD_LINK])?.takeIf { it.isNotBlank() }

        val serverPassword = values[DEFAULT_SERVER_PASSWORD]?.takeIf { it.isNotEmpty() }

        // Optional defaults for the debug dev server's fields (config.txt is not committed)
        val devUrl = values[DEV_API_BASE_URL]?.takeIf { it.isNotBlank() }
        val devName = values[DEV_SERVER_NAME]?.takeIf { it.isNotBlank() }

        base = ServerConfig(url, name, supportMeLink, webClientId, discordLink, serverPassword, devUrl, devName)
        applyDebugServer(context)
    }

    fun getConfig(): ServerConfig {
        return config ?: error("AppConfig.initialize() must be called first")
    }

    fun getConfigOrNull(): ServerConfig? = config
}
