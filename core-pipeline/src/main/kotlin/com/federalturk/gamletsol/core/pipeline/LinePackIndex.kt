package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack

/**
 * Yol haritası G073 ("Satır veritabanı format ve eşleştirme sözleşmesi"):
 * [pack] içindeki satırları normalize edilmiş OCR metnine göre aratılabilir
 * hale getirir. İki aşamalı eşleştirme:
 *
 * 1. **Tam eşleşme**: normalize edilmiş (bkz. [TextNormalizer]), büyük/küçük
 *    harf duyarsız metin pakette birebir varsa, o satır döner.
 * 2. **Önek eşleşmesi**: OCR satırın TAMAMINI henüz yakalamamış olabilir
 *    (typewriter efekti ortasında, satır kenardan kesilmiş vb.). Bu durumda,
 *    pakette OCR metnini ÖNEK olarak içeren (yani pakette "ocrMetni + devamı"
 *    şeklinde bir satır varsa) en YAKIN — en az fazlalık karakter içeren —
 *    satır eşleşme kabul edilir. Yanlışlıkla çok kısa/genel bir metnin
 *    rastgele bir satırla eşleşmesini önlemek için OCR metni en az
 *    [minPrefixLength] karakter olmalıdır.
 *
 * NOT (dürüstlük ilkesi): Bu eşleştirme mantığı, [TermPreservingTranslator]
 * gibi, yalnız birim testle (temiz/sahte veriyle) doğrulandı. Gerçek
 * gürültülü OCR çıktısıyla (yanlış karakter tanıma, fazladan boşluk/noktalama)
 * gerçek bir pakete karşı hiç denenmedi — eşik değerleri (özellikle
 * [minPrefixLength]) gerçek veriyle yeniden ayarlanabilir.
 */
class LinePackIndex(
    private val pack: TranslationLinePack,
    private val minPrefixLength: Int = DEFAULT_MIN_PREFIX_LENGTH,
) {
    private val byExactKey: Map<String, TranslationLine> =
        pack.lines.associateBy { normalizeKey(it.sourceText) }

    // Önek adayları en kısadan en uzuna sıralı: ilk eşleşen, OCR metnine en
    // yakın (en az fazlalık içeren) satırdır.
    private val prefixCandidates: List<Pair<String, TranslationLine>> =
        pack.lines
            .map { normalizeKey(it.sourceText) to it }
            .filter { (key, _) -> key.isNotEmpty() }
            .sortedBy { (key, _) -> key.length }

    /** Eşleşme bulunursa o satırı, bulunamazsa `null` döner (çağıran canlı çeviriye düşmeli). */
    fun lookup(ocrText: String): TranslationLine? {
        val key = normalizeKey(ocrText)
        if (key.isEmpty()) return null

        byExactKey[key]?.let { return it }

        if (key.length < minPrefixLength) return null
        return prefixCandidates.firstOrNull { (lineKey, _) -> lineKey.length > key.length && lineKey.startsWith(key) }
            ?.second
    }

    companion object {
        const val DEFAULT_MIN_PREFIX_LENGTH = 8

        private fun normalizeKey(text: String): String = TextNormalizer.normalize(text).lowercase()
    }
}
