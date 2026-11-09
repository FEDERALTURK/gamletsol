package com.federalturk.gamletsol.core.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun Socket.closeForcibly() {
    runCatching { setSoLinger(true, 0) }
    runCatching { close() }
}

/**
 * TV tarafında çalışan basit TCP sunucusu — telefondan gelen [CueEvent]
 * satırlarını dinler ve her biri için [onCueReceived] çağırır.
 *
 * NOT (yol haritası, P3): Tek seferde tek istemci (telefon) bağlantısı
 * düşünülmüştür — eşzamanlı çoklu bağlantı/yeniden bağlanma mantığı YOK,
 * bu MVP sürümünün bilinen bir sınırıdır. Her yeni bağlantı, önceki
 * bağlantıyı kapatmadan kabul edilir; çağıran kod (app-tv) şimdilik tek
 * bir telefonun bağlanacağını varsayar.
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
 */
class TvEventServer(
    port: Int = DEFAULT_PORT,
    private val validatePairingCode: ((String) -> Boolean)? = null,
    private val onCueReceived: (CueEvent) -> Unit,
) {
    private val serverSocket = ServerSocket(port)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    // DÜZELTME #1 (gerçek CI çalıştırmasında bulunan hata): `executor.shutdownNow()`
    // yalnızca iş parçacıklarını KESER (interrupt) — blocking bir
    // `InputStream.read()`/`readLine()` çağrısı Java'da kesintiye duyarlı
    // DEĞİLDİR, bu yüzden `stop()` çağrıldığında istemciye bağlı soket
    // kapanmadan açık kalabiliyordu ve istemci tarafı (`TvEventClient`)
    // bağlantının koptuğunu asla fark etmiyordu (bkz.
    // TvEventServerClientTest "send after server stop" — gerçek CI
    // çalıştırmasında AssertionError ile başarısız oldu, bu kum
    // havuzundaki yerel çalıştırmada rastlantıyla geçmişti). Her kabul
    // edilen istemci soketi burada tutulur ve [stop] bunların HEPSİNİ
    // açıkça kapatır.
    //
    // DÜZELTME #2 (yine gerçek yerel test çalıştırmalarında ~1/8-1/15
    // oranında bulunan GERÇEK bir yarış koşulu — SO_LINGER eklenmesinden
    // SONRA bile bazı çalıştırmalarda sorun sürdü, bu bir zamanlama sorunu
    // değil, mantık hatasıydı): istemcinin `connect()` çağrısı, sunucunun
    // `accept()`'i TAMAMLAMASINI ve soketi `clientSockets`'a EKLEMESİNİ
    // BEKLEMEZ — TCP el sıkışması çekirdek seviyesinde tamamlandığı anda
    // istemci tarafı döner, ama bu arada sunucunun kabul döngüsü henüz
    // `accept()`'ten dönmemiş/listeye eklememiş olabilir. `stop()` TAM BU
    // aralıkta çalışırsa, o soket ne `clientSockets` listesinde bulunur
    // (henüz eklenmedi) ne de `serverSocket.close()`'dan etkilenir (zaten
    // kullanıcı alanında `accept()`'ten dönmüş olabilir) — sonuç: o soket
    // HİÇBİR ZAMAN kapatılmaz, istemci sonsuza kadar "bağlı" kalır. Bunu
    // kanıtlamak için saf Java soketleriyle (Gradle/Kotlin olmadan) izole
    // bir tekrar-üretim scripti yazıldı ve senkronizasyon olmadan bu
    // yarışın gerçekten oluştuğu doğrulandı. Düzeltme: [acceptLock],
    // "soketi listeye ekle" ile "stop() çalıştı mı kontrol et" adımlarını
    // atomik hale getiriyor — ya accept() soketi listeye [stop] kilidi
    // almadan ÖNCE ekler (o zaman [stop] onu bulup kapatır), ya da [stop]
    // kilidi önce alır ve `running`'i false yapar (o zaman accept() kilidi
    // aldığında bunu görür ve soketi KENDİSİ hemen kapatır) — iki durumda
    // da soket asla gözden kaçmaz.
    private val acceptLock = Any()
    private val clientSockets = CopyOnWriteArrayList<Socket>()

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
                val code = handshakeLine?.removePrefix(PAIR_PREFIX)?.takeIf { handshakeLine.startsWith(PAIR_PREFIX) }
                val accepted = code != null && validator(code)
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
                writer.write(TvCapabilities.CURRENT.encode())
                writer.newLine()
                writer.flush()
            } catch (e: Exception) {
                return
            }

            while (running.get()) {
                val line = try {
                    reader.readLine()
                } catch (e: Exception) {
                    null
                } ?: break
                val event = CueEventCodec.decode(line) ?: continue
                onCueReceived(event)
            }
        } finally {
            runCatching { socket.close() }
            clientSockets.remove(socket)
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
    }
}
