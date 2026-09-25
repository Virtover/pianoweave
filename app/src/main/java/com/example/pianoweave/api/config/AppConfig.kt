package com.example.pianoweave.api.config

import android.content.Context

object AppConfig {

    private const val CONFIG_FILE = "config/config.txt"
    private const val DEFAULT_API_BASE_URL = "DEFAULT_API_BASE_URL"
    private const val LEGACY_API_BASE_URL = "API_BASE_URL"

    fun loadDefaultBaseUrl(
        context: Context
    ): String {
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
                        val separator =
                            line.indexOf('=')

                        if (separator <= 0) {
                            null
                        } else {
                            val key =
                                line.substring(
                                    0,
                                    separator
                                ).trim()

                            val value =
                                line.substring(
                                    separator + 1
                                ).trim()

                            key to value
                        }
                    }
                    .toMap()
            }

        val url = values[DEFAULT_API_BASE_URL] ?: values[LEGACY_API_BASE_URL]

        require(!url.isNullOrBlank()) {
            "Missing DEFAULT_API_BASE_URL in $CONFIG_FILE"
        }

        require(
            url.endsWith("/")
        ) {
            "DEFAULT_API_BASE_URL must end with '/'"
        }

        return url
    }
}
