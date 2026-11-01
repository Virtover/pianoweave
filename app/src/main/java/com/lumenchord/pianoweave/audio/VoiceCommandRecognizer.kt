package com.lumenchord.pianoweave.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/**
 * Hands-free piano roll control: listens on the shared microphone capture (AcousticNoteDetector) for
 * short [VoiceCommand] words.
 *
 * Cheap by design: the voice gate (Silero VAD, already running for note detection) decides what is
 * speech, [UtteranceSegmenter] keeps only short isolated utterances, and only those reach Vosk, which
 * decodes against a ~16 word grammar on its own thread. While you just play, Vosk does nothing.
 */
object VoiceCommandRecognizer {
    private const val TAG = "VoiceCommandRecognizer"
    private const val SAMPLE_RATE = 16000f
    // Vosk takes float samples in the 16-bit range
    private const val PCM_SCALE = 32768f
    // The mic is unprocessed (no automatic gain), so a voice across the room arrives quiet: each
    // utterance is brought to this peak, by at most MAX_GAIN, before decoding
    private const val TARGET_PEAK = 0.7f
    private const val MAX_GAIN = 24f
    // Fed to Vosk in blocks so an endpoint between words in a row only closes a part, not the rest
    private const val FEED_BLOCK = 1600
    // After "Selah" alone, the next utterance in this window counts as if it began with Selah:
    // people pause after the wake word, which splits "Selah ... play" into two utterances
    private const val WAKE_WINDOW_MS = 4000L
    // Utterances waiting for the decoder; more are dropped (newest first), so a stalled or slow
    // decoder can never build up a backlog of stale commands
    private const val MAX_PENDING_DECODES = 2

    enum class Status { OFF, LOADING, LISTENING, FAILED }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status

    /** True while an utterance that could be a command is being heard (drives the mic's glow). */
    private val _isHearing = MutableStateFlow(false)
    val isHearing: StateFlow<Boolean> = _isHearing

    /** The commands of each recognized utterance, in the order said. */
    private val _commands = MutableSharedFlow<List<VoiceCommand>>(extraBufferCapacity = 4)
    val commands: SharedFlow<List<VoiceCommand>> = _commands

    /** What an utterance that gave no command sounded like to the recognizer (shown in debug builds). */
    private val _unmatched = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val unmatched: SharedFlow<String> = _unmatched

    /** "Selah" was heard alone: commands are awaited for a few seconds. */
    private val _woke = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val woke: SharedFlow<Unit> = _woke

