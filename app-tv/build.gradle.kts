// Android TV / Google TV uygulama modülü — G001-G006 (P0) kapısı için
// kanıt APK'sı. Herhangi bir standart Android TV veya Google TV cihazında
// çalışması hedeflenir (Profilo'ya veya başka bir markaya özel bir API
// KULLANILMAZ — yalnızca AOSP'nin genel `android.media.tv.*` API'leri).
//
// NOT (dürüstlük ilkesi): Bu modül bu kum havuzunda yerel olarak
// DERLENMEDİ (Android SDK yok, bkz. kök README.md "Doğrulama durumu").
// CI (.github/workflows/android.yml) ilk gerçek derleme/lint doğrulamasını
// yapar; gerçek bir Android TV/Google TV cihazında TvInputManager/TvView
// davranışı (G002-G003) HİÇ test edilmedi.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.federalturk.gamletsol.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.federalturk.gamletsol.tv"
        // Android TV, min API 21'den beri var; Google TV cihazları da dahil
        // geniş uyumluluk için TV-spesifik minSdk'yı ana uygulamayla aynı
        // (26) tutuyoruz — daha eski TV'leri hedeflemek ayrı bir karar.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-probe"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // NOT (dürüstlük ilkesi): bu bir PROBE/kanıt APK'sı, Play'e
            // yayınlanmayacak — debug anahtarıyla imzalanıyor.
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
