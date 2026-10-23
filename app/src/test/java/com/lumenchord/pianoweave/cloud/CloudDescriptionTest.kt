package com.lumenchord.pianoweave.cloud

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDescriptionTest {

    private val gson = Gson()
    private val metadata = """{"title":"Clair de Lune","author":"Debussy","duration":312.5}"""

    @Test
    fun readsNamedKeys() {
        val (env, isLegacy) = parseCloudDescription(
            gson, """{"videoUrl":"https://youtu.be/abc","metadata":$metadata,"addedAt":1700000000000}"""
        )!!
        assertFalse(isLegacy)
        assertEquals("https://youtu.be/abc", env.videoUrl)
        assertEquals("Clair de Lune", env.metadata?.title)
        assertEquals(1700000000000L, env.addedAt)
    }

    @Test
    fun readsKeysRenamedByR8InAnyOrder() {
        // What release builds stored: field names replaced by letters, which differ between builds
        for (json in listOf(
            """{"a":"https://youtu.be/abc","b":$metadata,"c":1700000000000}""",
            """{"c":"https://youtu.be/abc","a":1700000000000,"b":$metadata}"""
        )) {
            val (env, isLegacy) = parseCloudDescription(gson, json)!!
            assertTrue(isLegacy)
            assertEquals("https://youtu.be/abc", env.videoUrl)
            assertEquals("Debussy", env.metadata?.author)
            assertEquals(1700000000000L, env.addedAt)
        }
    }

    @Test
    fun legacyWithoutDateStillReadsDetails() {
        val (env, isLegacy) = parseCloudDescription(gson, """{"a":"song.mid","b":$metadata}""")!!
        assertTrue(isLegacy)
        assertEquals("song.mid", env.videoUrl)
        assertEquals("Clair de Lune", env.metadata?.title)
        assertNull(env.addedAt)
    }

    @Test
    fun unreadableDescriptionsGiveNothing() {
        assertNull(parseCloudDescription(gson, "not json"))
        assertNull(parseCloudDescription(gson, "[1,2]"))
        assertNull(parseCloudDescription(gson, "{}"))
    }
}
