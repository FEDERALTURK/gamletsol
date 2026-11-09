// Android uygulama modülü — Compose UI + CameraX önizleme + ML Kit OCR/çeviri
// + TextToSpeech. Android SDK gerektirir; bu kum havuzunda yerel olarak
// derlenmedi (bkz. kök README.md "Doğrulama durumu"), CI'da
// (.github/workflows/android.yml) doğrulanır.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.federalturk.gamletsol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.federalturk.gamletsol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // NOT (dürüstlük ilkesi, legacy projedeki ile aynı): bu ilk
            // sürümde Play/beta imzalama YOK — yalnızca debug anahtarıyla
            // imzalanıyor, gerçek yayın öncesi kalıcı bir imzalama düzeni
            // eklenmeli.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-pipeline"))
    implementation(project(":core-transport"))
    implementation(project(":feature-capture"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
