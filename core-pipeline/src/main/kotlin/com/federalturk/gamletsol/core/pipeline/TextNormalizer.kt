package com.federalturk.gamletsol.core.pipeline

/** Yol haritası "normalizasyon" adımı: OCR çıktısındaki gürültülü boşlukları sadeleştirir. */
object TextNormalizer {
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
}
