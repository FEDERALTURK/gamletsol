package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack

/**
 * Yol haritası G073 ("Satır veritabanı format ve eşleştirme sözleşmesi"):
 * [pack] içindeki satırları normalize edilmiş OCR metnine göre aratılabilir
 * hale getirir. Üç aşamalı eşleştirme, bu sırayla:
 *
 * 1. **Tam eşleşme**: anahtar (bkz. [matchKey]) birebir eşitse o satır döner.
 *    Anahtar büyük/küçük harf, fazladan boşluk, tırnak/üç nokta biçimi ve
 *    satır sonundaki noktalama farklarını yok sayar — OCR "LOADING...",
 *    "Loading…" ve "Loading" için aynı satırı bulur.
 * 2. **Kalıp eşleşmesi** (2026-11-13): kaynak metni `{0}`, `{1}` gibi yer
 *    tutucular içeren satırlar kalıptır. Örn. kaynak `Press {0} to continue`,
 *    çeviri `Devam etmek için {0} tuşuna basın` → OCR "Press X to continue"
 *    → "Devam etmek için X tuşuna basın". Yer tutucu en fazla
 *    [MAX_PLACEHOLDER_LENGTH] karakterlik kısa bir parça yakalar (tuş adı
 *    gibi); uzun serbest metni yakalayıp anlamsız bir çeviri üretmesin diye.
 * 3. **Önek eşleşmesi**: OCR satırın TAMAMINI henüz yakalamamış olabilir
 *    (typewriter efekti ortasında, satır kenardan kesilmiş vb.). Bu durumda,
 *    pakette OCR metnini ÖNEK olarak içeren en YAKIN — en az fazlalık
 *    karakter içeren — satır eşleşme kabul edilir. Yanlışlıkla çok kısa/genel
 *    bir metnin rastgele bir satırla eşleşmesini önlemek için OCR metni en az
 *    [minPrefixLength] karakter olmalıdır. Kalıp satırları önek eşleşmesine
 *    katılmaz.
 *
 * NOT (dürüstlük ilkesi): Bu eşleştirme mantığı yalnız birim testle (temiz/
 * sahte veriyle) doğrulandı. Gerçek gürültülü OCR çıktısıyla (yanlış
 * karakter tanıma, fazladan boşluk/noktalama) gerçek bir pakete karşı hiç
 * denenmedi — eşik değerleri gerçek veriyle yeniden ayarlanabilir.
 */
