#include <jni.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <vector>
#include <atomic>
#include <algorithm>
#include <cstring>

#define LOG_TAG "audiobridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    constexpr int32_t RING_BUFFER_SIZE = 65536;
    float ringBuffer[RING_BUFFER_SIZE];
    std::atomic<int64_t> writeIndex{0};
    AAudioStream *audioStream = nullptr;

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

    void errorCallback(AAudioStream *stream, void *userData, aaudio_result_t error) {
        LOGE("AAudio stream error: %s", AAudio_convertResultToText(error));
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_startCapture(JNIEnv *env, jclass clazz) {
    if (audioStream != nullptr) return JNI_TRUE;

    AAudioStreamBuilder *builder;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) return JNI_FALSE;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
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

    result = AAudioStream_requestStart(audioStream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %s", AAudio_convertResultToText(result));
        AAudioStream_close(audioStream);
        audioStream = nullptr;
        return JNI_FALSE;
    }

    writeIndex.store(0, std::memory_order_relaxed);
    LOGI("NativeAudioEngine: startCapture() successful");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_stopCapture(JNIEnv *env, jclass clazz) {
    if (audioStream != nullptr) {
        AAudioStream_requestStop(audioStream);
        AAudioStream_close(audioStream);
        audioStream = nullptr;
        LOGI("NativeAudioEngine: stopCapture() successful");
    }
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_getSampleRate(JNIEnv *env, jclass clazz) {
    if (audioStream != nullptr) {
        return AAudioStream_getSampleRate(audioStream);
    }
    return 44100;
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_getAvailableFrames(JNIEnv *env, jclass clazz) {
    return static_cast<jint>(writeIndex.load(std::memory_order_acquire));
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_copyLatest(JNIEnv *env, jclass clazz, jobject destination, jint frames) {
    if (destination == nullptr || frames <= 0) return 0;

    float *destPtr = static_cast<float *>(env->GetDirectBufferAddress(destination));
    if (destPtr == nullptr) return 0;

    int64_t currentWriteIndex = writeIndex.load(std::memory_order_acquire);
    int64_t startReadIndex = currentWriteIndex - frames;
    if (startReadIndex < 0) startReadIndex = 0; // Or handle underflow by padding with zeros

    for (int32_t i = 0; i < frames; ++i) {
        destPtr[i] = ringBuffer[(startReadIndex + i) % RING_BUFFER_SIZE];
    }

    return static_cast<jint>(frames);
}

}
