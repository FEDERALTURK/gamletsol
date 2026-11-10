package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * G073 doğrulama kriteri: tam eşleşme + önek eşleşmesi mantığı, sahte (temiz)
 * veriyle doğrulanır — bkz. [LinePackIndex] KDoc'undaki dürüstlük notu.
 */
class LinePackIndexTest {

    private fun pack(vararg lines: TranslationLine) = TranslationLinePack(
        packId = "test-pack",
        gameProfileId = "test-profile",
        version = 1,
        lines = lines.toList(),
    )

    @Test
    fun `exact match is found case-insensitively after normalization`() {
        val index = LinePackIndex(
            pack(TranslationLine(sourceText = "The door is locked.", translatedText = "Kapı kilitli.")),
        )

        val hit = index.lookup("  the   DOOR is locked.  ")

        assertEquals("Kapı kilitli.", hit?.translatedText)
    }

    @Test
    fun `unknown line falls through to null so caller can use live translation`() {
        val index = LinePackIndex(
            pack(TranslationLine(sourceText = "The door is locked.", translatedText = "Kapı kilitli.")),
        )

        assertNull(index.lookup("You found a sword."))
    }

    @Test
    fun `prefix match picks the closest (shortest overhang) candidate line`() {
        val index = LinePackIndex(
            pack(
                TranslationLine(sourceText = "Watch out, the bridge is unstable", translatedText = "Dikkat, köprü sağlam değil"),
                TranslationLine(
                    sourceText = "Watch out, the bridge is unstable and may collapse",
                    translatedText = "Dikkat, köprü sağlam değil ve çökebilir",
                ),
            ),
        )

        // OCR henüz tüm satırı yakalamamış (typewriter ortası) — en yakın
        // (en az fazlalıklı) satır seçilmeli, daha uzun olan değil.
        val hit = index.lookup("Watch out, the bridge is unstable")

        assertEquals("Dikkat, köprü sağlam değil", hit?.translatedText)
    }

    @Test
    fun `prefix match is rejected below the minimum prefix length to avoid false positives`() {
        val index = LinePackIndex(
            pack(TranslationLine(sourceText = "Hello there, traveler", translatedText = "Merhaba, gezgin")),
            minPrefixLength = 8,
        )

        // "Hello" (5 karakter) çok kısa bir önek — rastgele bir kelimenin
        // tesadüfen bir satırın başlangıcı olmasını önlemek için reddedilmeli.
        assertNull(index.lookup("Hello"))
    }

    @Test
    fun `blank ocr text never matches anything`() {
        val index = LinePackIndex(
            pack(TranslationLine(sourceText = "Hello there", translatedText = "Merhaba")),
        )

        assertNull(index.lookup("   "))
    }
}
