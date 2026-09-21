#include <jni.h>
#include <android/log.h>

#define LOG_TAG "audiobridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_startCapture(JNIEnv *env, jclass clazz) {
    LOGI("NativeAudioEngine: startCapture() called");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_stopCapture(JNIEnv *env, jclass clazz) {
    LOGI("NativeAudioEngine: stopCapture() called");
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_getSampleRate(JNIEnv *env, jclass clazz) {
    LOGI("NativeAudioEngine: getSampleRate() called");
    return 44100;
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_getAvailableFrames(JNIEnv *env, jclass clazz) {
    // Placeholder returning 0 to avoid loops for now
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_pianoweave_audio_NativeAudioEngine_copyLatest(JNIEnv *env, jclass clazz, jobject destination, jint frames) {
    LOGI("NativeAudioEngine: copyLatest() called with %d frames", frames);
    return 0;
}

}
