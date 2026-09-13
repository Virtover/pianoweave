package com.example.ytpiano.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PianoApiFactory {

    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: PianoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PianoApi::class.java)
    }
}