// Saf Kotlin/JVM modülü (core-model, core-transport gibi) — tek kullanımlık
// eşleştirme kodu/token üretimi ve doğrulaması burada yaşar (yol haritası
// G023, önerilen konum: `core-pairing`). Android SDK gerektirmez.
//
// NOT (dürüstlük ilkesi / yol haritası sırası): Bu, G023'ün ("Tek kullanımlık
// QR eşleştirme") kabul kriterlerini (kısa ömürlü token, tekrar kullanım
// reddi, bilinmeyen/süresi geçmiş token reddi) karşılayan BİR KATMANDIR.
// G023 başlığındaki "QR" kısmı artık TAMAMLANDI (2026-11-09): TV tarafı
// (kod + QR görseli üretimi, PairingQrPayload/QrMatrixEncoder, app-tv) VE
// telefon tarafı (kamera ile QR tarama, app-mobile QrScannerScreen) — ama
// bu QR'ın gerçek bir TV ekranından gerçek bir telefon kamerasıyla
// okunabilirliği bu kum havuzunda HİÇ denenmedi (kamera/gerçek donanım
// yok); manuel kod/alan girişi hâlâ var (G025'in gerektirdiği yedek yol).
// Ayrıca bu modül kanalı ŞİFRELEMEZ — bu token yalnızca KİMLİK DOĞRULAMA
// sağlar; G024'teki TLS/fingerprint doğrulaması hâlâ eklenmedi, kanal hâlâ
// düz TCP'dir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // G023 QR görseli (TV tarafı): zxing-core SAF Java'dır (Android
    // bağımlılığı yok) — bu yüzden bu modülün "Android SDK gerektirmez"
    // kuralını bozmadan burada kullanılabilir. QR KARESİNİ (BitMatrix)
    // üretir; piksel/Bitmap/Canvas'a çevirme app-tv'ye bırakılır (bkz.
    // QrMatrixEncoder dokümantasyonu).
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
