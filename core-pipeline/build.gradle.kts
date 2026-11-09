// Saf Kotlin/JVM modülü (core-model gibi) — kare→OCR→çeviri→cue hattının
// platformdan bağımsız mantığı burada yaşar; Android'e özgü kısımlar
// (CameraX, ML Kit) platform modüllerinde bu modülün arayüzlerini uygular.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
