package com.federalturk.gamletsol.core.model

/**
 * Hatta (pipeline) işlenmiş, çizilmeye/seslendirilmeye hazır tek bir altyazı
 * birimi. Yol haritası "Kimlik takibi" bölümündeki kurala göre üretilir:
 * [cueId] aynı metin/bölge için sabit kalır, [revision] her güncellemede
 * artar, [seq] tüm cue'lar arasında monoton artan global sıradır — bir
 * tüketici (ekran çizimi, TTS) yalnızca kendi gördüğü en yüksek [seq]'ten
 * daha yeni bir cue'yu işler; böylece geç gelen/eski bir yanıt asla eski
 * içeriğin üstüne yazmaz veya TTS'i tekrar tetiklemez.
 */
data class Cue(
    val cueId: String,
    val revision: Int,
    val seq: Long,
    val frame: FrameRef,
    val mode: CaptureMode,
    val region: TextRegion,
    val originalText: String,
    val translatedText: String?,
    val createdAtMillis: Long,
    val ttlMillis: Long,
) {
    fun expiresAtMillis(): Long = createdAtMillis + ttlMillis

    fun isExpiredAt(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis()

    /** [other], bu cue'nun aynı [cueId]'si için daha yeni bir revizyon mu? */
    fun isSupersededBy(other: Cue): Boolean {
        require(other.cueId == cueId) { "Farklı cueId'ler karşılaştırılamaz: $cueId vs ${other.cueId}" }
        return other.seq > seq
    }

    companion object {
        /** Altyazı modu için önerilen TTL aralığı (yol haritası: 2-6 sn). */
        val SUBTITLE_TTL_RANGE_MILLIS = 2_000L..6_000L

        /** Varsayılan altyazı TTL'i — aralığın ortasına yakın, muhafazakâr bir seçim. */
        const val DEFAULT_SUBTITLE_TTL_MILLIS = 4_000L
    }
}
