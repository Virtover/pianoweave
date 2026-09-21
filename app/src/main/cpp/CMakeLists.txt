cmake_minimum_required(VERSION 3.22.1)

project("native-audio-bridge")

add_library(native-audio-bridge SHARED native-audio-bridge.cpp)

target_link_libraries(
        native-audio-bridge
        android
        log
        aaudio
)
