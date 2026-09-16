package com.example.ytpiano.api.config

import android.content.Context

object AppConfig {

    private const val CONFIG_FILE = "config/config.txt"
    private const val API_BASE_URL = "API_BASE_URL"

    fun loadBaseUrl(
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

        val url = values[API_BASE_URL]

        require(!url.isNullOrBlank()) {
            "Missing API_BASE_URL in $CONFIG_FILE"
        }

        require(
            url.endsWith("/")
        ) {
            "API_BASE_URL must end with '/'"
        }

        return url
    }
}