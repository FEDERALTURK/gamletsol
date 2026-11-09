// Android kütüphane modülü — CameraX tabanlı FrameSource uygulaması. Bu
// modül Android SDK gerektirir; bu kum havuzunda yerel olarak derlenmedi
// (bkz. kök README.md "Doğrulama durumu"), CI'da (.github/workflows/android.yml)
// doğrulanır.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.federalturk.gamletsol.feature.capture"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core-pipeline"))
    implementation(project(":core-model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
