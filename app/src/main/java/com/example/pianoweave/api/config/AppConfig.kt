package com.example.pianoweave.api.config

import android.content.Context

data class ServerConfig(
    val baseUrl: String,
    val name: String
)

object AppConfig {

    private const val CONFIG_FILE = "config/config.txt"
    private const val DEFAULT_API_BASE_URL = "DEFAULT_API_BASE_URL"
    private const val LEGACY_API_BASE_URL = "API_BASE_URL"
    private const val DEFAULT_SERVER_NAME = "DEFAULT_SERVER_NAME"
    private var config: ServerConfig? = null

    fun initialize(
        context: Context
    ) {
        if (config != null) return
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

        config = ServerConfig(url, name)
    }

    fun getConfig(): ServerConfig {
        return config ?: error("AppConfig.initialize() must be called first")
    }
}