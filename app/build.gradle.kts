plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.pianoweave"

    ndkVersion = "30.0.16248370"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pianoweave"

        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
    }

    buildTypes {
        release {
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.fluidsynth.kmp)
    implementation(libs.jtransforms)
    
    // TensorFlow Lite / Basic Pitch dependencies
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register("downloadBasicPitchModel") {
    val modelUrl = "https://raw.githubusercontent.com/spotify/basic-pitch/main/basic_pitch/saved_models/icassp_2022/nmp.tflite"
    val targetFile = file("src/main/assets/basic_pitch.tflite")
    outputs.file(targetFile)
    doLast {
        if (!targetFile.exists()) {
            targetFile.parentFile.mkdirs()
            println("Downloading Spotify Basic Pitch model...")
            ant.invokeMethod("get", mapOf("src" to modelUrl, "dest" to targetFile))
            println("Download complete.")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadBasicPitchModel")
}
