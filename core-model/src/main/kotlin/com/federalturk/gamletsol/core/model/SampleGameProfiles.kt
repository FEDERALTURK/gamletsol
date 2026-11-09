package com.federalturk.gamletsol.core.model

/**
 * G031 kabul kriteri "üç oyun türü için ROI/terim/isim ayarı" için üç örnek
 * profil. Bunlar gerçek bir oyundan değil, yol haritasındaki tür
 * sınıflandırmasından (RPG/FPS/STRATEGY) türetilen TEMSİLİ örneklerdir —
 * gerçek bir oyunla hiç test edilmedi (dürüstlük ilkesi).
 *
 * Her profildeki [TermOverride] listesi, doğrulama kriterinde istenen
 * kategorileri kapsar:
 *  - Sayılar zaten [com.federalturk.gamletsol.core.pipeline.TermPreservingTranslator]
 *    tarafından profilden BAĞIMSIZ olarak her zaman korunur (regex ile).
 *  - Butonlar (örn. "RT", "LB", "X") — kısaltma olarak çeviri motoruna
 *    verilmez.
 *  - İstatistik kısaltmaları (HP/Armor/XP/Mana) — aynı şekilde korunur.
 *  - Özel isimler (`displayTerm = null`) — olduğu gibi bırakılır.
 */
object SampleGameProfiles {
    val ACTION_RPG = GameProfile(
        profileId = "sample-action-rpg",
        displayName = "Örnek: Aksiyon RPG",
        genre = GameGenre.RPG,
        terms = listOf(
            TermOverride(sourceTerm = "HP"),
            TermOverride(sourceTerm = "Armor"),
            TermOverride(sourceTerm = "XP"),
            TermOverride(sourceTerm = "Mana"),
            // Örnek özel isimler — displayTerm=null, olduğu gibi kalır.
            TermOverride(sourceTerm = "Geralt"),
            TermOverride(sourceTerm = "Ciri"),
        ),
    )

    val FPS = GameProfile(
        profileId = "sample-fps",
        displayName = "Örnek: FPS",
        genre = GameGenre.FPS,
        terms = listOf(
            // Konsol kumandası buton adları — çevrilirse anlamsızlaşır.
            TermOverride(sourceTerm = "RT"),
            TermOverride(sourceTerm = "LB"),
            TermOverride(sourceTerm = "RB"),
            TermOverride(sourceTerm = "X"),
            TermOverride(sourceTerm = "Armor"),
            TermOverride(sourceTerm = "HP"),
        ),
    )

    val STRATEGY = GameProfile(
        profileId = "sample-strategy",
        displayName = "Örnek: Strateji",
        genre = GameGenre.STRATEGY,
        // Akıllı Ekran (Pro katman) senaryosu gibi tam kare ROI'ye daha yakın —
        // strateji oyunlarında menü/envanter metni ekranın her yerinde olabilir.
        region = TextRegion.FULL_FRAME,
        terms = listOf(
            TermOverride(sourceTerm = "HP"),
            TermOverride(sourceTerm = "Gold"),
            TermOverride(sourceTerm = "Wood"),
            // Örnek birim/fraksiyon ismi — olduğu gibi kalır.
            TermOverride(sourceTerm = "Starcraft"),
        ),
    )

    val ALL: List<GameProfile> = listOf(ACTION_RPG, FPS, STRATEGY)
}
