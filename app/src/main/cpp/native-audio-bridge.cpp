#include <jni.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <vector>
#include <atomic>
#include <mutex>
#include <algorithm>
#include <cstring>
#include <memory>
#include <dlfcn.h>

#define LOG_TAG "audiobridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    // 131072 frames = ~2.7 s at 48 kHz. Keep NATIVE_RING_FRAMES in AcousticNoteDetector in sync.
    constexpr int32_t RING_BUFFER_SIZE = 131072;

    // 48 kHz is the native rate of almost every Android device, so the input stays on the
    // low-latency (fast / MMAP) path and no system resampler runs. Downsampling to the model's
    // 16 kHz happens in AcousticNoteDetector with a proper anti-alias filter.
    constexpr int32_t CAPTURE_SAMPLE_RATE = 48000;

    // AAudio input presets (values from AAudio.h; defined here so the code builds with minSdk 26).
    // UNPROCESSED (API 29+) disables OEM noise suppression / AGC that is tuned for speech and
    // attenuates sustained piano tones. If the device rejects it, the default preset is used.
    constexpr int32_t INPUT_PRESET_UNPROCESSED = 9;
    constexpr int32_t INPUT_PRESET_DEFAULT = -1;  // do not call setInputPreset
    float ringBuffer[RING_BUFFER_SIZE];
    std::atomic<int64_t> writeIndex{0};
    AAudioStream *audioStream = nullptr;
    std::mutex streamMutex;

    void errorCallback(AAudioStream *stream, void *userData, aaudio_result_t error) {
        LOGE("AAudio stream error: %s (%d)", AAudio_convertResultToText(error), error);
        if (error == AAUDIO_ERROR_DISCONNECTED) {
            std::lock_guard<std::mutex> lock(streamMutex);
            if (audioStream == stream) {
                AAudioStream_close(stream);
                audioStream = nullptr;
                LOGI("AAudio stream closed due to disconnection (e.g. phone call interrupt).");
            }
        }
    }

    aaudio_data_callback_result_t dataCallback(
            AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames) {

        float *inputData = static_cast<float *>(audioData);
        int64_t currentWriteIndex = writeIndex.load(std::memory_order_relaxed);

        for (int32_t i = 0; i < numFrames; ++i) {
            ringBuffer[(currentWriteIndex + i) % RING_BUFFER_SIZE] = inputData[i];
        }

        writeIndex.store(currentWriteIndex + numFrames, std::memory_order_release);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    using SetInputPresetFn = void (*)(AAudioStreamBuilder *, int32_t);

    // AAudioStreamBuilder_setInputPreset exists from API 28; resolve it at runtime so minSdk 26 still loads.
    SetInputPresetFn setInputPresetFn() {
        static SetInputPresetFn fn = []() -> SetInputPresetFn {
            void *lib = dlopen("libaaudio.so", RTLD_NOW);
            if (lib == nullptr) return nullptr;
            return reinterpret_cast<SetInputPresetFn>(dlsym(lib, "AAudioStreamBuilder_setInputPreset"));
        }();
        return fn;
    }

    // Caller must hold streamMutex.
    aaudio_result_t openStreamWithPreset(int32_t preset) {
        AAudioStreamBuilder *builder;
        aaudio_result_t result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) return result;

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(builder, 1);
        AAudioStreamBuilder_setSampleRate(builder, CAPTURE_SAMPLE_RATE);
        AAudioStreamBuilder_setDataCallback(builder, dataCallback, nullptr);
        AAudioStreamBuilder_setErrorCallback(builder, errorCallback, nullptr);
        if (preset != INPUT_PRESET_DEFAULT) {
            SetInputPresetFn fn = setInputPresetFn();
            if (fn == nullptr) {
                AAudioStreamBuilder_delete(builder);
                return AAUDIO_ERROR_UNIMPLEMENTED;
            }
            fn(builder, preset);
        }

        AAudioStream *stream = nullptr;
        result = AAudioStreamBuilder_openStream(builder, &stream);
        AAudioStreamBuilder_delete(builder);
        if (result == AAUDIO_OK) audioStream = stream;
        return result;
    }

    // Caller must hold streamMutex. Tries UNPROCESSED first, then the default preset.
    aaudio_result_t openStream() {
        aaudio_result_t result = openStreamWithPreset(INPUT_PRESET_UNPROCESSED);
        if (result == AAUDIO_OK) {
            LOGI("AAudio input opened: preset=UNPROCESSED, rate=%d, perfMode=%d",
                 AAudioStream_getSampleRate(audioStream), AAudioStream_getPerformanceMode(audioStream));
            return result;
        }
        LOGI("UNPROCESSED preset unavailable (%s), falling back to default preset",
             AAudio_convertResultToText(result));

        result = openStreamWithPreset(INPUT_PRESET_DEFAULT);
        if (result != AAUDIO_OK) {
            LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
            audioStream = nullptr;
            return result;
        }
        LOGI("AAudio input opened: preset=default, rate=%d, perfMode=%d",
             AAudioStream_getSampleRate(audioStream), AAudioStream_getPerformanceMode(audioStream));
        return result;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_initialize(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        aaudio_stream_state_t state = AAudioStream_getState(audioStream);
        if (state != AAUDIO_STREAM_STATE_DISCONNECTED && state != AAUDIO_STREAM_STATE_CLOSED && state != AAUDIO_STREAM_STATE_CLOSING) {
            return JNI_TRUE;
        }
        AAudioStream_close(audioStream);
        audioStream = nullptr;
    }

    if (openStream() != AAUDIO_OK) return JNI_FALSE;

    LOGI("NativeAudioEngine: initialize() successful");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_startCapture(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream == nullptr) {
        // Attempt re-initialization if stream was closed/disconnected
        if (openStream() != AAUDIO_OK) return JNI_FALSE;
    }

    aaudio_stream_state_t state = AAudioStream_getState(audioStream);
    if (state == AAUDIO_STREAM_STATE_STARTED) return JNI_TRUE;

    aaudio_result_t result = AAudioStream_requestStart(audioStream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %s", AAudio_convertResultToText(result));
        return JNI_FALSE;
    }

    LOGI("NativeAudioEngine: startCapture() successful");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_stopCapture(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        aaudio_stream_state_t state = AAudioStream_getState(audioStream);
        if (state == AAUDIO_STREAM_STATE_STARTED || state == AAUDIO_STREAM_STATE_STARTING) {
            AAudioStream_requestStop(audioStream);
            LOGI("NativeAudioEngine: stopCapture() requested");
        }
    }
}

