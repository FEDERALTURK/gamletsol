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
        // G039: versionCode/versionName artık kök build.gradle.kts'teki
        // paylaşılan CI-farkında mantıktan geliyor — her CI APK'sı hangi
        // GitHub Actions çalışmasından/commit'ten geldiğini kendi üstünde
        // taşıyor (bkz. kök build.gradle.kts KDoc'u).
        versionCode = rootProject.extra["gamletsolVersionCode"] as Int
        versionName = "0.2.0+" + rootProject.extra["gamletsolBuildMetadata"]
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
            // G024: core-transport artık Bouncy Castle'a (bcprov/bcpkix/bcutil)
            // bağımlı; üçü de aynı yoldaki bir OSGi manifest'ini taşıyor —
            // bu, kod DEĞİL, sadece OSGi meta verisi, dışlamak güvenli.
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-pairing"))
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
    // G023/G025: TV'de gösterilen eşleştirme QR'ını kamerayla okumak için
    // (bkz. ui/QrScannerScreen.kt) — bu, kodu elle yazma zorunluluğunu
    // (eski MVP sınırı) kaldırır; manuel alan hâlâ var, bu yalnızca bir
    // kısayoldur.
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
