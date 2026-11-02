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
        assertEquals(1, seg.run(pause, 0.2f).size)
    }

    @Test
    fun quietWordPeakingBrieflyIsKept() {
        // Real profile of "loop" 30 dB down: ... 0.30 0.33 0.43 0.39 0.20 0.10 ...
        val seg = UtteranceSegmenter()
        seg.run(pause, 0.02f)
        listOf(0.29f, 0.18f, 0.25f, 0.30f, 0.33f, 0.43f, 0.39f, 0.20f).forEach { seg.feed(chunk, it) }
        assertEquals(1, seg.run(pause, 0.05f).size)
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
        assertEquals(listOf(VoiceCommand.UNDO), VoiceCommand.parse(words("undo")))
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
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(words("pause", conf = 0.72f)))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("again", conf = 0.72f)))
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
    fun selahAloneIsAWakeNotACommand() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("selah"), requireWake = true))
        assertTrue(VoiceCommand.isWakeOnly(words("sella")))
        // A clear "Selah" can come out at ~0.3, its confidence split between sound-alike spellings
        assertTrue(VoiceCommand.isWakeOnly(words("sella", conf = 0.3f)))
        assertEquals(false, VoiceCommand.isWakeOnly(words("selah", "play")))
        assertEquals(false, VoiceCommand.isWakeOnly(emptyList()))
    }

    @Test
    fun doubtfulSelahIsNotAWake() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(listOf("selah" to 0.1f, "play" to 0.9f), requireWake = true))
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
        assertEquals(listOf(VoiceCommand.FORWARD), VoiceCommand.parse(words("go", "forward")))
        assertEquals(List(2) { VoiceCommand.BACK }, VoiceCommand.parse(words("back", "back")))
        // Doubtful words are missed rather than taken for another command (seen in loud noise: 0.59-0.66)
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("wait", conf = 0.59f)))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("loop", conf = 0.66f)))
    }

    @Test
    fun soundAlikesAreNotCommands() {
        // "no" is in the grammar only to keep it from being heard as "go"
        assertTrue("no" in VoiceCommand.DISTRACTOR_WORDS)
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("no")))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(words("no", "pause")))
        // left out of the grammar entirely
        listOf("mark", "skip").forEach { assertTrue(it, "\"$it\"" !in VoiceCommand.grammarJson) }
    }

    @Test
    fun noPhraseBelongsToTwoCommands() {
        val all = VoiceCommand.entries.flatMap { it.phrases }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun shortNoiseAtTheEdgesIsTrimmed() {
        val unk = VoiceCommand.UNKNOWN_WORD
        val heard = listOf(HeardWord(unk, 1f, 0.12f), HeardWord("pause", 0.9f, 0.4f), HeardWord(unk, 1f, 0.2f))
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.parse(VoiceCommand.trimEdgeNoise(heard)))
    }

    @Test
    fun speechAroundACommandIsNotTrimmed() {
        val unk = VoiceCommand.UNKNOWN_WORD
        // "please pause" / "don't pause": a real word before the command
        val before = listOf(HeardWord(unk, 1f, 0.4f), HeardWord("pause", 0.9f, 0.4f))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(VoiceCommand.trimEdgeNoise(before)))
        // anything unknown between commands is talking
        val middle = listOf(HeardWord("loop", 0.9f, 0.3f), HeardWord(unk, 1f, 0.1f), HeardWord("play", 0.9f, 0.3f))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.parse(VoiceCommand.trimEdgeNoise(middle)))
    }

    @Test
    fun noiseIsNoNearMiss() {
        assertEquals(false, VoiceCommand.hasKnownWord(words(VoiceCommand.UNKNOWN_WORD)))
        assertEquals(false, VoiceCommand.hasKnownWord(emptyList()))
        assertTrue(VoiceCommand.hasKnownWord(words("loop", conf = 0.3f)))
    }

    @Test
    fun unsureCommandCountsWhenNothingElseCouldBeMeant() {
        // "pause" at 0.68 in a loud room; the other readings are only noise
        val alternatives = listOf(words(VoiceCommand.UNKNOWN_WORD), emptyList())
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.decide(words("pause", conf = 0.68f), alternatives))
    }

    @Test
    fun unsureCommandWithACommandRivalIsDropped() {
        // Seen in loud noise: "play" heard as "wait" 0.59, "loop end" as "loop" 0.66
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.decide(words("wait", conf = 0.59f), listOf(words("play"))))
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.decide(words("loop", conf = 0.66f), listOf(words("loop", "end"))))
        // A rival that is the same command doesn't count against it
        assertEquals(listOf(VoiceCommand.PAUSE), VoiceCommand.decide(words("pause", conf = 0.6f), listOf(words("stop"))))
    }

    @Test
    fun confidentCommandIgnoresRivals() {
        assertEquals(listOf(VoiceCommand.PLAY), VoiceCommand.decide(words("play", conf = 0.95f), listOf(words("wait"))))
    }

    @Test
    fun tooUnsureIsDroppedEvenAlone() {
        assertEquals(emptyList<VoiceCommand>(), VoiceCommand.decide(words("pause", conf = 0.4f), emptyList()))
    }
}
