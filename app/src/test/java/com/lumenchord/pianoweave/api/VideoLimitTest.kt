package com.lumenchord.pianoweave.api

import com.google.gson.Gson
import com.lumenchord.pianoweave.billing.longerVideosText
import com.lumenchord.pianoweave.billing.usablePlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plans that allow longer videos, and the limit the app checks before sending. */
class VideoLimitTest {

    private val gson = Gson()

    private fun balance(json: String) = gson.fromJson(json, UserBalanceResponse::class.java)

    @Test
    fun theBalanceTellsALimitNoLimitAndNothingApart() {
        assertEquals(VideoLimit.Minutes(120f), balance("""{"user_id": "u", "minutes": 0, "max_video_length_minutes": 120}""").videoLimit)
        assertEquals(VideoLimit.Minutes(37.5f), balance("""{"user_id": "u", "minutes": 0, "max_video_length_minutes": 37.5}""").videoLimit)
        // JSON null: no limit for this user
        assertEquals(VideoLimit.None, balance("""{"user_id": "u", "minutes": 0, "max_video_length_minutes": null}""").videoLimit)
        // Absent (older servers): the general limit applies
        assertNull(balance("""{"user_id": "u", "minutes": 0}""").videoLimit)
        assertNull(balance("""{"user_id": "u", "minutes": 0, "max_video_length_minutes": "lots"}""").videoLimit)
    }

    @Test
    fun theUsersLimitComesBeforeTheGeneralOne() {
        assertEquals(120f, effectiveMaxVideoMinutes(VideoLimit.Minutes(120f), 60f))
        assertNull(effectiveMaxVideoMinutes(VideoLimit.None, 60f))
        assertEquals(60f, effectiveMaxVideoMinutes(null, 60f))
        assertNull(effectiveMaxVideoMinutes(null, null))
    }

    @Test
    fun plansAndTheSubscriptionReadTheirLimits() {
        val info = gson.fromJson(
            """{"subscriptions": [
                 {"product_id": "sub_pro", "minutes_per_period": 100, "level": 1, "max_video_length_minutes": null},
                 {"product_id": "sub_unlimited", "unlimited": true, "level": 2, "max_video_length_minutes": 120}],
               "max_video_length_minutes": 60}""",
            ServerInfoResponse::class.java
        )
        val plans = usablePlans(info.subscriptions)
        assertNull(plans[0].maxVideoLengthMinutes)
        assertEquals(120, plans[1].maxVideoLengthMinutes)
        val sub = balance("""{"user_id": "u", "minutes": 0, "subscription": {"product_id": "sub_unlimited", "max_video_length_minutes": 120}}""").subscription!!
        assertEquals(120f, sub.maxVideoLengthMinutes)
    }

    @Test
    fun aPlanCardMentionsOnlyALongerLimit() {
        assertEquals("Videos up to 120 min", longerVideosText(120, 60f))
        assertEquals("Videos up to 120 min", longerVideosText(120, null))
        assertNull(longerVideosText(60, 60f))
        assertNull(longerVideosText(30, 60f))
        assertNull(longerVideosText(null, 60f))
    }

    @Test
    fun aTooLongVideoIsSaidPlainly() {
        val new = """{"detail": {"message": "Videos can be up to 120 minutes long", "error_code": "video_too_long", "max_video_length_minutes": 120}}"""
        assertEquals("Videos can be up to 120 minutes long", ServerErrors.videoTooLongMessage(413, new, 60f))
        // Older servers' plain string: the limit the app knows
        val old = """{"detail": "Video exceeds the configured maximum length"}"""
        assertEquals("Videos can be up to 60 minutes long.", ServerErrors.videoTooLongMessage(413, old, 60f))
        assertEquals("This video is too long for the server.", ServerErrors.videoTooLongMessage(413, old, null))
        // Another 413 (a file too large), or another status: not this message
        assertNull(ServerErrors.videoTooLongMessage(413, """{"detail": "File too large"}""", 60f))
        assertNull(ServerErrors.videoTooLongMessage(400, new, 60f))
    }
}
