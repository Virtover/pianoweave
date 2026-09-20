package com.example.pianoweave.api

import android.app.Application
import com.example.pianoweave.api.config.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PianoApiFactory {

    private lateinit var application: Application

    fun initialize(application: Application) {
        this.application = application
    }

    val api: PianoApi by lazy {
        check(::application.isInitialized) {
            "PianoApiFactory.initialize() must be called first"
        }

        Retrofit.Builder()
            .baseUrl(
                AppConfig.loadBaseUrl(
                    application
                )
            )
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(PianoApi::class.java)
    }
}