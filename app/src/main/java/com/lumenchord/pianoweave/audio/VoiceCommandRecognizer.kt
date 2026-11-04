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
 * Two ways at once ([CommandListener]), on one worker thread: the voice gate (Silero VAD on audio
 * cleaned by [SpeechEnhancer]) and [UtteranceSegmenter] cut short isolated utterances, which Vosk
 * reads against the ~16 word grammar; and Vosk also hears the whole stream ([CommandStream]), which
 * catches a voice from across the room that the gate misses while the piano plays.
 */
object VoiceCommandRecognizer {
    private const val TAG = "VoiceCommandRecognizer"
    // After "Selah" alone, the next utterance in this window counts as if it began with Selah:
    // people pause after the wake word, which splits "Selah ... play" into two utterances
    private const val WAKE_WINDOW_MS = 4000L
    // How long past its end a near miss waits for the stream (1 s; the stream gives a phrase ~0.6 s after)
    private const val HOLD_MISS_SAMPLES = 16000L
    // Audio chunks (32 ms) waiting for the worker; past this (a stalled worker) they're dropped and the
    // phrase in progress forgotten, so commands never come out seconds late
    private const val MAX_PENDING_CHUNKS = 30

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
    private var listener: CommandListener? = null
    private var wakeUntil = 0L

    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    // A near miss from an utterance, shown only once the stream has had its turn on the same audio
    // (worker): it often reads it as a command a moment later, and "not a command" then flashed first
    private var heldMiss: Pair<String, LongRange>? = null

    // Set on the audio thread when audio was dropped or lost, read on the worker
    @Volatile private var dropped = false

    // Touched only on the audio thread (or before it is attached)
    private val segmenter = UtteranceSegmenter()

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
        val hearing = segmenter.state == UtteranceSegmenter.State.SPEECH
        if (hearing != _isHearing.value) PianoPlayer.duckForVoice(hearing)
        _isHearing.value = hearing
        if (pending.incrementAndGet() > MAX_PENDING_CHUNKS) {
            pending.decrementAndGet()
            dropped = true
            return@ChunkListener
        }
        // Every chunk goes to the worker (the stream), and an utterance right after its last chunk
        val copy = chunk.copyOf()
        val forSession = session
        val at = VoiceDebugRecorder.chunks
        worker.execute {
            try {
                listen(copy, utterance, forSession, at)
            } finally {
                pending.decrementAndGet()
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
                VoiceDebugRecorder.start(appContext)
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
        VoiceDebugRecorder.stop()
        AcousticNoteDetector.stopVoice()
        _isHearing.value = false
        PianoPlayer.duckForVoice(false)
        _status.value = Status.OFF
        worker.execute {
            wakeUntil = 0L
            heldMiss = null
            listener?.close()
            listener = null
            model?.close()
            model = null
        }
    }

    /** Audio was dropped: an utterance in progress can't be trusted. Called on the audio thread. */
    internal fun resetStream() {
        Log.d(TAG, "Stream reset at chunk ${VoiceDebugRecorder.chunks}")
        dropped = true
        segmenter.reset()
        if (_isHearing.value) PianoPlayer.duckForVoice(false)
        _isHearing.value = false
    }

    private fun loadModel(modelDir: File): Boolean {
        if (listener != null) return true
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loaded = Model(modelDir.absolutePath)
            model = loaded
            listener = CommandListener(loaded)
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

    /** One chunk, and the utterance it ended if any, through both ways of hearing (worker). */
    private fun listen(chunk: FloatArray, utterance: FloatArray?, forSession: Int, at: Int) {
        val listener = listener ?: return
        if (forSession != session) return
        try {
            if (dropped) {
                dropped = false
                listener.reset()
            }
            listener.chunk(chunk)?.let { decide(listener, it, at) }
            if (utterance != null) listener.utterance(utterance)?.let { decide(listener, it, at) }
            heldMiss?.let { (text, span) ->
                if (listener.isTaken(span)) {
                    heldMiss = null
                } else if (listener.fed > span.last + HOLD_MISS_SAMPLES) {
                    heldMiss = null
                    _unmatched.tryEmit(text)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice command decoding failed", e)
        }
    }

    private fun decide(listener: CommandListener, phrase: CommandListener.Phrase, at: Int) {
        val heard = phrase.heard
        try {
            val words = VoiceCommand.trimEdgeNoise(heard.words)
            val now = SystemClock.elapsedRealtime()
            val awake = now < wakeUntil
            val selahMode = listeningMode == VoiceListeningMode.SELAH
            val commands = VoiceCommandDecoder.decide(heard, requireWake = selahMode && !awake)
            Log.d(
                TAG,
                "${if (phrase.fromUtterance) "Utterance" else "Stream"} at chunk $at: heard ${describe(heard.words)}" +
                    " (or ${heard.alternatives.joinToString(" / ") { alt -> alt.joinToString(" ") { it.word } }})" +
                    " -> $commands${if (awake) " (after Selah)" else ""}"
            )
            when {
                commands.isNotEmpty() -> {
                    listener.take(phrase)
                    wakeUntil = 0L
                    _commands.tryEmit(commands)
                }
                VoiceCommand.isWakeOnly(words) -> {
                    wakeUntil = now + WAKE_WINDOW_MS
                    _woke.tryEmit(Unit)
                }
                // Only near misses are worth showing: something the grammar knows was heard, and in
                // Selah mode only when addressed. Noise and other talking stay silent.
                phrase.fromUtterance && VoiceCommand.hasKnownWord(words) &&
                    (!selahMode || awake || words.first().first in VoiceCommand.WAKE_WORDS) ->
                    heldMiss = describe(heard.words) to phrase.span
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice command decoding failed", e)
        }
    }

    private fun describe(words: List<HeardWord>): String =
        if (words.isEmpty()) "nothing" else words.joinToString(" ") { "${it.word} ${"%.2f".format(it.confidence)}" }
}
