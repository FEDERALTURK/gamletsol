package com.federalturk.gamletsol.core.pairing

/**
 * TV'nin ürettiği, kullanıcının telefona elle girdiği (veya ileride QR ile
 * taratacağı) kısa ömürlü eşleştirme kodu.
 *
 * NOT (yol haritası, G023): [value] insan tarafından yazılabilir kısa bir
 * koddur ("7F3K-9QXZ" gibi), kriptografik bir sır DEĞİLDİR — güvenlik, kısa
 * TTL'den ([DEFAULT_TTL_MILLIS], 60 sn) ve tek kullanımlıktan ([PairingRegistry]
 * bkz.) gelir, kodun uzunluğundan değil. Gerçek uçtan uca güvenlik için
 * G024'teki TLS/fingerprint doğrulaması da gerekiyor (henüz eklenmedi).
 */
data class PairingToken(
    val value: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    fun isExpiredAt(nowMillis: Long): Boolean = nowMillis >= issuedAtMillis + ttlMillis

    companion object {
        /** Önceki planlama notlarındaki ("60s QR expiry") süreyle hizalı. */
        const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