    // Model loading and decoding run here, never on the audio thread
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "VoiceCommandThread") }

    // Touched only on the worker
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var wakeUntil = 0L

    private val pendingDecodes = java.util.concurrent.atomic.AtomicInteger(0)

    // Touched only on the audio thread (or before it is attached)
    private val segmenter = UtteranceSegmenter { reason -> Log.d(TAG, "Dropped speech: $reason") }

    @Volatile private var session = 0

    /** Changed any time; the next utterance is cut and read the new way. */
    @Volatile
    var listeningMode = VoiceListeningMode.WORDS
        set(value) {
            field = value
            segmenter.requirePauseBefore = value == VoiceListeningMode.WORDS
        }

    private val chunkListener = VoiceActivityGate.ChunkListener { chunk, probability ->
        val utterance = segmenter.feed(chunk, probability)
        _isHearing.value = segmenter.state == UtteranceSegmenter.State.SPEECH
        if (utterance != null) {
            if (pendingDecodes.incrementAndGet() > MAX_PENDING_DECODES) {
                pendingDecodes.decrementAndGet()
                Log.d(TAG, "Dropped utterance: decoder busy")
            } else {
                val forSession = session
                worker.execute {
                    try {
                        decode(utterance, forSession)
                    } finally {
                        pendingDecodes.decrementAndGet()
                    }
                }
            }
        }
    }

    /**
     * Loads the model from [modelDir] (off the main thread) and starts listening. Status goes to
     * [Status.FAILED] if the model or the microphone can't be used.
     */
    fun start(context: Context, modelDir: File) {
        val appContext = context.applicationContext
        val forSession = ++session
        _status.value = Status.LOADING
        worker.execute {
            if (forSession != session) return@execute
            if (!loadModel(modelDir)) {
                _status.value = Status.FAILED
                return@execute
            }
            if (forSession != session) return@execute
            segmenter.reset()
            VoiceActivityGate.chunkListener = chunkListener
            if (AcousticNoteDetector.startVoice(appContext) && VoiceActivityGate.isAvailable) {
                _status.value = Status.LISTENING
            } else {
                VoiceActivityGate.chunkListener = null
                AcousticNoteDetector.stopVoice()
                _status.value = Status.FAILED
            }
        }
    }

    /** Stops listening and frees the model (~tens of MB) until the next [start]. */
    fun stop() {
        session++
        VoiceActivityGate.chunkListener = null
        AcousticNoteDetector.stopVoice()
        _isHearing.value = false
        _status.value = Status.OFF
        worker.execute {
            wakeUntil = 0L
            recognizer?.close()
            recognizer = null
            model?.close()
            model = null
        }
    }

    /** Audio was dropped: an utterance in progress can't be trusted. Called on the audio thread. */
    internal fun resetStream() {
        segmenter.reset()
        _isHearing.value = false
    }

    private fun loadModel(modelDir: File): Boolean {
        if (recognizer != null) return true
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loaded = Model(modelDir.absolutePath)
            model = loaded
            recognizer = createRecognizer(loaded)
            Log.i(TAG, "Vosk model loaded from ${modelDir.name}")
            true
        } catch (e: Throwable) {
            // IOException for a broken model, UnsatisfiedLinkError if the native library is missing
            Log.e(TAG, "Vosk model failed to load", e)
            model?.close()
            model = null
            false
        }
    }

    private fun decode(utterance: FloatArray, forSession: Int) {
        val rec = recognizer ?: return
        if (forSession != session) return
        try {
            val words = recognizeWords(rec, utterance)
            val now = SystemClock.elapsedRealtime()
            val awake = now < wakeUntil
            val commands = VoiceCommand.parse(words, requireWake = listeningMode == VoiceListeningMode.SELAH && !awake)
            Log.d(TAG, "Heard ${describe(words)} -> $commands${if (awake) " (after Selah)" else ""}")
            if (forSession != session) return
            when {
                commands.isNotEmpty() -> {
                    wakeUntil = 0L
                    _commands.tryEmit(commands)
                }
                VoiceCommand.isWakeOnly(words) -> {
                    wakeUntil = now + WAKE_WINDOW_MS
                    _woke.tryEmit(Unit)
                }
                else -> _unmatched.tryEmit(describe(words))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice command decoding failed", e)
            rec.reset()
        }
    }

    /** Grammar recognizer for [VoiceCommand]s on [model]. */
    internal fun createRecognizer(model: Model): Recognizer =
        Recognizer(model, SAMPLE_RATE, VoiceCommand.grammarJson).apply { setWords(true) }

    /** The (word, confidence) pairs heard in one utterance (16 kHz, -1..1; rescaled in place). */
    internal fun recognizeWords(rec: Recognizer, utterance: FloatArray): List<Pair<String, Float>> {
        var peak = 0f
        for (s in utterance) peak = maxOf(peak, kotlin.math.abs(s))
        val scale = PCM_SCALE * if (peak > 0f) minOf(TARGET_PEAK / peak, MAX_GAIN) else 1f
        for (i in utterance.indices) utterance[i] *= scale

        val words = mutableListOf<Pair<String, Float>>()
        var off = 0
        while (off < utterance.size) {
            val n = minOf(FEED_BLOCK, utterance.size - off)
            // An endpoint (a pause between words in a row) closes a part: keep its words, go on
            if (rec.acceptWaveForm(utterance.copyOfRange(off, off + n), n)) words += parseWords(rec.result)
            off += n
        }
        words += parseWords(rec.finalResult)
        return words
    }

    /** The commands in one utterance, or empty (see [VoiceCommand.parse]). */
    internal fun recognize(rec: Recognizer, utterance: FloatArray, requireWake: Boolean = false): List<VoiceCommand> =
        VoiceCommand.parse(recognizeWords(rec, utterance), requireWake)

    private fun describe(words: List<Pair<String, Float>>): String =
        if (words.isEmpty()) "nothing" else words.joinToString(" ") { (w, c) -> "$w ${"%.2f".format(c)}" }

    /** (word, confidence) pairs of a Vosk result with words enabled. */
    private fun parseWords(json: String): List<Pair<String, Float>> {
        val result = JsonParser.parseString(json).asJsonObject.getAsJsonArray("result") ?: return emptyList()
        return result.map { element ->
            val word = element.asJsonObject
            word.get("word").asString to word.get("conf").asFloat
        }
    }
}
