package com.federalturk.gamletsol.core.transport

import java.util.Base64

/**
 * Telefondan TV'ye gönderilen tek bir altyazı/çeviri olayının MANTIKSAL
 * (domain) içeriği — yol haritası/boru hattı seviyesinde bu üç alan
 * yeterlidir; "bu olay kaçıncı sırada, hangi bağlantı oturumuna ait"
 * bilgisi BİLEREK burada DEĞİL, [DecodedCueEvent]/[CueEventCodec]'in
 * tel (wire) seviyesinde taşınır (bkz. G026 notu aşağıda) — böylece
 * [TvReceiverAdapter]/`TvRelaySubtitleSink` gibi bu sınıfı zaten kullanan
 * kod, sıra/epoch kavramından habersiz kalabilir (bu, bağlantının kendi
 * iç muhasebesidir, pipeline'ın değil).
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
 * Telden (wire) okunan bir [CueEvent] ile onu taşıyan bağlantı-seviyesi
 * zarf bilgisi (G026 — [sequence]/[epochId], bkz. [CueEventCodec]/
 * [CueSequenceGuard] dokümantasyonu).
 */
data class DecodedCueEvent(
    val event: CueEvent,
    val sequence: Long,
    val epochId: String,
)

/**
 * [CueEvent]'i (+ bağlantı-seviyesi [sequence]/[epochId] zarfını) tek
 * satırlık bir metne kodlar/çözer — satır bazlı (newline-delimited) bir
 * TCP akışında güvenle taşınabilmesi için metin alanları Base64 ile
 * kodlanır (metinlerdeki `|` veya `\n` karakterleri protokolü bozmaz).
 *
 * NOT (dürüstlük ilkesi): Kasıtlı olarak basit tutuldu — JSON gibi bir
 * kütüphane eklemek yerine elle yazılmış, bağımlılıksız bir kodlama
 * kullanıldı. Şifreleme G024'te eklendi (TLS, bu dosyanın kapsamı dışında);
 * bu kodlayıcı yalnızca ALAN BİÇİMİNİ doğrular.
 *
 * [sequence]/[epochId] (yol haritası G026, 2026-11-09): Her [TvEventClient]
 * bağlantısı rastgele bir [epochId] (bağlantı/oturum kimliği) üretir ve
 * gönderdiği her olayda 0'dan başlayıp monoton artan bir [sequence] taşır
 * (bkz. [TvEventClient.send]). [TvEventServer] bunu ([CueSequenceGuard]
 * ile) tekrar oynatılan (replay) veya sırası bozulmuş (reorder) olayları
 * reddetmek, ve G028'in (yeniden bağlanma) "eski oturumun olayları yeni
 * oturuma sızmaz" gereksinimini karşılamak için kullanır.
 *
 * G026 (olay doğrulama ve sıra güvenliği, 2026-11-09): [decode] aşağıdaki
 * sınırları uygular — bunlar ağdan gelen rastgele/bozuk/kötü niyetli veriye
 * karşı bir ilk savunma hattıdır:
 * - **Boyut sınırı:** Ham satır [MAX_LINE_LENGTH] karakteri aşarsa hiç
 *   ayrıştırılmadan reddedilir (bellek tüketimi/DoS'a karşı).
 * - **Şema:** Tam olarak 5 `|`-ayrılmış alan beklenir (eski 3-alanlı biçim,
 *   G026 öncesi, artık kabul EDİLMEZ — breaking change, bu depodaki genel
 *   "geriye dönük uyumluluk şiması yok" ilkesiyle uyumlu).
 * - **Metin uzunluğu:** Çözülmüş metin alanlarının her biri
 *   [MAX_TEXT_LENGTH] UTF-16 birimini aşarsa reddedilir (bir altyazı/çeviri
 *   satırı için gerçekçi olmayan bir boyut).
 * - **sequence/epochId:** `sequence` negatif olamaz; `epochId` boş olamaz
 *   ve [MAX_EPOCH_ID_LENGTH] karakteri aşamaz.
 *
 * NOT (dürüstlük ilkesi — HTML/kod çalıştırma): `app-tv`'nin kendisi hiçbir
 * zaman bu metni bir WebView/HTML motoruna VERMEDİ — [CueEvent.originalText]/
 * [CueEvent.translatedText] her zaman düz Compose `Text` olarak çizilir
 * (bkz. `app-tv/MainActivity.kt`), bu yüzden `<script>` gibi bir dizi
 * zaten ÇALIŞTIRILAMAZ, sadece düz metin olarak görünür. Yol haritasının
 * G026 kabul kriterindeki "metin HTML çalıştırmaz" ifadesi bu açıdan
 * baştan itibaren doğruydu; burada eklenen asıl yeni koruma boyut/şema/
 * sıra sınırlarıdır.
 */
object CueEventCodec {
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    /** Ham (Base64 öncesi) ham satırın en fazla izin verilen uzunluğu. */
    const val MAX_LINE_LENGTH = 20_000

    /** Çözülmüş bir metin alanının en fazla izin verilen uzunluğu (UTF-16 birimi). */
    const val MAX_TEXT_LENGTH = 4_000

    /** [CueEvent] zarfındaki epoch kimliğinin en fazla izin verilen uzunluğu. */
    const val MAX_EPOCH_ID_LENGTH = 64

    fun encode(event: CueEvent, sequence: Long, epochId: String): String {
        val original = encoder.encodeToString(event.originalText.toByteArray(Charsets.UTF_8))
        val translated = encoder.encodeToString(event.translatedText.toByteArray(Charsets.UTF_8))
        return "$original|$translated|${event.timestampMs}|$sequence|$epochId"
    }

    /** Geçersiz/bozuk/sınırı aşan bir satır için `null` döner — çağıran bunu yoksaymalı. */
    fun decode(line: String): DecodedCueEvent? {
        if (line.isEmpty() || line.length > MAX_LINE_LENGTH) return null
        val parts = line.split("|")
        if (parts.size != 5) return null
        return try {
            val original = String(decoder.decode(parts[0]), Charsets.UTF_8)
            val translated = String(decoder.decode(parts[1]), Charsets.UTF_8)
            if (original.length > MAX_TEXT_LENGTH || translated.length > MAX_TEXT_LENGTH) return null

            val timestampMs = parts[2].toLong()
            val sequence = parts[3].toLong()
            if (sequence < 0) return null

            val epochId = parts[4]
            if (epochId.isBlank() || epochId.length > MAX_EPOCH_ID_LENGTH) return null

            DecodedCueEvent(
                event = CueEvent(originalText = original, translatedText = translated, timestampMs = timestampMs),
                sequence = sequence,
                epochId = epochId,
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }
}
