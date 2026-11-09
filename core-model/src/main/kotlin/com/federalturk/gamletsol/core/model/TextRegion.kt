package com.federalturk.gamletsol.core.model

/**
 * Kare içinde normalize edilmiş (0f..1f) bir dikdörtgen bölge — hem ROI
 * seçimi hem de bir cue'nun ekrandaki konumu için kullanılır. Normalize
 * olması, kaynak çözünürlüğünden (telefon kamerası vs konsol MediaProjection)
 * bağımsız çalışmasını sağlar.
 */
data class TextRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "TextRegion koordinatları 0f..1f aralığında olmalı: $this"
        }
        require(left < right && top < bottom) { "TextRegion geçersiz: $this" }
    }

    companion object {
        /** Altyazı modu için varsayılan ROI: ekranın alt ~%22'si, kenarlardan içeride. */
        val DEFAULT_SUBTITLE_BAND = TextRegion(left = 0.08f, top = 0.74f, right = 0.92f, bottom = 0.96f)

        /** Akıllı Ekran modu için tüm kare. */
        val FULL_FRAME = TextRegion(left = 0f, top = 0f, right = 1f, bottom = 1f)
    }
}
