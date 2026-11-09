package com.federalturk.gamletsol.core.model

/** Bir yakalama oturumunun (kamera açıldığından kapanana kadar) durumu. */
data class Session(
    val sessionId: String,
    val sourceEpoch: Long,
    val mode: CaptureMode,
    val startedAtMillis: Long,
    val sourceLanguageCode: String = "en",
    val targetLanguageCode: String = "tr",
)

// NOT (G031, 2026-11-09): Bu dosyada önceden burada duran basit `GameProfile`
// taslağı (yalnız id/displayName/roiPreset/hasVoiceDubPack) kaldırıldı —
// hiçbir yerde kullanılmıyordu (yalnızca bir yer tutucuydu). Gerçek
// GameProfile/TermOverride/GameProfileStore artık ayrı dosyalarda
// (GameProfile.kt, GameProfileStore.kt, SampleGameProfiles.kt) ve
// core-pipeline'daki TermPreservingTranslator ile birlikte tanımlı.
