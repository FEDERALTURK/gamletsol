package com.federalturk.gamletsol.tv

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SubtitleSink
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.SocketTvReceiverAdapter
import com.federalturk.gamletsol.core.transport.TvEventClient
import com.federalturk.gamletsol.core.transport.TvReceiverAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
 *
 * 2026-11-13 hata düzeltmeleri:
 * - Gönderim artık çağıranın iş parçacığında değil, bu sınıfa ait tek bir
 *   arka plan iş parçacığında yapılır. Eskiden hat ana iş parçacığında
 *   çalıştığı için soket yazımı Android'in ana-iş-parçacığında-ağ yasağına
 *   takılıyor, `send` sessizce `false` dönüyor ve ilk altyazıda bağlantı
 *   düşürülüyordu (arayüz ise "bağlı" demeye devam ediyordu). Yavaş bir
 *   Wi-Fi'de de OCR/çeviri hattı artık ağ gönderimini beklemez.
 * - Aynı cue'nun içeriği değişmemiş yenilemeleri (TTL tazeleme, saniyede
 *   birkaç kez) TV'ye tekrar tekrar gönderilmez; yalnız yeni cue veya yeni
 *   revizyon gönderilir.
 * - Bağlantı koparsa [onConnectionLost] çağrılır (arayüz durumu güncellenir).
 */
class TvRelaySubtitleSink(
    private val onConnectionLost: () -> Unit = {},
) : SubtitleSink {

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gamletsol-tv-send").apply { isDaemon = true }
    }

    @Volatile
    private var lastSentKey: String? = null

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
     *
     * [expectedFingerprintSha256] (G024): QR ile tarandıysa TV'nin TLS
     * sertifika fingerprint'i — verilirse bağlantı gerçek MITM korumalı
     * pinning ile kurulur (fingerprint uyuşmazsa reddedilir). Manuel
     * giriş yolunda `null` geçilir — bağlantı yine TLS üzerinden
     * ŞİFRELİDİR ama TV'nin kimliği PİNLENMEZ (bkz. `TvEventClient.connect`
     * dokümantasyonu).
     */
    fun connectBlocking(
        host: String,
        port: Int,
        pairingCode: String,
        expectedFingerprintSha256: String? = null,
    ): Boolean {
        disconnect()
        lastSentKey = null
        val newClient = TvEventClient.connect(
            host,
            port,
            pairingCode = pairingCode,
            expectedFingerprintSha256 = expectedFingerprintSha256,
        ) ?: return false
        adapter = SocketTvReceiverAdapter(newClient)
        return true
    }

    /**
     * Bağlantıyı bırakır. Soketin kapatılması (TLS kapanış mesajı ağa
     * yazılabilir) ana iş parçacığında yapılmasın diye gönderim iş
     * parçacığına devredilir; bu fonksiyon hemen döner.
     */
    fun disconnect() {
        val previous = adapter ?: return
        adapter = null
        lastSentKey = null
        sendExecutor.execute { previous.disconnect() }
    }

    override fun show(cue: Cue) {
        val translated = cue.translatedText
        if (translated.isNullOrBlank()) return
        val current = adapter ?: return

        val key = "${cue.cueId}#${cue.revision}"
        if (key == lastSentKey) return // içerik aynı, yalnız TTL tazelendi
        lastSentKey = key

        val event = CueEvent(
            originalText = cue.originalText,
            translatedText = translated,
            timestampMs = cue.createdAtMillis,
        )
        sendExecutor.execute {
            // Bu arada kullanıcı bağlantıyı kesmiş/yenilemiş olabilir.
            if (adapter !== current) return@execute
            if (!current.sendSubtitle(event)) {
                // Gönderim başarısız oldu — bağlantı kopmuş olabilir, temizle.
                if (adapter === current) {
                    adapter = null
                    lastSentKey = null
                    current.disconnect()
                    onConnectionLost()
                }
            }
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
