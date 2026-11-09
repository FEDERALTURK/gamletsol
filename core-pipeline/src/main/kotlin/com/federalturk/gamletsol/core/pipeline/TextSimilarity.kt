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

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1, // silme
                    dp[i][j - 1] + 1, // ekleme
                    dp[i - 1][j - 1] + substitutionCost, // değiştirme
                )
            }
        }
        return dp[a.length][b.length]
    }
}
