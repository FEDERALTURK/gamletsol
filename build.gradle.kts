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

// G039 ("CI ve sürüm paketleme"): her CI derlemesinin ürettiği APK'nın
// HANGİ GitHub Actions çalışmasından ve hangi commit'ten geldiğini kendi
// üstünde taşıması için tek, paylaşılan bir versiyon kaynağı. app-mobile
// ve app-tv build.gradle.kts dosyaları bunu `rootProject.extra` üzerinden
// okuyor — iki modülde ayrı ayrı (ve tutarsız biçimde) tekrarlanmıyor.
//
// `GITHUB_RUN_NUMBER` yalnızca Actions içinde ayarlıdır ve her çalışmada
// KESİNLİKLE artar (bu depodaki workflow'lar için), bu yüzden versionCode
// olarak kullanmak Android'in "her yeni APK, öncekinden büyük bir
// versionCode taşımalı" kuralını doğal olarak karşılıyor. Yerel bir
// derlemede (CI dışında) bu değişken yok — o durumda versionCode 1'de
// sabit kalır (öncekiyle aynı, zaten bu kum havuzunda/yerel ortamda hiç
// derlenmedi, bkz. kök README.md "Doğrulama durumu").
val gamletsolCiRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Git kısa SHA'sını almayı dener; `git` yoksa veya depo bir git çalışma
// kopyası değilse (örn. kullanıcının yerel senkron klasörü) SESSİZCE
// "local" döner — build'i hiçbir zaman bu yüzden kırmaz.
val gamletsolGitShortSha: String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotBlank()) output else "local"
} catch (e: Exception) {
    "local"
}

extra["gamletsolVersionCode"] = gamletsolCiRunNumber ?: 1
extra["gamletsolBuildMetadata"] =
    if (gamletsolCiRunNumber != null) "ci$gamletsolCiRunNumber.$gamletsolGitShortSha" else "local.$gamletsolGitShortSha"
