package com.federalturk.gamletsol.tv

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SubtitleSink
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.SocketTvReceiverAdapter
import com.federalturk.gamletsol.core.transport.TvEventClient
import com.federalturk.gamletsol.core.transport.TvReceiverAdapter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** TV bağlantısının, kullanıcıya gösterilecek kendiliğinden değişen durumu. */
enum class TvLinkState {
    /** Bağlı (ilk bağlantı ya da başarılı yeniden bağlanma). */
    CONNECTED,

    /** Bağlantı koptu, kendiliğinden yeniden bağlanılmaya çalışılıyor. */
    RECONNECTING,

    /** Yeniden bağlanma da başarısız — kullanıcı QR ile yeniden bağlanmalı. */
    LOST,
}

/**
 * [SubtitleSink] uygulaması — her yeni cue'yu, kullanıcının bağlandığı TV'ye
 * ([TvReceiverAdapter] — G022 yetenek sözleşmesi) gönderir. `CaptionPipeline`
 * birden çok sink'e aynı anda yayın yaptığı için bu sink
 * [com.federalturk.gamletsol.ui.PhoneScreenSubtitleSink] ile birlikte çalışır;
 * TV'ye bağlı değilken [show] sessizce hiçbir şey yapmaz.
 *
 * Senkronizasyon (protokol v2, 2026-11-13):
 * - **Temizle**: telefondaki altyazının süresi dolunca (ekranda artık yazı
 *   yok) [clear] TV'ye `CTL:CLEAR` gönderir; TV'de eski altyazı kalmaz.
 * - **Nabız**: bağlıyken ~[HEARTBEAT_INTERVAL_MILLIS]'de bir, son
 *   gönderimden beri sessizlik varsa `CTL:PING` yazılır. Yazma başarısızsa
 *   bağlantı kopmuş demektir (önceden kopukluk ancak bir sonraki altyazı
 *   gönderilmeye çalışılınca fark ediliyordu).
 * - **Otomatik yeniden bağlanma**: TV ilk bağlantıda bir yeniden bağlanma
 *   jetonu verir. Bağlantı koparsa bu jetonla, artan aralıklarla
 *   ([RECONNECT_DELAYS_MILLIS]) yeniden bağlanılır — QR yeniden okutulmaz.
 *   Başarılı olunca son altyazı (hâlâ ekrandaysa) yeniden gönderilir. Tüm
 *   denemeler başarısızsa [TvLinkState.LOST] bildirilir.
 *
 * Tüm ağ işleri bu sınıfa ait TEK bir arka plan iş parçacığında yapılır;
 * OCR/çeviri hattı ağı hiç beklemez ve ana iş parçacığında ağ erişimi
 * yasağına takılınmaz.
 *
 * NOT (dürüstlük ilkesi): Protokol kısmı gerçek loopback TLS soketleriyle
 * test edildi (core-transport TvSyncProtocolTest); gerçek bir telefon↔TV
 * çiftinde, gerçek bir Wi-Fi ağında HENÜZ denenmedi.
 */
