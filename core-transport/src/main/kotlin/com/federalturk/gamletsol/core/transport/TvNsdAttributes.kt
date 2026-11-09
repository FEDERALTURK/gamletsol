package com.federalturk.gamletsol.core.transport

/**
 * G025 ("otomatik keşif") — Android'in gerçek `NsdManager`'ı (mDNS/DNS-SD)
 * ile reklam edilen bir TV'nin TXT kayıtlarına gömülecek veri. Bu dosyanın
 * KENDİSİ saf Kotlin/JVM'dir (Android bağımlılığı YOK) — `core-transport`'un
 * "Android SDK'sız test edilebilir" kuralını bozmaz. Gerçek
 * `NsdManager.registerService`/`discoverServices`/`resolveService`
 * çağrıları yalnızca `app-tv` (reklam) ve `app-mobile`'da (keşif/çözümleme)
 * yaşar — bu sınıf sadece o çağrıların okuyacağı/yazacağı
 * `Map<String, ByteArray>` TXT kayıt biçimini tanımlar ve dönüştürür.
 *
 * NOT (tasarım kararı): G025, QR'ı (G023) VEYA manuel girişi (roadmap'in
 * hep istediği yedek yol) ORTADAN KALDIRMAZ — ikisi de kalır. NSD, "aynı
 * yerel ağdaki TV'leri otomatik listele" imkanı EKLER; kullanıcı hâlâ
 * QR tarayabilir ya da elle girebilir (örn. NSD engellenmiş bir ağda,
 * ya da mDNS'i desteklemeyen bir router'da).
 *
 * NOT (gizlilik/güvenlik, dürüstlük ilkesi): mDNS TXT kayıtları yerel ağda
 * HERKESE açıktır (QR'ı görmek de zaten aynı güven seviyesindedir — fiziksel
 * erişim/aynı Wi-Fi gerekir). Eşleştirme kodu + TLS fingerprint'i burada
 * taşınması, G024'ün MITM korumasını AZALTMAZ: pinning hâlâ fingerprint
 * karşılaştırmasıyla yapılır, NSD sadece bu değerleri elle/QR ile taşıma
 * ZORUNLULUĞUNU kaldırır.
 */
data class TvNsdAttributes(
    val pairingCode: String,
    val fingerprintSha256: String,
    val protocolVersion: Int,
) {
    /**
     * [fromTxtRecordMap]'in (byte dizisi tabanlı) karşılığı — birim testlerde
     * round-trip kontrolü için kullanılır.
     *
     * NOT (gerçek Android API sınırı, CI'da bulundu): gerçek
     * `NsdServiceInfo.setAttribute(String, byte[])` çağrısı yalnızca API
     * 34+'te var; bu uygulamanın minSdk'si (26) daha düşük olduğu için
     * `app-tv`'deki [TvNsdAdvertiser] bu haritayı DEĞİL, aşağıdaki
     * [toTxtRecordStringMap]'i (her API seviyesinde var olan
     * `setAttribute(String, String)` çağrısıyla) kullanır. Yine de
     * `NsdServiceInfo.getAttributes()` TÜM API seviyelerinde her zaman
     * `Map<String, byte[]>` döner (TXT kayıtları ağda zaten bayt dizisidir),
     * bu yüzden [fromTxtRecordMap] (çözme tarafı) değişmeden kalır.
     */
    fun toTxtRecordMap(): Map<String, ByteArray> = mapOf(
        KEY_PAIRING_CODE to pairingCode.toByteArray(Charsets.UTF_8),
        KEY_FINGERPRINT to fingerprintSha256.toByteArray(Charsets.UTF_8),
        KEY_PROTOCOL_VERSION to protocolVersion.toString().toByteArray(Charsets.UTF_8),
    )

    /**
     * Gerçek `NsdServiceInfo.setAttribute(String, String)` çağrılarında
     * kullanılacak harita — [TvNsdAdvertiser]'ın (`app-tv`) gerçekte
     * çağırdığı yol budur (bkz. [toTxtRecordMap]'in dokümantasyonu).
     */
    fun toTxtRecordStringMap(): Map<String, String> = mapOf(
        KEY_PAIRING_CODE to pairingCode,
        KEY_FINGERPRINT to fingerprintSha256,
        KEY_PROTOCOL_VERSION to protocolVersion.toString(),
    )

    companion object {
        /** DNS-SD servis türü — hem app-tv'nin reklamında hem app-mobile'ın taramasında aynı sabit kullanılmalı. */
        const val SERVICE_TYPE = "_gamletsol._tcp."

        const val KEY_PAIRING_CODE = "code"
        const val KEY_FINGERPRINT = "fp"
        const val KEY_PROTOCOL_VERSION = "v"

        /**
         * [toTxtRecordMap]'in tersi — gerçek `NsdServiceInfo.attributes`'tan
         * (bir `resolveService` çağrısı SONRASI) okunur. Herhangi bir alan
         * eksik/boşsa (eski bir sürüm, bozuk/ilgisiz bir mDNS kaydı, ya da
         * henüz çözümlenmemiş bir servis) `null` döner — çağıran kod bunu
         * "bu bir GAMLETSOL TV'si değil/henüz hazır değil" olarak ele almalı.
         */
        fun fromTxtRecordMap(attributes: Map<String, ByteArray?>): TvNsdAttributes? {
            val code = attributes[KEY_PAIRING_CODE]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
                ?: return null
            val fingerprint = attributes[KEY_FINGERPRINT]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
                ?: return null
            val version = attributes[KEY_PROTOCOL_VERSION]?.toString(Charsets.UTF_8)?.toIntOrNull()
                ?: return null
            return TvNsdAttributes(pairingCode = code, fingerprintSha256 = fingerprint, protocolVersion = version)
        }
    }
}
