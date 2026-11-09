package com.federalturk.gamletsol.core.pairing

/** [PairingRegistry.validateAndConsume]'un olası sonuçları. */
enum class PairingResult {
    VALID,
    EXPIRED,
    ALREADY_USED,
    UNKNOWN,
}

/**
 * TV tarafında tutulan, o an geçerli eşleştirme kodunu/token'ını yöneten
 * kayıt defteri (yol haritası G023). Tek seferde yalnızca BİR kod aktiftir —
 * [issue] her çağrıldığında önceki kod (henüz kullanılmamış olsa da) geçersiz
 * hale gelir ("ikinci yayıncı" senaryosu: TV ekranında yeni bir kod
 * gösterildiğinde eski kod artık işe yaramaz).
 *
 * Thread-safety: `TvEventServer`'ın her istemci bağlantısını kendi iş
 * parçacığında işlediği düşünülerek `synchronized` ile korunuyor.
 */
class PairingRegistry(private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private var active: PairingToken? = null
    private val recentlyConsumed = ArrayDeque<String>()

    /** Önceki aktif kodu (varsa) geçersiz kılar ve yeni bir kod üretir. */
    fun issue(ttlMillis: Long = PairingToken.DEFAULT_TTL_MILLIS): PairingToken {
        synchronized(lock) {
            val token = PairingTokenGenerator.generate(clock(), ttlMillis)
            active = token
            return token
        }
    }

    /**
     * [code] geçerliyse onu hemen tüketir (tek kullanımlık) ve [PairingResult.VALID]
     * döner — bu yüzden aynı kod bir daha ASLA kabul edilmez (replay/tekrar
     * deneme reddi, roadmap'in "tekrar denemesinin reddi" doğrulama notu).
     */
    fun validateAndConsume(code: String): PairingResult {
        synchronized(lock) {
            if (recentlyConsumed.contains(code)) return PairingResult.ALREADY_USED

            val current = active
            if (current == null || current.value != code) return PairingResult.UNKNOWN

            if (current.isExpiredAt(clock())) return PairingResult.EXPIRED

            active = null
            rememberConsumed(code)
            return PairingResult.VALID
        }
    }

    private fun rememberConsumed(code: String) {
        recentlyConsumed.addLast(code)
        // Sınırsız büyümesin — tek bir TV oturumunda makul bir geçmiş yeter.
        while (recentlyConsumed.size > MAX_REMEMBERED_CONSUMED) {
            recentlyConsumed.removeFirst()
        }
    }

    companion object {
        private const val MAX_REMEMBERED_CONSUMED = 50
    }
}
