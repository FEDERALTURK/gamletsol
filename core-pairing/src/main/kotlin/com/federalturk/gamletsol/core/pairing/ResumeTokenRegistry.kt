package com.federalturk.gamletsol.core.pairing

import java.security.SecureRandom

/**
 * TV tarafında, eşleşmiş bir telefona verilen "yeniden bağlanma jetonları"
 * (protokol v2, 2026-11-13).
 *
 * Neden: Eşleştirme kodu tek kullanımlık ve 60 sn geçerli. Wi-Fi bir anlığına
 * koptuğunda ya da TV kısa süre uykuya geçtiğinde telefonun yeniden QR
 * okutması gerekiyordu. Artık TV, başarılı her bağlantıda telefona tahmin
 * edilemez bir jeton verir; telefon bağlantı koparsa bu jetonla kendiliğinden
 * geri bağlanır.
 *
 * Güvenlik kuralları:
 * - Jeton 128 bit rastgele ([SecureRandom]); yalnız TLS kanalı içinde
 *   taşınır, QR'da/NSD'de yer almaz.
 * - **Tek kullanımlık**: bir jetonla bağlanılınca o jeton geçersiz olur;
 *   sunucu aynı bağlantıda yenisini verir (dönüşümlü). Çalınmış eski bir
 *   jeton işe yaramaz.
 * - [ttlMillis] (varsayılan 12 saat) sonra kendiliğinden geçersiz olur.
 * - Bellekte tutulur: TV uygulaması kapatılıp açılınca tüm jetonlar
 *   geçersizdir, telefon yeniden QR okutmalıdır (bilinçli sınır).
 * - En fazla [maxTokens] jeton tutulur; aşılırsa en eskisi silinir.
 */
class ResumeTokenRegistry(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val random = SecureRandom()
    private val tokens = LinkedHashMap<String, Long>() // jeton → veriliş zamanı

    @Synchronized
    fun issue(): String {
        purgeExpired()
        val bytes = ByteArray(16).also(random::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        tokens[token] = clock()
        while (tokens.size > maxTokens) {
            tokens.remove(tokens.keys.first())
        }
        return token
    }

    /** Geçerliyse `true` döner ve jetonu tüketir (tek kullanımlık). */
    @Synchronized
    fun validateAndConsume(token: String): Boolean {
        purgeExpired()
        return tokens.remove(token) != null
    }

    /** TV kullanıcıya "tüm telefonları unut" dediğinde vb. */
    @Synchronized
    fun revokeAll() {
        tokens.clear()
    }

    private fun purgeExpired() {
        val now = clock()
        tokens.entries.removeAll { now - it.value >= ttlMillis }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 12L * 60 * 60 * 1000
        const val DEFAULT_MAX_TOKENS = 8
    }
}
