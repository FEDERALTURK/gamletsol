package com.federalturk.gamletsol.core.transport

import java.util.Base64

/**
 * Telefondan TV'ye gönderilen tek bir altyazı/çeviri olayı.
 *
 * NOT (yol haritası, P3/G022-G030): Bu, planlanan tam "olay protokolü"nün
 * İLK/MVP sürümüdür — yalnızca metin alanları var; ileride (gerekirse)
 * sahne/oyun kimliği, Akıllı Ekran menü/envanter alanları gibi ek alanlar
 * eklenebilir (bkz. proje kapsamı, Pro katman "Akıllı Ekran").
 */
data class CueEvent(
    val originalText: String,
    val translatedText: String,
    val timestampMs: Long,
)

/**
 * [CueEvent]'i tek satırlık bir metne kodlar/çözer — satır bazlı (newline-
 * delimited) bir TCP akışında güvenle taşınabilmesi için alanlar Base64 ile
 * kodlanır (metinlerdeki `|` veya `\n` karakterleri protokolü bozmaz).
 *
 * NOT (dürüstlük ilkesi): Kasıtlı olarak basit tutuldu — JSON gibi bir
 * kütüphane eklemek yerine elle yazılmış, bağımlılıksız bir kodlama
 * kullanıldı. Şifreleme/kimlik doğrulama YOK (bkz. build.gradle.kts notu).
 */
object CueEventCodec {
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    fun encode(event: CueEvent): String {
        val original = encoder.encodeToString(event.originalText.toByteArray(Charsets.UTF_8))
        val translated = encoder.encodeToString(event.translatedText.toByteArray(Charsets.UTF_8))
        return "$original|$translated|${event.timestampMs}"
    }

    /** Geçersiz/bozuk bir satır için `null` döner — çağıran bunu yoksaymalı. */
    fun decode(line: String): CueEvent? {
        val parts = line.split("|")
        if (parts.size != 3) return null
        return try {
            val original = String(decoder.decode(parts[0]), Charsets.UTF_8)
            val translated = String(decoder.decode(parts[1]), Charsets.UTF_8)
            val timestampMs = parts[2].toLong()
            CueEvent(originalText = original, translatedText = translated, timestampMs = timestampMs)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }
}
