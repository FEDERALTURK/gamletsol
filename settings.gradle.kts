pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gamletsol"

// Modül yapısı, yol haritası belgesindeki "Önerilen proje/dosya yapısı"
// bölümünü izler. İlk sürümde yalnız gerçekten uygulanan modüller eklendi;
// app-tv / receiver-web / source-playstation / source-xbox / backend /
// data-local / feature-session, ilgili karar kapıları onaylandığında veya
// ihtiyaç ortaya çıktığında (TV eşleştirme → P3, kalıcı ayar/sözlük → P2/P4)
// eklenecek.
include(":app-mobile")
include(":core-model")
include(":core-pipeline")
include(":feature-capture")
