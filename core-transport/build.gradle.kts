// Saf Kotlin/JVM modülü (core-model, core-pipeline gibi) — telefon↔TV cue
// (altyazı olayı) taşıma katmanının platformdan bağımsız mantığı burada
// yaşar: CueEvent kodlama/kod açma + TCP soket sunucu/istemci.
//
// NOT (dürüstlük ilkesi / yol haritası): Bu modül, yol haritasının P3 fazı
// (G022-G030, "TV eşleştirme + güvenli taşıma + olay protokolü") için
// planlanan mimarinin sürümüdür:
// - G024 (2026-11-09): kanal artık TLS ile şifreleniyor (TvTlsIdentity,
//   bcpkix/bcprov ile üretilen kendi-imzalı sertifika). QR ile eşleşen
//   istemciler sertifika fingerprint'ini PİNLER (gerçek doğrulama); manuel
//   IP/port/kod girişiyle bağlanan istemciler fingerprint bilmediği için
//   HER sertifikaya güvenir (şifreli ama doğrulanmamış kanal — MITM'e karşı
//   korumasız, pasif dinlemeye karşı korumalı). Bkz. TvEventClient/
//   TvEventServer dokümantasyonu.
// - QR ile eşleştirme artık VAR (core-pairing, PairingQrPayload) — NSD (ağ
//   üzerinde gerçek otomatik TV keşfi) hâlâ YOK; kullanıcı QR tarayabilir
//   veya IP adresini/port'unu/kodu elle girebilir (bkz. app-mobile
//   tarafındaki "TV'ye Yansıt" menü öğesi).
// - Bu modülün kendisi gerçek bir soket üzerinden (localhost loopback)
//   gerçek birim testleriyle doğrulanmıştır — bu, sahte/mock bir testten
//   farklı olarak GERÇEK TCP+TLS davranışını (bağlantı, el sıkışma, satır
//   bazlı okuma, kapatma) sınar. Ancak gerçek bir telefon ve gerçek bir TV
//   arasında, gerçek bir Wi-Fi ağında HENÜZ denenmedi.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)
    // G024: kendi-imzalı TLS sertifikası üretimi (TvTlsIdentity). Her ikisi
    // de SAF Java'dır (Android'e özgü bir sınıf kullanmaz), bu yüzden bu
    // modülün "Android SDK gerektirmez" kuralı bozulmaz.
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Yalnızca testlerde: core-pairing + core-transport'un gerçek bir soket
    // üzerinden birlikte doğru çalıştığını kanıtlamak için (bkz.
    // TvEventPairingHandshakeTest). core-transport'un ana kodu core-pairing'e
    // BAĞIMLI DEĞİL — handshake doğrulayıcısı yalnızca bir (String) -> Boolean
    // lambda'sıdır, çağıran taraf (app-tv) hangi doğrulama mantığını
    // kullanacağına kendi karar verir.
    testImplementation(project(":core-pairing"))
}
