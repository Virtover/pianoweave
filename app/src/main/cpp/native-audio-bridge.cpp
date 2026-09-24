#include <jni.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <vector>
#include <atomic>
#include <mutex>
#include <algorithm>
#include <cstring>
#include <memory>

#define LOG_TAG "audiobridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    // Increased to 256k to comfortably hold >2 seconds of 44.1kHz audio
    constexpr int32_t RING_BUFFER_SIZE = 131072;
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
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_initialize(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        aaudio_stream_state_t state = AAudioStream_getState(audioStream);
        if (state != AAUDIO_STREAM_STATE_DISCONNECTED && state != AAUDIO_STREAM_STATE_CLOSED && state != AAUDIO_STREAM_STATE_CLOSING) {
            return JNI_TRUE;
        }
        AAudioStream_close(audioStream);
        audioStream = nullptr;
    }

    AAudioStreamBuilder *builder;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) return JNI_FALSE;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setSampleRate(builder, 44100);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, nullptr);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, nullptr);

    result = AAudioStreamBuilder_openStream(builder, &audioStream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
        audioStream = nullptr;
        return JNI_FALSE;
    }

    LOGI("NativeAudioEngine: initialize() successful");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_startCapture(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream == nullptr) {
        // Attempt re-initialization if stream was closed/disconnected
        AAudioStreamBuilder *builder;
        aaudio_result_t result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) return JNI_FALSE;

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(builder, 1);
        AAudioStreamBuilder_setSampleRate(builder, 44100);
        AAudioStreamBuilder_setDataCallback(builder, dataCallback, nullptr);
        AAudioStreamBuilder_setErrorCallback(builder, errorCallback, nullptr);

        result = AAudioStreamBuilder_openStream(builder, &audioStream);
        AAudioStreamBuilder_delete(builder);

        if (result != AAUDIO_OK) {
            LOGE("Failed to reopen AAudio stream in startCapture: %s", AAudio_convertResultToText(result));
            audioStream = nullptr;
            return JNI_FALSE;
        }
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
Java_com_example_pianoweave_audio_NativeAudioEngine_stopCapture(JNIEnv *env, jclass clazz) {
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
Java_com_example_pianoweave_audio_NativeAudioEngine_getSampleRate(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        return AAudioStream_getSampleRate(audioStream);
    }
    return 44100;
}

JNIEXPORT jlong JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_getAvailableFrames(JNIEnv *env, jclass clazz) {
    return static_cast<jlong>(writeIndex.load(std::memory_order_acquire));
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_copyLatest(JNIEnv *env, jclass clazz, jobject destination, jint frames, jlong startIndex) {
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
Java_com_example_pianoweave_audio_NativeAudioEngine_cleanup(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (audioStream != nullptr) {
        AAudioStream_close(audioStream);
        audioStream = nullptr;
        LOGI("NativeAudioEngine: cleanup() successful");
    }
}

}
