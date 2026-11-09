package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TextRegion

/** Bir OCR denemesinin ham sonucu — normalizasyon/kararlılık kontrolünden önce. */
data class RecognizedText(
    val text: String,
    val confidence: Float = 1f,
)

/**
 * Cihaz-üstü OCR soyutlaması. Android tarafında ML Kit Latin Text Recognition
 * ile uygulanır (app-mobile); bu arayüz testlerde sahte bir uygulamayla
 * değiştirilebilsin diye core-pipeline'da tanımlıdır.
 */
interface TextRecognizer {
    /** [region] zaten uygulanmış bir [image] üzerinde metin tanır.
     * Okunabilir metin bulunamazsa `null` döner. */
    suspend fun recognize(image: RawImage, region: TextRegion): RecognizedText?
}
