package com.federalturk.gamletsol.core.model

/**
 * Bir kareyi tekil olarak tanımlayan kimlik bilgisi (yol haritası "Kimlik
 * takibi" bölümü). [sourceEpoch], kaynağın (kamera oturumu veya konsol
 * bağlantısı) her yeniden başlatılışında artar; böylece eski bir kaynaktan
 * kalan kareler/cue'lar yeni oturumla asla karışmaz. [frameId], o epoch
 * içinde monoton artan kare sayacıdır.
 */
data class FrameRef(
    val sourceEpoch: Long,
    val frameId: Long,
    val capturedAtMillis: Long,
) {
    /** [other] bu kareden kronolojik olarak sonra mı geldi? */
    fun isNewerThan(other: FrameRef): Boolean {
        if (sourceEpoch != other.sourceEpoch) return sourceEpoch > other.sourceEpoch
        return frameId > other.frameId
    }
}
