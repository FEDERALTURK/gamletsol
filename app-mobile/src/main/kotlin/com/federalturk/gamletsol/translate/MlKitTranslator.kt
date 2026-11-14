package com.federalturk.gamletsol.translate

import com.federalturk.gamletsol.core.pipeline.Translator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit Translation (cihaz-üstü) tabanlı [Translator]. Yol haritasının
 * "Gizlilik ilkesi" ve "çekirdek ürün (P0-P5) tamamen cihaz-üstü çalışır"
 * kararına karşılık gelir: model bir kez indirildikten sonra çeviri
 * tamamen çevrimdışı çalışır, ayrıca bir ağ çağrısı yapılmaz.
 *
 * NOT: Şimdilik yalnız en<->tr sabit; genel dil desteği gerekirse
 * `TranslateLanguage.fromLanguageTag(...)` ile ISO 639-1 kodundan
 * `TranslateLanguage` sabitine eşleme eklenmeli.
 */
class MlKitTranslator : Translator {

    private val englishToTurkish = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build(),
    )

    /**
     * Modeli indirir (henüz indirilmediyse). [requireWifiOnly], yol
     * haritasındaki maliyet/veri kullanımı farkındalığıyla varsayılan olarak
     * açıktır — kullanıcı isterse kapatabilir (ileride ayarlar ekranı).
     */
    /**
     * Modeli indirir (henüz indirilmediyse). İlk denemede Wi-Fi şartı arar;
     * başarısız olursa (Wi-Fi yok / model büyük) ikinci denemede herhangi bir
     * ağ bağlantısıyla yeniden dener — kullanıcının mobil verideyken hiç çeviri
     * alamaması önlenir (2026-11-14 düzeltmesi).
     */
    suspend fun ensureModelDownloaded() {
        // Önce Wi-Fi ile dene (kullanıcının mobil verisini korur).
        val wifiResult = runCatching { downloadModel(requireWifi = true) }
        if (wifiResult.isSuccess) return

        // Wi-Fi yoksa herhangi bir ağ ile yeniden dene.
        downloadModel(requireWifi = false)
    }

    private suspend fun downloadModel(requireWifi: Boolean) {
        val conditionsBuilder = DownloadConditions.Builder()
        if (requireWifi) conditionsBuilder.requireWifi()

        suspendCancellableCoroutine<Unit> { continuation ->
            englishToTurkish.downloadModelIfNeeded(conditionsBuilder.build())
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
        require(sourceLanguageCode == "en" && targetLanguageCode == "tr") {
            "MlKitTranslator şu an yalnız en->tr destekliyor, istenen: $sourceLanguageCode->$targetLanguageCode"
        }
        return suspendCancellableCoroutine { continuation ->
            englishToTurkish.translate(text)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    fun close() {
        englishToTurkish.close()
    }
}
