// app/build.gradle.kts

plugins {
    // Versión del Android Gradle plugin
    id("com.android.application") version "8.5.0"

    // Kotlin Android (Kotlin DSL)
    id("org.jetbrains.kotlin.android") version "1.9.10"

    // Safe Args (Navigation)
    id("androidx.navigation.safeargs") version "2.5.0"

    // (Opcional) plugin para descargar modelos
    id("de.undercouch.download") version "4.1.2"
}

android {
    namespace = "com.example.visionfusion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.visionfusion"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        // Si quieres usar funciones de Java 1.8 en tu código Java
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Config para Kotlin (JVM target) + Compose
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        // Activamos Data Binding, View Binding y Compose
        dataBinding = true
        viewBinding = true
        compose = true
    }

    // Ajustamos el compilador de Compose a la versión que corresponde a la BOM
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3" // BOM 2023.09.00 -> Compiler 1.5.3
    }

    // Para que Gradle no comprima archivos .tflite
    androidResources {
        noCompress += "tflite"
    }

    buildTypes {
        getByName("release") {
            // Si no quieres ofuscación, pon en false
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// (Opcional) Si usarás un script para descargar modelos desde 'download_models.gradle.kts':
// val assetDir = "$projectDir/src/main/assets"
// apply(from = "download_models.gradle.kts")

// --- Variables de versión para otras dependencias ---
val navVersion = "2.5.0"
val cameraXVersion = "1.1.0"

// En este ejemplo ya NO usamos 'composeVersion' porque lo gestiona la BOM.
dependencies {
    // Kotlin + Corrutinas
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")

    // AndroidX y UI
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    implementation("androidx.fragment:fragment-ktx:1.5.4")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // CameraX
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-extensions:$cameraXVersion")

    // Window Manager (opcional)
    implementation("androidx.window:window:1.0.0")

    // MediaPipe Tasks (Visión)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Tests (opcionales)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    // ========== JETPACK COMPOSE (usando la BOM) ==========
    // 1. Declaramos la BOM:
    val composeBom = platform("androidx.compose:compose-bom:2023.09.00")
    implementation(composeBom)

    // 2. Ya no precisamos versión en cada dependencia:
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Previews, tooling:
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
