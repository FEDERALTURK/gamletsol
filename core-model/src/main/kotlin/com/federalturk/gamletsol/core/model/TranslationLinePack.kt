package com.federalturk.gamletsol.core.model

/**
 * Yol haritası X6/G073 ("Oyun bazlı önceden hazırlanmış çeviri satır
 * veritabanı"): tek bir oyun satırının İngilizce kaynağı + GAMLETSOL ekibi/
 * çevirmeni tarafından yazılmış, incelenmiş Türkçe karşılığı.
 *
 * KAYNAK NOTU (telif): [sourceText] GAMLETSOL'un kendi OCR yakalamasından
 * (gerçek oynanış sırasında, G074'teki editoryal hatta) gelir; [translatedText]
 * bizim kendi özgün çevirimizdir. Bu sınıf ve format tamamen GAMLETSOL'a
 * özgüdür — hiçbir üçüncü taraf ürünün dosya biçimi, alan adı veya verisi
 * buraya kopyalanmadı (bkz. proje analiz belgesi
 * `analiz/gamecaption-karsilastirma.md`, sadece kavram seviyesinde esin).
 *
 * [speaker] ve [style] opsiyoneldir; ileride G027 altyazı render'ına
 * (konuşmacı renklendirme gibi) bağlanabilir — bu sınıf şu an sadece veriyi
 * taşır, render tarafı ayrı bir göreve (G027 devamı) bırakılmıştır.
 */
data class TranslationLine(
    val sourceText: String,
    val translatedText: String,
    val speaker: String? = null,
    val style: String? = null,
) {
    init {
        require(sourceText.isNotBlank()) { "sourceText boş olamaz" }
        require(translatedText.isNotBlank()) { "translatedText boş olamaz" }
    }
}

/**
 * Tek bir oyun profiline ([gameProfileId]) bağlı, sürümlü ([version]) satır
 * koleksiyonu. Dağıtım/kurulum/imza mekanizması G075'te (sürümlü paket
 * dağıtımı, G071'in ses paketi altyapısıyla paylaşılan) ayrı ele alınır —
 * bu sınıf yalnız kurulduktan SONRAKİ bellek-içi veri modelidir.
 *
 * [version], aynı [gameProfileId] için paket güncellendiğinde (yeni satır
 * eklendi, bir çeviri düzeltildi) artırılır; core-pipeline'daki çeviri
 * cache'i (G019) bu değeri anahtarının bir parçası yapar — aksi halde eski
 * bir paketten gelen çeviri, paket güncellendikten sonra da cache'te takılı
 * kalabilir.
 */
data class TranslationLinePack(
    val packId: String,
    val gameProfileId: String,
    val version: Int,
    val lines: List<TranslationLine>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(packId.isNotBlank()) { "packId boş olamaz" }
        require(gameProfileId.isNotBlank()) { "gameProfileId boş olamaz" }
        require(version >= 1) { "version en az 1 olmalı" }
    }

    /** Çeviri cache'i ve diagnostics için tek, kompakt sürüm etiketi. */
    val versionTag: String get() = "$packId@$version"

    companion object {
        /** Şu an yalnız 1 var; ileride alan eklenirse [GameProfile] ile aynı desenle göçle. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
