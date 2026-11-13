package com.federalturk.gamletsol.core.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext

private fun Socket.closeForcibly() {
    runCatching { setSoLinger(true, 0) }
    runCatching { close() }
}

/**
 * TV tarafında çalışan basit TCP sunucusu — telefondan gelen [CueEvent]
 * satırlarını dinler ve her biri için [onCueReceived] çağırır.
 *
 * NOT (yol haritası, G028 — reconnect/snapshot, 2026-11-09): Sunucu artık
 * TEK bir "birincil" (primary) istemci bağlantısı kavramını uygular (bkz.
 * [primaryClientLock]) — eşleştirme + yetenek el sıkışmasını BAŞARIYLA
 * tamamlayan her yeni bağlantı, önceki birincil bağlantıyı (varsa) ZORLA
 * kapatır ve onun yerini alır. Bu, "telefon ağ koptuktan sonra yeniden
 * bağlanır" senaryosunda eski (yarı ölü/zombi) bağlantının sonsuza kadar
 * açık kalmamasını ve iki bağlantının aynı anda cue göndermeye çalışmamasını
 * sağlar. Eski MVP davranışı (sınırsız eşzamanlı bağlantı kabul etme) artık
 * YOK — bu BİLİNÇLİ bir breaking change'dir (bu depodaki "geriye dönük
 * uyumluluk şiması yok" ilkesiyle uyumlu).
 *
 * NOT (dürüstlük ilkesi, G028 sınırı): Bu sunucu HENÜZ bir "son cue
 * anlık görüntüsünü (snapshot) yeni bağlanan istemciye geri gönder" veya
 * zaman-aşımına dayalı otomatik "temizle" (clear) mesajı İÇERMEZ —
 * protokol hâlâ TEK yönlüdür (telefon → TV, bkz. [TvCapabilities.supportsClear]
 * `false`) ve TV zaten son aldığı cue'yu ekranda tutar (yenisi gelene kadar),
 * bu yüzden "TV'de güncel durumu göster" gereksinimi dolaylı olarak zaten
 * karşılanıyor — telefon tarafı yeniden bağlandığında (yeni bir
 * [TvEventClient], yeni bir epoch ile) CANLI cue göndermeye devam eder,
 * eski bir ARABELLEK'ten (backlog) tekrar oynatma YAPILMAZ çünkü böyle bir
 * arabellek zaten YOK — her cue anında iletilir, biriktirilmez. Otomatik
 * yeniden bağlanma (bağlantı koptuğunda [TvEventClient]'ın kendiliğinden
 * yeniden denemesi) de henüz YOK, kullanıcı/`app-mobile` manuel olarak
 * yeniden bağlanmalıdır (bkz. `TvConnectDialog`) — bilinen MVP sınırı.
 * Saat farkı (clock skew) notu: [CueEvent.timestampMs] gönderenin (telefon)
 * saatidir; ileride bir TTL/temizle mekanizması eklenirse bu değer TV'nin
 * kendi `System.currentTimeMillis()`'iyle DOĞRUDAN karşılaştırılmamalı —
 * TV, bir cue'yu NE ZAMAN ALDIĞINI kendi monoton saatiyle ayrıca kaydetmeli
 * (bkz. [onCueReceived]'in çağrıldığı an, `app-tv` tarafında).
 *
 * `port = 0` verilirse işletim sistemi boş bir port seçer — bu, [boundPort]
 * üzerinden okunabilir ve gerçek loopback soket testlerinde kullanılır
 * (bkz. TvEventServerClientTest).
 *
 * [validatePairingCode] verilirse (yol haritası G023), her yeni bağlantının
 * İLK satırı `PAIR:<kod>` biçiminde olmalıdır; sunucu sonucu HER ZAMAN bir
 * yanıt satırıyla ([PAIR_OK_RESPONSE] veya [PAIR_REJECTED_RESPONSE]) bildirir
 * — istemci ([TvEventClient.connect]) bu yanıtı okuyup beklemeden bağlantıyı
 * "başarılı" sayamaz, aksi halde (yalnızca yazma başarılı olduğu için) gerçek
 * bir reddi fark etmeyebilir. Doğrulayıcı `false` döner veya satır eksik/
 * bozuksa bağlantı HİÇBİR [CueEvent] işlenmeden kapatılır. `null` geçilirse
 * (varsayılan — geriye dönük uyumluluk için) eşleştirme kontrolü YAPILMAZ ve
 * hiçbir yanıt satırı gönderilmez.
 *
 * [tlsIdentity] (yol haritası G024, 2026-11-09): ZORUNLUDUR — bu sunucu
 * ARTIK HER ZAMAN TLS üzerinden dinler (düz TCP YOK). `accept()` edilen her
 * soket aslında bir `SSLSocket`'tir; TLS el sıkışması, o soketin akışları
 * ilk kullanıldığında (handshake/CAPS okuma/yazma) ÖRTÜK olarak gerçekleşir.
 * Sunucu, istemcinin sertifikasını/kimliğini DOĞRULAMAZ (yalnızca kendi
 * kimliğini sunar) — istemci tarafındaki fingerprint pinning ([TvEventClient]
 * dokümantasyonuna bakın) GÜVENLİĞİN asıl kaynağıdır.
 *
 * NOT (yol haritası G026 — olay doğrulama ve sıra güvenliği, 2026-11-09):
 * Her bağlantı kendi [CueSequenceGuard] örneğini kullanır — bir satırdan
 * çözülen [DecodedCueEvent], bu koruyucu tarafından kabul edilmezse (tekrar/
 * replay, sırası bozulmuş/reorder) [onCueReceived] HİÇ ÇAĞRILMAZ, sessizce
 * atlanır (bkz. [CueEventCodec]/[CueSequenceGuard] dokümantasyonu).
 *
 * PROTOKOL v2 (2026-11-13, telefon↔TV senkronizasyonu):
 * - **Temizle**: `CTL:CLEAR` satırı gelince [onClear] çağrılır (telefonda
 *   altyazı ekrandan kalktı → TV'de de kalksın).
 * - **Nabız**: bir bağlantıda ilk `CTL:PING` görüldükten sonra okuma zaman
 *   aşımı [heartbeatTimeoutMillis] olarak ayarlanır; o süre hiçbir satır
 *   gelmezse bağlantı ölü sayılıp kapatılır. (İlk PING'den önce zaman aşımı
 *   yok — nabız göndermeyen eski bir telefon bağlantısı düşürülmez.)
 * - **Bağlantı durumu**: bir bağlantı birincil olunca/birincil bağlantı
 *   kapanınca [onPrimaryClientChanged] çağrılır — TV arayüzü telefon
 *   gidince eşleştirme ekranına dönebilsin diye.
 * - **Yeniden bağlanma jetonu**: [issueResumeToken] verilirse, el sıkışması
 *   başarılı olan her bağlantıya yetenek satırından sonra
 *   `RESUME_TOKEN:<jeton>` satırı gönderilir. Telefon bağlantı koparsa
 *   `PAIR:<kod>` yerine `RESUME:<jeton>` göndererek yeni kod okutmadan geri
 *   bağlanabilir; jeton [validateResumeToken] ile doğrulanır.
 */
