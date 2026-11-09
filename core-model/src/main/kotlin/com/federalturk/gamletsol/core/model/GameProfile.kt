package com.federalturk.gamletsol.core.model

/**
 * Yol haritası G031 ("Oyun profili ve terim sözlüğü"): kullanıcının seçtiği
 * oyun türüne göre OCR bölgesini ve çeviri sırasında KORUNMASI gereken
 * terimleri (buton adları, istatistik kısaltmaları — HP/Armor gibi —, özel
 * isimler) tanımlar.
 *
 * Bu sınıf kasıtlı olarak saf Kotlin veridir (bkz. kök `AGENTS.md` madde 5,
 * modül sınırları) — kalıcı depolama (DataStore/Room) ayrı bir adaptör
 * olarak `app-mobile`'da eklenecek; bu modülde yalnız bellek-içi [GameProfileStore]
 * ile test edilir.
 */
data class GameProfile(
    val profileId: String,
    val displayName: String,
    val genre: GameGenre,
    val region: TextRegion = TextRegion.DEFAULT_SUBTITLE_BAND,
    val terms: List<TermOverride> = emptyList(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        /**
         * Şu an yalnız 1 var. [GameProfileStore.upsert] daha eski bir
         * [schemaVersion] ile gelen bir profili bu sürüme taşır — ileride 2.
         * şema eklenirse buraya gerçek bir alan dönüşümü yazılacak.
         */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Yol haritasının "üç oyun türü" kabul kriterindeki örnek sınıflandırma. */
enum class GameGenre {
    RPG,
    FPS,
    STRATEGY,
}

/**
 * Çeviri motoruna gitmeden önce/sonra KORUNACAK bir terim.
 *
 * [displayTerm] `null` ise terim TAMAMEN olduğu gibi bırakılır (örn. bir
 * oyuncu/karakter ismi: "Geralt" çeviri çıktısında da "Geralt" kalır).
 * `null` değilse, terim çeviri motorundan geçmez ama ekranda [displayTerm]
 * ile değiştirilir (örn. kısaltmayı büyütmek: "HP" -> "Can Puanı" gibi bir
 * sabit karşılık istenirse).
 *
 * Eşleştirme büyük/küçük harf duyarsızdır (ekranda genelde "HP" / "Hp" / "hp"
 * gibi varyasyonlar görülebilir) — bkz. [TermPreservingTranslator] (core-pipeline).
 */
data class TermOverride(
    val sourceTerm: String,
    val displayTerm: String? = null,
) {
    init {
        require(sourceTerm.isNotBlank()) { "sourceTerm boş olamaz" }
    }
}