class TvRelaySubtitleSink(
    private val onLinkStateChanged: (TvLinkState) -> Unit = {},
) : SubtitleSink {

    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "gamletsol-tv-link").apply { isDaemon = true }
    }

    private val lock = Any()

    // Aşağıdaki alanlar [lock] altında okunur/yazılır.
    private var adapter: TvReceiverAdapter? = null
    private var endpoint: Endpoint? = null
    private var resumeToken: String? = null
    private var userWantsConnection = false
    private var reconnectTask: ScheduledFuture<*>? = null
    private var lastSentKey: String? = null
    private var lastSentCueId: String? = null
    private var lastEvent: CueEvent? = null
    private var lastWriteAtMillis = 0L

    private data class Endpoint(val host: String, val port: Int, val fingerprintSha256: String?)

    init {
        worker.scheduleWithFixedDelay(
            ::heartbeatTick,
            HEARTBEAT_INTERVAL_MILLIS,
            HEARTBEAT_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    val isConnected: Boolean
        get() = synchronized(lock) { adapter?.isConnected == true }

    /**
     * Bloklayan bir soket bağlantısı dener — çağıran arka plan iş
     * parçacığında (ör. Dispatchers.IO) çalıştırmalı. [pairingCode] TV
     * ekranında gösterilen eşleştirme kodudur (G023); yanlış/süresi geçmiş/
     * tekrar kullanılmış bir kod da `false` döndürür.
     *
     * [expectedFingerprintSha256] (G024): QR/NSD/hatırlanan TV yolunda TV'nin
     * TLS sertifika fingerprint'i — verilirse bağlantı MITM korumalı
     * pinning ile kurulur. Manuel giriş yolunda `null` (şifreli ama kimlik
     * pinlenmez). Otomatik yeniden bağlanma da aynı pinning'i kullanır.
     */
    fun connectBlocking(
        host: String,
        port: Int,
        pairingCode: String,
        expectedFingerprintSha256: String? = null,
    ): Boolean {
        disconnect()
        val client = TvEventClient.connect(
            host,
            port,
            pairingCode = pairingCode,
            expectedFingerprintSha256 = expectedFingerprintSha256,
        ) ?: return false
        synchronized(lock) {
            adapter = SocketTvReceiverAdapter(client)
            endpoint = Endpoint(host, port, expectedFingerprintSha256)
            resumeToken = client.resumeToken
            userWantsConnection = true
            lastSentKey = null
            lastSentCueId = null
            lastWriteAtMillis = System.currentTimeMillis()
        }
        return true
    }

    /** Kullanıcı bağlantıyı kesti: otomatik yeniden bağlanma da durur. Hemen döner. */
    fun disconnect() {
        val previous = synchronized(lock) {
            userWantsConnection = false
            reconnectTask?.cancel(false)
            reconnectTask = null
            endpoint = null
            resumeToken = null
            lastSentKey = null
            lastSentCueId = null
            lastEvent = null
            adapter.also { adapter = null }
        }
        // Soketin kapatılması (TLS kapanış mesajı ağa yazılabilir) ana iş
        // parçacığında yapılmasın diye arka plana devredilir.
        if (previous != null) worker.execute { previous.disconnect() }
    }

    override fun show(cue: Cue) {
        val translated = cue.translatedText
        if (translated.isNullOrBlank()) return
        val event = CueEvent(
            originalText = cue.originalText,
            translatedText = translated,
            timestampMs = cue.createdAtMillis,
        )
        val key = "${cue.cueId}#${cue.revision}"
        val current = synchronized(lock) {
            // Yeniden bağlanınca tekrar gönderebilmek için son altyazıyı her durumda sakla.
            lastEvent = event
            val connected = adapter ?: return
            if (key == lastSentKey) return // içerik aynı, yalnız TTL tazelendi
            lastSentKey = key
            lastSentCueId = cue.cueId
            connected
        }
        worker.execute {
            if (!isCurrent(current)) return@execute
            if (current.sendSubtitle(event)) markWritten() else onLinkBroken(current)
        }
    }

    override fun clear(cueId: String) {
        val current = synchronized(lock) {
            // Ekranda artık altyazı yok: yeniden bağlanınca eski altyazıyı geri gönderme.
            lastEvent = null
            val connected = adapter ?: return
            if (lastSentCueId != cueId) return
            lastSentKey = null
            lastSentCueId = null
            connected
        }
        worker.execute {
            if (!isCurrent(current)) return@execute
            if (!current.capabilities.supportsClear) return@execute // eski TV: son altyazı kalır
            if (current.clear(cueId)) markWritten() else onLinkBroken(current)
        }
    }

    private fun heartbeatTick() {
        val current = synchronized(lock) {
            val connected = adapter ?: return
            if (System.currentTimeMillis() - lastWriteAtMillis < HEARTBEAT_INTERVAL_MILLIS) return
            connected
        }
        if (current.heartbeat()) markWritten() else onLinkBroken(current)
    }

    private fun isCurrent(candidate: TvReceiverAdapter): Boolean = synchronized(lock) { adapter === candidate }

    private fun markWritten() {
        synchronized(lock) { lastWriteAtMillis = System.currentTimeMillis() }
    }

    /** Yalnız [worker] iş parçacığında çağrılır. */
    private fun onLinkBroken(broken: TvReceiverAdapter) {
        val canResume = synchronized(lock) {
            if (adapter !== broken) return // zaten ele alındı / kullanıcı yeniden bağlandı
            adapter = null
            lastSentKey = null
            lastSentCueId = null
            userWantsConnection && endpoint != null && resumeToken != null
        }
        broken.disconnect()
        if (canResume) {
            onLinkStateChanged(TvLinkState.RECONNECTING)
            scheduleReconnect(attempt = 0)
        } else {
            onLinkStateChanged(TvLinkState.LOST)
        }
    }

    private fun scheduleReconnect(attempt: Int) {
        synchronized(lock) {
            if (!userWantsConnection) return
            val delay = RECONNECT_DELAYS_MILLIS[attempt]
            reconnectTask = worker.schedule(Runnable { tryReconnect(attempt) }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun tryReconnect(attempt: Int) {
        val (target, token) = synchronized(lock) {
            if (!userWantsConnection || adapter != null) return
            val target = endpoint ?: return
            val token = resumeToken ?: return
            target to token
        }
        val client = TvEventClient.connect(
            target.host,
            target.port,
            expectedFingerprintSha256 = target.fingerprintSha256,
            resumeToken = token,
        )
        if (client != null) {
            val newAdapter = SocketTvReceiverAdapter(client)
            val resend = synchronized(lock) {
                if (!userWantsConnection || adapter != null) {
                    // Bu arada kullanıcı bağlantıyı kesti ya da elle yeniden bağlandı.
                    client.close()
                    return
                }
                adapter = newAdapter
                resumeToken = client.resumeToken
                reconnectTask = null
                lastWriteAtMillis = System.currentTimeMillis()
                lastEvent
            }
            onLinkStateChanged(TvLinkState.CONNECTED)
            // Ekranda hâlâ bir altyazı varsa TV'de de hemen görünsün.
            if (resend != null) {
                if (newAdapter.sendSubtitle(resend)) markWritten() else onLinkBroken(newAdapter)
            }
            return
        }
        // Jeton sunucuda tüketilmiş olabilir; yine de deneme hakkı bitene kadar
        // sürdürülür (TV kısa süre ağdan düşmüş olabilir).
        if (attempt + 1 < RECONNECT_DELAYS_MILLIS.size) {
            scheduleReconnect(attempt + 1)
        } else {
            synchronized(lock) {
                reconnectTask = null
                resumeToken = null
            }
            onLinkStateChanged(TvLinkState.LOST)
        }
    }

    /** Uygulama kapanırken: bağlantıyı kes ve iş parçacığını durdur. */
    fun shutdown() {
        disconnect()
        worker.execute { worker.shutdown() }
    }

    companion object {
        /** Nabız aralığı; TV tarafındaki zaman aşımı (8 sn) bunun birkaç katı. */
        const val HEARTBEAT_INTERVAL_MILLIS = 2_000L

        /** Yeniden bağlanma denemeleri arası beklemeler (toplam ~1,5 dk; varsayım, ölçüm değil). */
        val RECONNECT_DELAYS_MILLIS = longArrayOf(500, 1_000, 2_000, 4_000, 8_000, 15_000, 15_000, 15_000, 15_000)
    }
}
