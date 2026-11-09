package com.federalturk.gamletsol.tv

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SubtitleSink
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.TvEventClient

/**
 * [SubtitleSink] uygulaması — her yeni cue'yu, kullanıcının bağlandığı TV'ye
 * (core-transport `TvEventClient`) gönderir. Bu, P3/G022-G030'un ilk/MVP
 * sürümüdür: `CaptionPipeline` zaten birden çok sink'e aynı anda yayın
 * yapabildiği için bu sink, [PhoneScreenSubtitleSink] ile birlikte
 * `sinks` listesine eklenir (bkz. CaptionViewModel) — TV'ye bağlı değilken
 * [show] sessizce hiçbir şey yapmaz, telefon ekranı normal çalışmaya devam
 * eder.
 *
 * NOT (dürüstlük ilkesi): Bağlantı koptuğunda otomatik yeniden bağlanma
 * YOK — [show] gönderim başarısız olursa bağlantıyı null'a çeker ve
 * [isConnected] `false` döner; kullanıcı menüden yeniden bağlanmalıdır.
 * Gerçek bir telefon↔TV çiftinde, gerçek bir Wi-Fi ağında HENÜZ denenmedi.
 */
class TvRelaySubtitleSink : SubtitleSink {
    @Volatile
    private var client: TvEventClient? = null

    val isConnected: Boolean
        get() = client?.isConnected == true

    /** Bloklayan bir soket bağlantısı dener — çağıran arka plan iş parçacığında (ör. Dispatchers.IO) çalıştırmalı. */
    fun connectBlocking(host: String, port: Int): Boolean {
        disconnect()
        val newClient = TvEventClient.connect(host, port)
        client = newClient
        return newClient != null
    }

    fun disconnect() {
        client?.close()
        client = null
    }

    override fun show(cue: Cue) {
        val translated = cue.translatedText
        if (translated.isNullOrBlank()) return
        val current = client ?: return
        val event = CueEvent(
            originalText = cue.originalText,
            translatedText = translated,
            timestampMs = cue.createdAtMillis,
        )
        if (!current.send(event)) {
            // Gönderim başarısız oldu — bağlantı kopmuş olabilir, temizle.
            client = null
        }
    }

    override fun clear(cueId: String) {
        // NOT (yol haritası, MVP sınırı): TV tarafı şu an cue'ları kendi
        // TTL'i ile silmiyor, bu yüzden burada TV'ye ayrıca bir "temizle"
        // olayı GÖNDERİLMİYOR — TV ekranında son cue, yenisi gelene kadar
        // görünmeye devam eder. Gerekirse ileride bir "clear" CueEvent
        // türü eklenebilir.
    }
}
