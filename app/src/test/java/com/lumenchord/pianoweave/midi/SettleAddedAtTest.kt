package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class SettleAddedAtTest {

    @Test
    fun keepsTheEarlierOfLocalAndCloudDates() {
        assertEquals(100L, settleAddedAt(localAddedAt = 100L, cloudAddedAt = 200L, cloudCreatedTime = 300L))
        assertEquals(100L, settleAddedAt(localAddedAt = 200L, cloudAddedAt = 100L, cloudCreatedTime = 300L))
    }

    @Test
    fun usesDriveCreationTimeWhenCloudCopyHasNoDate() {
        // A device that dated the song by its download adopts the upload time instead.
        assertEquals(300L, settleAddedAt(localAddedAt = 900L, cloudAddedAt = 0L, cloudCreatedTime = 300L))
        // The device that added it keeps its original date.
        assertEquals(100L, settleAddedAt(localAddedAt = 100L, cloudAddedAt = 0L, cloudCreatedTime = 300L))
    }

    @Test
    fun ignoresMissingCloudDates() {
        assertEquals(500L, settleAddedAt(localAddedAt = 500L, cloudAddedAt = 0L, cloudCreatedTime = 0L))
    }

    @Test
    fun devicesThatDisagreeConverge() {
        // Two devices with different dates both settle on the earliest after syncing through the cloud.
        val first = settleAddedAt(localAddedAt = 900L, cloudAddedAt = 0L, cloudCreatedTime = 300L)
        val second = settleAddedAt(localAddedAt = 100L, cloudAddedAt = first, cloudCreatedTime = 300L)
        val firstAgain = settleAddedAt(localAddedAt = first, cloudAddedAt = second, cloudCreatedTime = 300L)
        assertEquals(100L, second)
        assertEquals(100L, firstAgain)
    }
}
