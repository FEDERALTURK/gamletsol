package com.federalturk.gamletsol.tv

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SubtitleSink
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.SocketTvReceiverAdapter
import com.federalturk.gamletsol.core.transport.TvEventClient
import com.federalturk.gamletsol.core.transport.TvReceiverAdapter

/**
 * [SubtitleSink] uygulaması — her yeni cue'yu, kullanıcının bağlandığı TV'ye
 * ([TvReceiverAdapter] — G022 yetenek sözleşmesi) gönderir. Bu, P3/G022-G030'un
 * ilk/MVP sürümüdür: `CaptionPipeline` zaten birden çok sink'e aynı anda yayın
 * yapabildiği için bu sink, [PhoneScreenSubtitleSink] ile birlikte
 * `sinks` listesine eklenir (bkz. CaptionViewModel) — TV'ye bağlı değilken
 * [show] sessizce hiçbir şey yapmaz, telefon ekranı normal çalışmaya devam
 * eder.
 *
 * NOT (G022): Bu sınıf artık doğrudan `TvEventClient`'a değil, soyut
 * [TvReceiverAdapter] arayüzüne bağlıdır ([SocketTvReceiverAdapter] ile
 * sarılmış) — ileride başka bir TV ailesi (Samsung/LG) için ayrı bir
 * `TvReceiverAdapter` uygulaması eklenirse, bu sınıfın hiçbir satırı
 * değişmeden çalışmaya devam eder.
 *
 * NOT (dürüstlük ilkesi): Bağlantı koptuğunda otomatik yeniden bağlanma
 * YOK — [show] gönderim başarısız olursa bağlantıyı null'a çeker ve
 * [isConnected] `false` döner; kullanıcı menüden yeniden bağlanmalıdır.
 * Gerçek bir telefon↔TV çiftinde, gerçek bir Wi-Fi ağında HENÜZ denenmedi.
 */
class TvRelaySubtitleSink : SubtitleSink {
    @Volatile
    private var adapter: TvReceiverAdapter? = null

    val isConnected: Boolean
        get() = adapter?.isConnected == true

    /**
     * Bloklayan bir soket bağlantısı dener — çağıran arka plan iş
     * parçacığında (ör. Dispatchers.IO) çalıştırmalı. [pairingCode]
     * TV ekranında gösterilen eşleştirme kodudur (G023); yanlış/süresi
     * geçmiş/tekrar kullanılmış bir kod da (ya da TV'nin yetenek
     * bildirimini hiç göndermemesi, bkz. G022) bu fonksiyonun `false`
     * dönmesine yol açar (hangi nedenle reddedildiği ayrıştırılmaz).
     */
    fun connectBlocking(host: String, port: Int, pairingCode: String): Boolean {
        disconnect()
        val newClient = TvEventClient.connect(host, port, pairingCode = pairingCode) ?: return false
        adapter = SocketTvReceiverAdapter(newClient)
        return true
    }

    fun disconnect() {
        adapter?.disconnect()
        adapter = null
    }

    override fun show(cue: Cue) {
        val translated = cue.translatedText
        if (translated.isNullOrBlank()) return
        val current = adapter ?: return
        val event = CueEvent(
            originalText = cue.originalText,
            translatedText = translated,
            timestampMs = cue.createdAtMillis,
        )
        if (!current.sendSubtitle(event)) {
            // Gönderim başarısız oldu — bağlantı kopmuş olabilir, temizle.
            adapter = null
        }
    }

    override fun clear(cueId: String) {
        // NOT (G022/dürüstlük ilkesi): [TvReceiverAdapter.clear] (ve
        // altındaki TvCapabilities.supportsClear) protokolün henüz bir
        // "temizle" mesaj türü tanımlamadığını açıkça belirtiyor — bu
        // yüzden burada bilerek çağrılmıyor. TV ekranında son cue, yenisi
        // gelene kadar görünmeye devam eder (bilinen MVP sınırı).
    }
}
