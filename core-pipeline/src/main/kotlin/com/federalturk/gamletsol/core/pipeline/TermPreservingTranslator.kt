package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.GameProfile
import com.federalturk.gamletsol.core.model.TermOverride

/**
 * Yol haritası G031 ("Oyun profili ve terim sözlüğü"): [delegate] çeviri
 * motorunun (örn. ML Kit) sayıları, buton adlarını, istatistik
 * kısaltmalarını (HP/Armor/XP gibi) veya özel isimleri yanlışlıkla çevirip
 * bozmasını önler.
 *
 * Yöntem: [translate] çağrılmadan önce metindeki korunacak terimler
 * (sayılar + [activeProfile]'daki [TermOverride]'lar) tek geçişte bulunup
 * yer tutucu jetonlarla değiştirilir ("§0§", "§1§", ...), [delegate] yalnız
 * bu maskelenmiş metni görür, çeviri sonucunda jetonlar orijinal terimle
 * (veya [TermOverride.displayTerm] belirtilmişse onunla) geri yüklenir.
 *
 * NOT (dürüstlük ilkesi): Bu yer-tutucu/geri-yükleme yaklaşımı yalnız
 * [FakeTranslator] ile birim testiyle doğrulandı (bkz.
 * `TermPreservingTranslatorTest`). Gerçek ML Kit modelinin "§0§" gibi bir
 * jetonu çeviri sırasında bozup bozmayacağı (boşluk ekleme, büyük/küçük
 * harf değişimi vb.) bu kum havuzunda hiç denenmedi — gerçek cihazda
 * doğrulanması gerekir.
 */
class TermPreservingTranslator(
    private val delegate: Translator,
    private val activeProfile: () -> GameProfile?,
) : Translator {

    override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
        val profile = activeProfile()
        val pattern = buildPattern(profile)

        val replacements = mutableListOf<String>()
        val masked = pattern.replace(text) { match ->
            val override = profile?.terms.orEmpty().firstOrNull { it.sourceTerm.equals(match.value, ignoreCase = true) }
            replacements += (override?.displayTerm ?: match.value)
            placeholder(replacements.lastIndex)
        }

        if (replacements.isEmpty()) {
            return delegate.translate(text, sourceLanguageCode, targetLanguageCode)
        }

        val translatedMasked = delegate.translate(masked, sourceLanguageCode, targetLanguageCode)

        var restored = translatedMasked
        replacements.forEachIndexed { index, value ->
            restored = restored.replace(placeholder(index), value)
        }
        return restored
    }

    private fun placeholder(index: Int) = "§${index}§"

    /**
     * Sayılar HER ZAMAN korunur (profil olsun olmasın); profil terimleri
     * varsa eklenir. Terimler `\b` (kelime sınırı) ile sarılır — yoksa örn.
     * "X" terimi "Explosion" gibi bir kelimenin İÇİNDEKİ harfle de eşleşip
     * metni bozardı.
     */
    private fun buildPattern(profile: GameProfile?): Regex {
        val termAlternatives = profile?.terms.orEmpty().map { "\\b${Regex.escape(it.sourceTerm)}\\b" }
        val alternatives = listOf(NUMBER_PATTERN) + termAlternatives
        return Regex(alternatives.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    companion object {
        private const val NUMBER_PATTERN = "\\d+"
    }
}
