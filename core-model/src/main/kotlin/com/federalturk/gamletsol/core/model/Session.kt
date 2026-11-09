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

/** Oyuna özgü, isteğe bağlı ayarlar (ROI ön ayarı, dublaj durumu vb.). */
data class GameProfile(
    val id: String,
    val displayName: String,
    val roiPreset: TextRegion? = null,
    val hasVoiceDubPack: Boolean = false,
)
