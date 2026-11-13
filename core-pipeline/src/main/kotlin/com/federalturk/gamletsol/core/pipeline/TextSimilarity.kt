package com.federalturk.gamletsol.core.pipeline

/**
 * Yol haritasının "2-kare kararlılık/fuzzy eşleşme" adımı için basit bir
 * Levenshtein tabanlı benzerlik ölçütü. Ardışık iki OCR okuması birebir aynı
 * olmasa bile (tek karakterlik kenarlık gürültüsü vb.) yeterince benzerse
 * aynı kabul edilir; bu, tek bir yanlış OCR okumasının yeni bir cue
 * doğurmasını önler.
 */
object TextSimilarity {
    /** 0f (tamamen farklı) ile 1f (birebir aynı) arasında benzerlik oranı. */
    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1f - distance.toFloat() / maxLen
    }

    // İki satırlı DP: O(n·m) zaman, O(min(n, m)) bellek. Her karede çağrıldığı
    // için (n+1)×(m+1) tablo ayırmak yerine iki dizi yeniden kullanılır.
    private fun levenshteinDistance(a: String, b: String): Int {
        val (longer, shorter) = if (a.length >= b.length) a to b else b to a
        var previous = IntArray(shorter.length + 1) { it }
        var current = IntArray(shorter.length + 1)
        for (i in 1..longer.length) {
            current[0] = i
            for (j in 1..shorter.length) {
                val substitutionCost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1, // silme
                    current[j - 1] + 1, // ekleme
                    previous[j - 1] + substitutionCost, // değiştirme
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[shorter.length]
    }
}
