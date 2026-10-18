package com.lumenchord.pianoweave.util

import android.content.Context
import org.json.JSONObject

/** A verse from assets/bible_verses.json, paired with a name of God that fits it. */
data class BibleVerse(
    val ref: String,
    val text: String,
    val godName: String,
    val godNameHebrew: String,
    val godNameMeaning: String
)

object BibleVerses {
    private const val ASSET = "bible_verses.json"

    /** Reads every verse from the bundled asset. Does disk IO; call off the main thread. */
    fun load(context: Context): List<BibleVerse> {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val verses = JSONObject(json).getJSONArray("verses")
        return (0 until verses.length()).map { i ->
            val verse = verses.getJSONObject(i)
            val name = verse.getJSONObject("godName")
            BibleVerse(
                ref = verse.getString("ref"),
                text = verse.getString("text"),
                godName = name.getString("name"),
                godNameHebrew = name.optString("hebrew"),
                godNameMeaning = name.optString("meaning")
            )
        }
    }
}
