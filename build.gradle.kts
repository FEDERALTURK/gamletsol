// Kök build.gradle.kts — yalnızca eklentileri tüm alt modüller için
// `apply false` ile bildirir; gerçek uygulama her modülün kendi
// build.gradle.kts dosyasındadır.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
