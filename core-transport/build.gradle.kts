// Saf Kotlin/JVM modülü (core-model, core-pipeline gibi) — telefon↔TV cue
// (altyazı olayı) taşıma katmanının platformdan bağımsız mantığı burada
// yaşar: CueEvent kodlama/kod açma + TCP soket sunucu/istemci.
//
// NOT (dürüstlük ilkesi / yol haritası): Bu modül, yol haritasının P3 fazı
// (G022-G030, "TV eşleştirme + güvenli taşıma + olay protokolü") için
// planlanan mimarinin İLK/MVP sürümüdür:
// - Şifreleme YOK (düz TCP metin protokolü) — G023-G024'teki TLS + eşleştirme
//   token'ı HENÜZ eklenmedi. Yalnızca ev içi/aynı Wi-Fi ağı için düşünülmeli.
// - QR ile eşleştirme / NSD (ağ üzerinde otomatik TV keşfi) YOK — kullanıcı
//   TV'nin IP adresini/port'unu elle girer (bkz. app-mobile tarafındaki
//   "TV'ye Yansıt" menü öğesi).
// - Bu modülün kendisi gerçek bir soket üzerinden (localhost loopback)
//   gerçek birim testleriyle doğrulanmıştır — bu, sahte/mock bir testten
//   farklı olarak GERÇEK TCP davranışını (bağlantı, satır bazlı okuma,
//   kapatma) sınar. Ancak gerçek bir telefon ve gerçek bir TV arasında,
//   gerçek bir Wi-Fi ağında HENÜZ denenmedi.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
