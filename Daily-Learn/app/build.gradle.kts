plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.daily_learn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.daily_learn"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-march=armv8.2-a+dotprod+fp16", "-O3", "-DNDEBUG")
                cFlags += listOf("-march=armv8.2-a+dotprod+fp16", "-O3", "-DNDEBUG")
                abiFilters += "arm64-v8a"
                arguments += listOf(
                    "-DGGML_VULKAN=OFF",
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16",
                    "-DGGML_CPU_REPACK=ON"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core & UI
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.fragment)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle - ViewModel & LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.common)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // WorkManager
    implementation(libs.workmanager)

    // ML Kit OCR Text Recognition
    implementation(libs.mlkit.text.recognition)

    // ONNX Runtime for Local Embeddings
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // Testing
    testImplementation(libs.junit)
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.16.3")
    testImplementation("org.mockito:mockito-core:4.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}