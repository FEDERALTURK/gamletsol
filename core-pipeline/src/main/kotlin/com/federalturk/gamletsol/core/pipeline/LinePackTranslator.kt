package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLinePack

/**
 * Yol haritası X6/G073: oyun başına önceden hazırlanmış, insan onaylı bir
 * çeviri satır veritabanını (bkz. [LinePackIndex]) canlı çevirinin ÖNÜNE
 * koyar.
 *
 * [activePack] o anki oyun için aktif paketi döndürür; `null` ise bu katman
 * TAMAMEN devre dışıdır ve her metin doğrudan [delegate]'e gider — yani
 * paket yoksa (veya henüz hiç paket üretilmediyse) davranış, bu sınıf hiç
 * yokmuş gibi, mevcut G010/G047 canlı çeviri akışıyla birebir aynıdır.
 *
 * [delegate] genelde [TermPreservingTranslator] olmalıdır (o da gerçek ML
 * Kit/[Translator] uygulamasını sarar) — paket isabet ederse [delegate] HİÇ
 * çağrılmaz, yani terim koruma maskeleme/geri-yükleme masrafı da atlanır;
 * bu beklenen davranıştır çünkü paketteki çeviri zaten insan onaylı NİHAİ
 * metindir.
 *
 * Kaynak/telif notu: Bu sınıf hiçbir üçüncü taraf ürünün kodunu veya
 * verisini okumaz/içermez; sadece çağıranın verdiği, GAMLETSOL'un kendi
 * editoryal pack'lerini (G074) tüketir.
 */
class LinePackTranslator(
    private val delegate: Translator,
    private val activePack: () -> TranslationLinePack?,
) : Translator {

    // Aynı paket sürümü art arda sorulduğunda (olağan durum) indeksi her
    // seferinde yeniden kurmamak için küçük bir önbellek.
    private var cachedPackVersionTag: String? = null
    private var cachedIndex: LinePackIndex? = null

    override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
        val pack = activePack() ?: return delegate.translate(text, sourceLanguageCode, targetLanguageCode)

        val hit = indexFor(pack).lookup(text)
        if (hit != null) {
            return hit.translatedText
        }
        return delegate.translate(text, sourceLanguageCode, targetLanguageCode)
    }

    private fun indexFor(pack: TranslationLinePack): LinePackIndex {
        val existing = cachedIndex
        if (existing != null && cachedPackVersionTag == pack.versionTag) {
            return existing
        }
        val built = LinePackIndex(pack)
        cachedPackVersionTag = pack.versionTag
        cachedIndex = built
        return built
    }
}
