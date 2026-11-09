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
// bölümünü izler. `app-tv`, güncel (ikinci) yol haritasının P0 önceliği
// olan G001-G006 (gerçek TV donanımında HDMI üstü yazı kanıtı) için
// eklendi — bkz. docs/roadmap/tasks/todo.md P0 bölümü ve
// docs/compatibility/hardware.md. receiver-web / source-playstation /
// source-xbox / backend / data-local / feature-session, ilgili karar
// kapıları onaylandığında veya ihtiyaç ortaya çıktığında (TV eşleştirme →
// P3, kalıcı ayar/sözlük → P2/P4) eklenecek.
include(":app-mobile")
include(":app-tv")
include(":core-model")
include(":core-pipeline")
include(":core-transport")
include(":feature-capture")
