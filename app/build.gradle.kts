import java.util.Properties

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.lumenchord.pianoweave"

    ndkVersion = "30.0.16248370"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lumenchord.pianoweave"

        minSdk = 26
        targetSdk = 37

        versionCode = 3
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

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
//            ndk {
//                //noinspection ChromeOsAbiSupport
//                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
//            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.fragment.ktx)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.fluidsynth.kmp)
    implementation(libs.jtransforms)

    implementation(libs.litert)
    implementation(libs.litert.gpu)

//    implementation("com.google.android.filament:filament-android:1.77.1")
//    implementation("io.github.sceneview:sceneview:4.47.0")

    implementation(libs.billing.ktx)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register("downloadModel") {
    val modelUrl = "https://storage.googleapis.com/magentadata/models/onsets_frames_transcription/tflite/onsets_frames_wavinput_no_offset_uni.tflite"
    val targetFile = file("src/main/assets/onsets_frames_wavinput_no_offset_uni.tflite")
    outputs.file(targetFile)
    doLast {
        if (!targetFile.exists()) {
            targetFile.parentFile.mkdirs()
            println("Downloading model...")
            ant.invokeMethod("get", mapOf("src" to modelUrl, "dest" to targetFile))
            println("Download complete.")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadModel")
}
