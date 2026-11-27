package com.lumenchord.pianoweave.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewTest {

    private fun parse(json: String) = Gson().fromJson(json, BillingCostResponse::class.java).toLinkPreview()

    @Test
    fun aNewServerGivesTitleChannelThumbnailAndLength() {
        val p = parse("""{"duration_seconds": 344, "cost_minutes": 6, "title": " SO BE IT ", "channel": "Elevation Worship",
            "thumbnail": "https://i.ytimg.com/vi/x/hqdefault.jpg", "webpage_url": "https://www.youtube.com/watch?v=x"}""")
        assertEquals("SO BE IT", p.title)
        assertEquals("Elevation Worship · 5:44", p.detailLine)
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", p.thumbnail)
        assertEquals("Video: SO BE IT, by Elevation Worship, length 5:44", p.contentDescription)
    }

    @Test
    fun anOlderServer_showsOnlyTheLength() {
        val p = parse("""{"duration_seconds": 3723, "cost_minutes": 63}""")
        assertNull(p.title)
        assertEquals("1:02:03", p.detailLine)
    }

    @Test
    fun nothingToShow_isEmpty_andOnlyHttpsImagesLoad() {
        val p = parse("""{"duration_seconds": 0, "cost_minutes": 0, "title": "  ", "thumbnail": "http://insecure/x.jpg"}""")
        assertTrue(p.isEmpty)
        assertNull(p.thumbnail)
    }
}