class LinePackIndex(
    private val pack: TranslationLinePack,
    private val minPrefixLength: Int = DEFAULT_MIN_PREFIX_LENGTH,
) {
    /** [groupPlaceholders][i] = (i+1). yakalama grubunun karşılık geldiği yer tutucu numarası. */
    private class Pattern(val regex: Regex, val groupPlaceholders: List<String>, val line: TranslationLine)

    private val byExactKey: Map<String, TranslationLine>
    private val patterns: List<Pattern>

    // Önek adayları en kısadan en uzuna sıralı: ilk eşleşen, OCR metnine en
    // yakın (en az fazlalık içeren) satırdır.
    private val prefixCandidates: List<Pair<String, TranslationLine>>

    init {
        val (patternLines, plainLines) = pack.lines.partition { isPattern(it.sourceText) }
        // Aynı anahtara sahip birden fazla satır varsa SONUNCUSU kazanır
        // (kişisel kütüphanede en son yapılan düzeltme geçerli olsun).
        byExactKey = plainLines.associateBy { matchKey(it.sourceText) }.filterKeys { it.isNotEmpty() }
        patterns = patternLines.mapNotNull { line ->
            compilePattern(line.sourceText)?.let { (regex, groups) -> Pattern(regex, groups, line) }
        }
        prefixCandidates = byExactKey.entries
            .map { it.key to it.value }
            .sortedBy { (key, _) -> key.length }
    }

    /** Eşleşme bulunursa o satırı, bulunamazsa `null` döner (çağıran canlı çeviriye düşmeli). */
    fun lookup(ocrText: String): TranslationLine? {
        val key = matchKey(ocrText)
        if (key.isEmpty()) return null

        byExactKey[key]?.let { return it }

        matchPattern(key)?.let { return it }

        if (key.length < minPrefixLength) return null
        return prefixCandidates.firstOrNull { (lineKey, _) -> lineKey.length > key.length && lineKey.startsWith(key) }
            ?.second
    }

    private fun matchPattern(key: String): TranslationLine? {
        for (pattern in patterns) {
            val match = pattern.regex.matchEntire(key) ?: continue
            var translated = pattern.line.translatedText
            pattern.groupPlaceholders.forEachIndexed { groupIndex, placeholder ->
                val value = match.groupValues[groupIndex + 1]
                translated = translated.replace("{$placeholder}", restoreCase(value))
            }
            return TranslationLine(sourceText = key, translatedText = translated, speaker = pattern.line.speaker, style = pattern.line.style)
        }
        return null
    }

    companion object {
        const val DEFAULT_MIN_PREFIX_LENGTH = 8

        /** [minPrefixLength] olarak verilirse önek eşleşmesi tamamen kapanır. */
        const val PREFIX_MATCH_DISABLED = Int.MAX_VALUE

        /** Bir yer tutucunun yakalayabileceği en uzun parça (tuş adı vb.). */
        const val MAX_PLACEHOLDER_LENGTH = 24

        private val PLACEHOLDER = Regex("""\{(\d)\}""")
        private val TRAILING_PUNCTUATION = Regex("""[\s.!?…:;,]+$""")
        private val LEADING_PUNCTUATION = Regex("""^[\s.…:;,\-–—]+""")

        fun isPattern(sourceText: String): Boolean = PLACEHOLDER.containsMatchIn(sourceText)

        /**
         * Eşleştirme anahtarı: boşlukları sadeleştir, tipografik tırnak ve üç
         * noktayı düz biçime çevir, baştaki/sondaki noktalamayı at, küçük harfe
         * çevir. Kalıplarda `{n}` yer tutucuları korunur.
         */
        fun matchKey(text: String): String =
            TextNormalizer.normalize(text)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace("…", "...")
                .replace(TRAILING_PUNCTUATION, "")
                .replace(LEADING_PUNCTUATION, "")
                .lowercase()

        private fun compilePattern(sourceText: String): Pair<Regex, List<String>>? {
            val key = matchKey(sourceText)
            if (key.isEmpty()) return null
            val builder = StringBuilder()
            var last = 0
            val groupOrder = mutableListOf<String>()
            for (match in PLACEHOLDER.findAll(key)) {
                builder.append(Regex.escape(key.substring(last, match.range.first)))
                val placeholder = match.groupValues[1]
                val existingGroup = groupOrder.indexOf(placeholder)
                if (existingGroup < 0) {
                    groupOrder += placeholder
                    builder.append("(.{1,$MAX_PLACEHOLDER_LENGTH}?)")
                } else {
                    // Aynı yer tutucu ikinci kez geçerse aynı metni beklemeli.
                    builder.append("\\").append(existingGroup + 1)
                }
                last = match.range.last + 1
            }
            builder.append(Regex.escape(key.substring(last)))
            val regex = runCatching { Regex(builder.toString()) }.getOrNull() ?: return null
            return regex to groupOrder
        }

        /**
         * Anahtar küçük harfe çevrildiği için yakalanan parça da küçük harftir;
         * tuş adları genelde büyük yazılır ("x" → "X", "rb" → "RB"). Kısa
         * (≤ 3 harf) parçalar büyük harfe, diğerleri ilk harfi büyük yapılır.
         */
        private fun restoreCase(value: String): String {
            val trimmed = value.trim()
            return if (trimmed.length <= 3) {
                trimmed.uppercase()
            } else {
                trimmed.replaceFirstChar { it.uppercase() }
            }
        }
    }
}
