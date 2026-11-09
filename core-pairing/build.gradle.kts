// Saf Kotlin/JVM modülü (core-model, core-transport gibi) — tek kullanımlık
// eşleştirme kodu/token üretimi ve doğrulaması burada yaşar (yol haritası
// G023, önerilen konum: `core-pairing`). Android SDK gerektirmez.
//
// NOT (dürüstlük ilkesi / yol haritası sırası): Bu, G023'ün ("Tek kullanımlık
// QR eşleştirme") kabul kriterlerini (kısa ömürlü token, tekrar kullanım
// reddi, bilinmeyen/süresi geçmiş token reddi) karşılayan BİR KATMANDIR —
// ama G023 başlığındaki "QR" kısmının YALNIZCA TV tarafı (kod + QR görseli
// üretimi, app-tv) yapıldı; telefon tarafında KAMERA İLE QR TARAMA henüz
// eklenmedi, kullanıcı kodu elle girer (bu, zaten G025'in gerektirdiği
// manuel yedek yoldur). Ayrıca bu modül kanalı ŞİFRELEMEZ — bu token yalnızca
// KİMLİK DOĞRULAMA sağlar; G024'teki TLS/fingerprint doğrulaması hâlâ
// eklenmedi, kanal hâlâ düz TCP'dir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.junit)
}