JNIEXPORT jint JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_getSampleRate(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        return AAudioStream_getSampleRate(audioStream);
    }
    return CAPTURE_SAMPLE_RATE;
}

JNIEXPORT jlong JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_getAvailableFrames(JNIEnv *env, jclass clazz) {
    return static_cast<jlong>(writeIndex.load(std::memory_order_acquire));
}

JNIEXPORT jint JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_copyLatest(JNIEnv *env, jclass clazz, jobject destination, jint frames, jlong startIndex) {
    if (destination == nullptr || frames <= 0) return 0;

    float *destPtr = static_cast<float *>(env->GetDirectBufferAddress(destination));
    if (destPtr == nullptr) return 0;

    int64_t currentWriteIndex = writeIndex.load(std::memory_order_acquire);
    const int64_t availableFrames = currentWriteIndex - startIndex;
    if (availableFrames <= 0) return 0;

    const int32_t framesToCopy = static_cast<int32_t>(std::min<int64_t>(frames, availableFrames));

    for (int32_t i = 0; i < frames; ++i) {
        destPtr[i] = ringBuffer[(startIndex + i) % RING_BUFFER_SIZE];
    }

    return framesToCopy;
}

JNIEXPORT void JNICALL
Java_com_lumenchord_pianoweave_audio_NativeAudioEngine_cleanup(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        AAudioStream_close(audioStream);
        audioStream = nullptr;
        LOGI("NativeAudioEngine: cleanup() successful");
    }
}

}
