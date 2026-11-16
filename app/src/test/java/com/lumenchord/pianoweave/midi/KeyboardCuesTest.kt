package com.lumenchord.pianoweave.midi

import com.lumenchord.pianoweave.audio.ConnectionChime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardCuesTest {
    private var time = 0L
    private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
    private var audible = true
    private val played = mutableListOf<String>()

    private val cues = KeyboardCues(
        schedule = { delay, task ->
            val entry = (time + delay) to task
            tasks += entry
            KeyboardCues.Cancel { tasks.remove(entry) }
        },
        isAudible = { audible },
        play = { rising -> played += if (rising) "back" else "lost" },
    )

    private fun advance(ms: Long) {
        time += ms
        tasks.filter { it.first <= time }.forEach { tasks.remove(it); it.second() }
    }

    @Test
    fun aDropThatLastsSoundsLostAfterTheGraceAndItsReturnSoundsBack() {
        cues.onLost()
        advance(KeyboardCues.GRACE_MS - 1)
        assertTrue(played.isEmpty())
        advance(1)
        assertEquals(listOf("lost"), played)
        cues.onBack()
        assertEquals(listOf("lost", "back"), played)
    }

    @Test
    fun aDropShorterThanTheGraceMakesNoSoundAtAll() {
        cues.onLost()
        advance(2_000)
        cues.onBack()
        advance(10_000)
        assertTrue(played.isEmpty())
    }

    @Test
    fun connectingWithoutAHeardLossIsSilent() {
        cues.onBack() // app start: the remembered keyboard connected by itself
        assertTrue(played.isEmpty())
    }

    @Test
    fun aLossWhileTheAppIsHiddenIsNotHeardSoItsReturnIsSilentToo() {
        audible = false
        cues.onLost()
        advance(KeyboardCues.GRACE_MS)
        audible = true
        cues.onBack()
        assertTrue(played.isEmpty())
    }

    @Test
    fun tappingOrDisconnectingOnPurposeOwesNoSound() {
        cues.onLost()
        cues.reset()
        advance(KeyboardCues.GRACE_MS)
        assertTrue(played.isEmpty())
        cues.onLost(); advance(KeyboardCues.GRACE_MS)
        cues.reset() // then connected by tapping
        cues.onBack()
        assertEquals(listOf("lost"), played)
    }

    @Test
    fun theBackSoundIsOncePerLoss() {
        cues.onLost(); advance(KeyboardCues.GRACE_MS)
        cues.onBack()
        cues.onBack() // e.g. ready again after the keyboard's services changed
        assertEquals(listOf("lost", "back"), played)
    }

    @Test
    fun backRisesAndLostSettlesDownwardBothSoftMajorChords() {
        val back = ConnectionChime.notes(rising = true)
        val lost = ConnectionChime.notes(rising = false)
        assertEquals(back.sorted(), back) // rolled upward
        assertEquals(lost.sortedDescending(), lost) // rolled downward
        assertEquals(setOf(0, 4, 7), back.map { (it - back.first()) % 12 }.toSet()) // C major
        assertEquals(setOf(0, 4, 7), lost.map { (it - lost.last()) % 12 }.toSet()) // C major too: never sad
        back.forEach { assertTrue(ConnectionChime.velocity(it, back) <= 40) }
        assertEquals(ConnectionChime.TOP_VELOCITY, ConnectionChime.velocity(84, back)) // the top voice carries
        // Gentler, voice for voice, and it ends on the root at the bottom: the ground stays
        assertTrue(ConnectionChime.velocity(lost.max(), lost) < ConnectionChime.velocity(back.max(), back))
        assertTrue(ConnectionChime.velocity(lost.min(), lost) < ConnectionChime.velocity(back.min(), back))
        assertEquals(0, (lost.last() - back.first()) % 12)
    }
}
