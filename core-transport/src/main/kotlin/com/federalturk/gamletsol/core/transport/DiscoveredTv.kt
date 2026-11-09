package com.federalturk.gamletsol.core.transport

/**
 * G025 — gerçek bir NSD/mDNS taramasının (`app-mobile`'daki
 * `TvNsdDiscoverer`, Android'e bağımlı olduğu için orada yaşar) sonucunda
 * çözümlenen (resolve edilmiş) bir TV. Bu veri sınıfının kendisi saf
 * Kotlin/JVM'dir — [TvNsdAttributes] gibi, Android'e bağımlı olmayan ortak
 * bir DTO olarak `core-transport`'ta tutulur ki ileride [TvEventClient]
 * tarafında doğrudan kullanılabilsin (host/port/pairingCode/fingerprint
 * zaten [TvEventClient.connect] ve [PairingQrPayload] ile aynı alan
 * adlarını taşıyor — tesadüf değil, aynı "bağlan" akışının girişi).
 */
data class DiscoveredTv(
    val serviceName: String,
    val host: String,
    val port: Int,
    val pairingCode: String,
    val fingerprintSha256: String,
    val protocolVersion: Int,
)
