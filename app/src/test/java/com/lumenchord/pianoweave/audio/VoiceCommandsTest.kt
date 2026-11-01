package com.lumenchord.pianoweave.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    private val chunk = FloatArray(512) { 0.1f }

    /** Feeds [count] chunks at [prob] and returns the utterances that finished. */
    private fun UtteranceSegmenter.run(count: Int, prob: Float): List<FloatArray> =
        (0 until count).mapNotNull { feed(chunk, prob) }

    private val pause = UtteranceSegmenter.PAUSE_CHUNKS

    private fun words(vararg w: String, conf: Float = 0.95f) = w.map { it to conf }

    @Test
    fun shortIsolatedWordIsAnUtterance() {
        val seg = UtteranceSegmenter()
        assertTrue(seg.run(pause, 0.05f).isEmpty())
        assertTrue(seg.run(15, 0.9f).isEmpty())
        val done = seg.run(pause, 0.05f)
        assertEquals(1, done.size)
        // pre-roll + speech + a short tail
        val expected = (UtteranceSegmenter.PRE_ROLL_CHUNKS + 15 + UtteranceSegmenter.TAIL_CHUNKS) * 512
        assertEquals(expected, done[0].size)
    }

    @Test
    fun voiceRisingOverAChunkStillCountsAsAfterAPause() {
        // Real profile of a spoken "pause": 0.03 0.35 0.70 0.99 ...
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.02f)
        seg.run(1, 0.35f)
        seg.run(12, 0.95f)
        assertEquals(1, seg.run(pause, 0.02f).size)
    }

    @Test
    fun steadyMurmurBelowSpeechCountsAsAPause() {
        // Room noise or a piano hovering under the speech threshold must not block commands
        val seg = UtteranceSegmenter()
        seg.run(40, 0.35f)
        seg.run(12, 0.9f)
        assertEquals(1, seg.run(pause, 0.35f).size)
    }

    @Test
    fun quietVoiceIsSpeech() {
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.05f)
        seg.run(12, 0.45f)
        assertEquals(1, seg.run(pause, 0.05f).size)
    }

    @Test
    fun longSpeechIsDropped() {
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.05f)
        seg.run(UtteranceSegmenter.MAX_SPEECH_CHUNKS + 5, 0.9f)
        assertTrue(seg.run(pause, 0.05f).isEmpty())
    }

    @Test
    fun wordRightAfterOtherSpeechIsDropped() {
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.05f)
        seg.run(UtteranceSegmenter.MAX_SPEECH_CHUNKS + 5, 0.9f) // a long sentence
        seg.run(3, 0.05f)                                       // a short breath
        seg.run(10, 0.9f)                                       // "pause" inside the sentence
        assertTrue(seg.run(pause, 0.05f).isEmpty())
        // after a real pause, a word counts again
        seg.run(10, 0.9f)
        assertEquals(1, seg.run(pause, 0.05f).size)
    }

    @Test
    fun clickIsTooShort() {
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.05f)
        seg.run(1, 0.9f)
        assertTrue(seg.run(pause, 0.05f).isEmpty())
    }

    @Test
    fun parsesSingleCommands() {
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("pause")))
        assertEquals(listOf(VoiceCommand.PLAY), VoiceCommand.parse(words("go")))
        assertEquals(listOf(VoiceCommand.UNDO), VoiceCommand.parse(words("no")))
        assertEquals(listOf(VoiceCommand.FULLSCREEN), VoiceCommand.parse(words("full", "screen")))
    }

    @Test
    fun parsesCommandsInARow() {
        assertEquals(
            listOf(VoiceCommand.FULLSCREEN, VoiceCommand.WAIT),
            VoiceCommand.parse(words("full", "screen", "wait"))
        )
        assertEquals(
            listOf(VoiceCommand.FULLSCREEN, VoiceCommand.PLAY),
            VoiceCommand.parse(words("full", "screen", "play"))
        )
        assertEquals(List(3) { VoiceCommand.FASTER }, VoiceCommand.parse(words("faster", "faster", "faster")))
    }

    @Test
    fun rejectsSpeechThatIsNotOnlyCommands() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(emptyList()))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words(VoiceCommand.UNKNOWN_WORD)))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("play", VoiceCommand.UNKNOWN_WORD)))
        // half a phrase
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("full", "play")))
        // more than anyone says as commands
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("faster", "faster", "faster", "faster", "faster")))
    }

    @Test
    fun placeLosingCommandsNeedMoreConfidence() {
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("pause", conf = 0.6f)))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("again", conf = 0.6f)))
        assertEquals(listOf(VoiceCommand.AGAIN), VoiceCommand.parse(words("again", conf = 0.8f)))
        // one doubtful word spoils the whole utterance
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(listOf("loop" to 0.9f, "again" to 0.6f)))
    }

    @Test
    fun grammarHasEveryPhraseAndUnknown() {
        VoiceCommand.entries.flatMap { it.phrases }.forEach { assertTrue(it, "\"$it\"" in VoiceCommand.grammarJson) }
        assertTrue(VoiceCommand.UNKNOWN_WORD in VoiceCommand.grammarJson)
    }

    @Test
    fun selahIsOptionalInWordsMode() {
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("pause")))
        assertEquals(
            listOf(VoiceCommand.FULLSCREEN, VoiceCommand.WAIT),
            VoiceCommand.parse(words("selah", "full", "screen", "wait"))
        )
    }

    @Test
    fun selahModeNeedsSelahFirst() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("pause"), requireWake = true))
        assertEquals(listOf(VoiceCommand.PLAY), VoiceCommand.parse(words("sela", "play"), requireWake = true))
        assertEquals(
            listOf(VoiceCommand.FULLSCREEN, VoiceCommand.WAIT),
            VoiceCommand.parse(words("selah", "full", "screen", "wait"), requireWake = true)
        )
        // Selah only counts first
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("play", "selah"), requireWake = true))
    }

    @Test
    fun selahAlonePauses() {
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("selah"), requireWake = true))
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("selah")))
    }

    @Test
    fun doubtfulSelahIsNotAWake() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(listOf("selah" to 0.3f, "play" to 0.9f), requireWake = true))
    }

    @Test
    fun withoutPauseRuleSpeechRightAfterSpeechCounts() {
        val seg = UtteranceSegmenter().apply { requirePauseBefore = false }
        seg.run(pause, 0.05f)
        seg.run(UtteranceSegmenter.MAX_SPEECH_CHUNKS + 5, 0.9f) // talking
        seg.run(pause, 0.05f)
        seg.run(3, 0.05f)
        seg.run(20, 0.9f)                                       // "Selah, play" said soon after
        assertEquals(1, seg.run(pause, 0.05f).size)
    }

    @Test
    fun twoWordPhrasesWinOverTheirFirstWord() {
        assertEquals(listOf(VoiceCommand.BACK), VoiceCommand.parse(words("go", "back")))
        assertEquals(listOf(VoiceCommand.FORWARD), VoiceCommand.parse(words("go", "forward")))
        assertEquals(listOf(VoiceCommand.PLAY), VoiceCommand.parse(words("go")))
        assertEquals(listOf(VoiceCommand.LOOP_START), VoiceCommand.parse(words("loop", "start")))
        assertEquals(listOf(VoiceCommand.LOOP_END), VoiceCommand.parse(words("loop", "end")))
        assertEquals(listOf(VoiceCommand.LOOP), VoiceCommand.parse(words("loop")))
    }

    @Test
    fun seekWords() {
        assertEquals(listOf(VoiceCommand.BACK), VoiceCommand.parse(words("rewind")))
        assertEquals(listOf(VoiceCommand.FORWARD), VoiceCommand.parse(words("skip")))
        assertEquals(List(2) { VoiceCommand.BACK }, VoiceCommand.parse(words("back", "back")))
        // "back" is harmless enough to take on a modest confidence
        assertEquals(listOf(VoiceCommand.BACK), VoiceCommand.parse(words("back", conf = 0.55f)))
    }
}
