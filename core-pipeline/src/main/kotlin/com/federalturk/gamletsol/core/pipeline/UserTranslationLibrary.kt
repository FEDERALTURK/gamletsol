package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack

/**
 * Kullanıcının kendi düzelttiği çevirilerden oluşan, düzenlenebilir satır
 * paketi (2026-11-13, "çeviri kütüphanesi").
 *
 * Kullanıcı ekrandaki bir çeviriyi düzelttiğinde [put] çağrılır; aynı
 * İngilizce satır (büyük/küçük harf, boşluk ve sondaki noktalama farkı
 * gözetmeksizin — bkz. [LinePackIndex.matchKey]) bir daha görüldüğünde
 * [LinePackTranslator] canlı çeviri yerine bu düzeltmeyi gösterir.
 *
 * Her değişiklik [TranslationLinePack.version]'ı artırır; bu sayede çeviri
 * önbelleği (CaptionPipeline `cacheVersionTag`) eski çeviriyi göstermeye
 * devam etmez ve ekrandaki satır yeni çeviriyle güncellenir.
 *
 * Değişmez [TranslationLinePack] anlık görüntüleri üretir: [snapshot] başka
 * bir iş parçacığından (çeviri hattı) güvenle okunabilir. Değiştiren
 * metodlar `synchronized`'dır. Kalıcılık (dosyaya yazma) bu sınıfın işi
 * değildir — platform tarafı [LinePackTsvCodec] ile yapar.
 */
class UserTranslationLibrary(initial: TranslationLinePack? = null) {

    @Volatile
    private var current: TranslationLinePack = initial ?: TranslationLinePack(
        packId = PACK_ID,
        gameProfileId = GAME_PROFILE_ID,
        version = 1,
        lines = emptyList(),
    )

    /** O anki değişmez anlık görüntü. */
    fun snapshot(): TranslationLinePack = current

    val size: Int get() = current.lines.size

    /** Aynı anahtarlı mevcut satır varsa onun yerine geçer; yoksa ekler. */
    @Synchronized
    fun put(sourceText: String, translatedText: String): TranslationLinePack {
        val source = TextNormalizer.normalize(sourceText)
        val translated = TextNormalizer.normalize(translatedText)
        require(source.isNotEmpty()) { "Kaynak metin boş olamaz" }
        require(translated.isNotEmpty()) { "Çeviri boş olamaz" }
        val key = LinePackIndex.matchKey(source)
        val kept = current.lines.filterNot { LinePackIndex.matchKey(it.sourceText) == key }
        return update(kept + TranslationLine(sourceText = source, translatedText = translated))
    }

    /** [sourceText] ile aynı anahtarlı satırı siler; silinecek satır yoksa `false`. */
    @Synchronized
    fun remove(sourceText: String): Boolean {
        val key = LinePackIndex.matchKey(sourceText)
        val kept = current.lines.filterNot { LinePackIndex.matchKey(it.sourceText) == key }
        if (kept.size == current.lines.size) return false
        update(kept)
        return true
    }

    @Synchronized
    fun clear() {
        if (current.lines.isNotEmpty()) update(emptyList())
    }

    /** [sourceText] için kayıtlı düzeltme (tam anahtar eşleşmesi), yoksa `null`. */
    fun find(sourceText: String): TranslationLine? {
        val key = LinePackIndex.matchKey(sourceText)
        return current.lines.lastOrNull { LinePackIndex.matchKey(it.sourceText) == key }
    }

    private fun update(lines: List<TranslationLine>): TranslationLinePack {
        val next = current.copy(version = current.version + 1, lines = lines)
        current = next
        return next
    }

    companion object {
        const val PACK_ID = "kisisel-kutuphane"
        const val GAME_PROFILE_ID = "genel"
    }
}