class TvEventServer(
    port: Int = DEFAULT_PORT,
    private val tlsIdentity: TvTlsIdentity,
    private val validatePairingCode: ((String) -> Boolean)? = null,
    private val validateResumeToken: ((String) -> Boolean)? = null,
    private val issueResumeToken: (() -> String)? = null,
    private val onClear: () -> Unit = {},
    private val onPrimaryClientChanged: (connected: Boolean) -> Unit = {},
    private val heartbeatTimeoutMillis: Int = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS,
    private val onCueReceived: (CueEvent) -> Unit,
) {
    private val capabilities: TvCapabilities = TvCapabilities.CURRENT.copy(supportsResume = issueResumeToken != null)

    private val serverSocket: ServerSocket = run {
        val sslContext = SSLContext.getInstance("TLS")
        // trustManagers = null → istemci sertifikası İSTENMİYOR/doğrulanmıyor
        // (bkz. sınıf dokümantasyonu) — yalnızca sunucunun kendi kimliği
        // (keyManagers) sunuluyor.
        sslContext.init(tlsIdentity.keyManagers, null, null)
        sslContext.serverSocketFactory.createServerSocket(port)
    }
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    // bkz. sınıf dokümantasyonundaki G024-öncesi düzeltme notları (iki
    // gerçek CI/yarış hatası, G023-G024 döneminde bulundu) — [acceptLock],
    // "soketi listeye ekle" ile "stop() çalıştı mı kontrol et" adımlarını
    // atomik hale getirir.
    private val acceptLock = Any()
    private val clientSockets = CopyOnWriteArrayList<Socket>()

    // G028: "tek birincil bağlantı" muhasebesi — bkz. sınıf dokümantasyonu.
    // Aynı kilit, "yeni bir bağlantıyı birincil yap" ile "stop() tüm
    // soketleri kapatıyor" adımlarının birbirini ezmemesi için de kullanılır.
    private val primaryClientLock = Any()
    private var primaryClientSocket: Socket? = null

    val boundPort: Int get() = serverSocket.localPort

    /** Kabul döngüsünü arka plan iş parçacığında başlatır; hemen döner. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.submit {
            while (running.get()) {
                val client = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    if (running.get()) continue else break
                }
                val registered = synchronized(acceptLock) {
                    if (!running.get()) {
                        false
                    } else {
                        clientSockets.add(client)
                        true
                    }
                }
                if (!registered) {
                    // stop() bu soket kabul edilirken/listeye eklenirken
                    // zaten çalıştı — yukarıdaki not. Burada kapatılmazsa
                    // bu bağlantı sonsuza kadar açık kalırdı.
                    client.closeForcibly()
                    continue
                }
                executor.submit { handleClient(client) }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var becamePrimary = false
        var heartbeatSeen = false
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

            val validator = validatePairingCode
            if (validator != null) {
                val handshakeLine = try {
                    reader.readLine()
                } catch (e: Exception) {
                    null
                }
                // v2: "PAIR:<kod>" (QR/kod ile ilk eşleştirme) veya
                // "RESUME:<jeton>" (kopan bağlantının kod okutmadan geri gelmesi).
                val accepted = when {
                    handshakeLine == null -> false
                    handshakeLine.startsWith(PAIR_PREFIX) -> validator(handshakeLine.removePrefix(PAIR_PREFIX))
                    handshakeLine.startsWith(RESUME_PREFIX) ->
                        validateResumeToken?.invoke(handshakeLine.removePrefix(RESUME_PREFIX)) == true
                    else -> false
                }
                val response = if (accepted) PAIR_OK_RESPONSE else PAIR_REJECTED_RESPONSE
                try {
                    writer.write(response)
                    writer.newLine()
                    writer.flush()
                } catch (e: Exception) {
                    return
                }
                if (!accepted) {
                    // Eşleştirme başarısız/eksik — tek bir CueEvent satırı
                    // bile işlenmeden bağlantı kapatılır (istemci yanıtı
                    // okuyup kendisi de bağlantıyı kapatacaktır).
                    return
                }
            }

            // G022 (TV yetenek sözleşmesi): eşleştirme adımı atlandıysa/
            // başarıyla tamamlandıysa, cue döngüsüne girmeden önce TV'nin
            // desteklediği protokol sürümü/özellikleri TEK SATIR olarak
            // gönderilir — bkz. TvCapabilities dokümantasyonu. İstemci
            // (TvEventClient.connect) bu satırı okumadan bağlantıyı
            // kullanılabilir saymaz; bu yüzden bu yazma başarısız olursa
            // bağlantı burada bırakılır (istemci zaten `null` dönecektir).
            try {
                writer.write(capabilities.encode())
                writer.newLine()
                // v2: yeniden bağlanma jetonu (bkz. sınıf dokümantasyonu).
                val resumeToken = issueResumeToken?.invoke()
                if (resumeToken != null) {
                    writer.write("$RESUME_TOKEN_PREFIX$resumeToken")
                    writer.newLine()
                }
                writer.flush()
            } catch (e: Exception) {
                return
            }

            // G028: eşleştirme + yetenek el sıkışması BAŞARIYLA tamamlandı —
            // bu bağlantı artık "birincil" sayılır. Önceki birincil bağlantı
            // (varsa, ör. ağı kaybetmiş ve şimdi yeniden bağlanan AYNI
            // telefonun eski yarı-ölü soketi) ZORLA kapatılır; bu BU
            // soketi etkilemez (farklı bir nesne referansı).
            val staleSocket = synchronized(primaryClientLock) {
                val previous = primaryClientSocket
                primaryClientSocket = socket
                previous
            }
            if (staleSocket != null && staleSocket !== socket) {
                staleSocket.closeForcibly()
            }
            becamePrimary = true
            runCatching { onPrimaryClientChanged(true) }

            // G026: bu bağlantıya özgü sıra/epoch koruyucusu — bkz. sınıf
            // dokümantasyonu ve [CueSequenceGuard].
            val sequenceGuard = CueSequenceGuard()

            while (running.get()) {
                val line = try {
                    reader.readLine()
                } catch (e: SocketTimeoutException) {
                    // v2 nabız zaman aşımı: telefon nabız göndermeye başlamıştı
                    // ama artık hiçbir satır gelmiyor → bağlantı ölü sayılır.
                    null
                } catch (e: Exception) {
                    null
                } ?: break

                if (ControlMessageCodec.isControl(line)) {
                    val control = ControlMessageCodec.decode(line) ?: continue
                    if (!sequenceGuard.accept(control)) continue
                    when (control.type) {
                        ControlType.CLEAR -> runCatching { onClear() }
                        ControlType.PING -> {
                            if (!heartbeatSeen) {
                                heartbeatSeen = true
                                runCatching { socket.soTimeout = heartbeatTimeoutMillis }
                            }
                        }
                    }
                    continue
                }

                val decoded = CueEventCodec.decode(line) ?: continue
                if (!sequenceGuard.accept(decoded)) continue
                onCueReceived(decoded.event)
            }
        } finally {
            runCatching { socket.close() }
            clientSockets.remove(socket)
            // Bu bağlantı birincildi ve HÂLÂ birincil olarak kayıtlıysa
            // (yani kendisinden sonra yeni bir birincil atanmadıysa) kaydı
            // temizle — aksi halde zaten daha önce atanmış YENİ birincili
            // yanlışlıkla null'a düşürürdük.
            val wasStillPrimary = synchronized(primaryClientLock) {
                if (primaryClientSocket === socket) {
                    primaryClientSocket = null
                    true
                } else {
                    false
                }
            }
            // Yalnız GERÇEKTEN birincil olan bağlantı kapanınca bildir —
            // yerine yenisi geçtiyse (yeniden bağlanma) "koptu" deme.
            if (becamePrimary && wasStillPrimary && running.get()) {
                runCatching { onPrimaryClientChanged(false) }
            }
        }
    }

    fun stop() {
        // `running`'i false yapmak ve o ana kadar listeye eklenmiş TÜM
        // soketleri kapatmak, [acceptLock] altında ATOMİK yapılır — bkz.
        // [start]'taki Düzeltme #2 notu. Bu kilit olmadan, listeye henüz
        // eklenmemiş ama kernel seviyesinde zaten kurulmuş bir bağlantı
        // hiç kapatılmadan açık kalabilirdi.
        synchronized(acceptLock) {
            running.set(false)
            // Açık kalan tüm istemci soketlerini ZORLA (SO_LINGER(true,0)
            // → RST) kapat — normal `close()`'un göndereceği nazik FIN'i
            // istemcinin fark etmesi değişken (bazen saniyelerce) sürebilir
            // (bkz. TvEventServerClientTest "send after server stop" —
            // gerçek çalıştırmalarda iki farklı kök nedenle bulunan hata).
            clientSockets.forEach { it.closeForcibly() }
            clientSockets.clear()
        }
        synchronized(primaryClientLock) { primaryClientSocket = null }
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PORT = 48631

        /** [TvEventClient.connect]'in gönderdiği handshake satırının öneki. */
        const val PAIR_PREFIX = "PAIR:"

        /** Sunucunun handshake'i kabul ettiğini bildiren yanıt satırı. */
        const val PAIR_OK_RESPONSE = "PAIR_OK"

        /** Sunucunun handshake'i reddettiğini bildiren yanıt satırı. */
        const val PAIR_REJECTED_RESPONSE = "PAIR_REJECTED"

        /** v2: kopan bağlantının kod okutmadan geri bağlanma handshake'i. */
        const val RESUME_PREFIX = "RESUME:"

        /** v2: yetenek satırından sonra sunucunun verdiği yeniden bağlanma jetonu. */
        const val RESUME_TOKEN_PREFIX = "RESUME_TOKEN:"

        /** v2: nabız görülen bir bağlantıda bu kadar süre satır gelmezse bağlantı ölü sayılır. */
        const val DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 8_000
    }
}
