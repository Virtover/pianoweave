package com.lumenchord.pianoweave.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
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
    private var decoder: VoiceCommandDecoder? = null
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
            decoder?.close()
            decoder = null
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
        if (decoder != null) return true
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loaded = Model(modelDir.absolutePath)
            model = loaded
            decoder = VoiceCommandDecoder(loaded)
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
        val decoder = decoder ?: return
        if (forSession != session) return
        try {
            val started = SystemClock.elapsedRealtime()
            val heard = decoder.hear(utterance)
            val words = VoiceCommand.trimEdgeNoise(heard.words)
            val now = SystemClock.elapsedRealtime()
            val awake = now < wakeUntil
            val selahMode = listeningMode == VoiceListeningMode.SELAH
            val commands = decoder.decide(heard, requireWake = selahMode && !awake)
            Log.d(
                TAG,
                "Heard ${describe(heard.words)} (or ${heard.alternatives.joinToString(" / ") { alt -> alt.joinToString(" ") { it.word } }})" +
                    " -> $commands${if (awake) " (after Selah)" else ""} in ${now - started} ms"
            )
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
                // Only near misses are worth showing: something the grammar knows was heard, and in
                // Selah mode only when addressed. Noise and other talking stay silent.
                VoiceCommand.hasKnownWord(words) &&
                    (!selahMode || awake || words.first().first in VoiceCommand.WAKE_WORDS) ->
                    _unmatched.tryEmit(describe(heard.words))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice command decoding failed", e)
        }
    }

    private fun describe(words: List<HeardWord>): String =
        if (words.isEmpty()) "nothing" else words.joinToString(" ") { "${it.word} ${"%.2f".format(it.confidence)}" }
}
