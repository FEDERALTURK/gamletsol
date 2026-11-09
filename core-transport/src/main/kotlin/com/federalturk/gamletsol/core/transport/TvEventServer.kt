package com.federalturk.gamletsol.core.transport

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
 */
class TvEventServer(
    port: Int = DEFAULT_PORT,
    private val onCueReceived: (CueEvent) -> Unit,
) {
    private val serverSocket = ServerSocket(port)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    // DÜZELTME (gerçek CI çalıştırmasında bulunan hata): `executor.shutdownNow()`
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
                clientSockets.add(client)
                executor.submit { handleClient(client) }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
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
        running.set(false)
        runCatching { serverSocket.close() }
        // Açık kalan tüm istemci soketlerini kapat — bkz. yukarıdaki
        // `clientSockets` alanının notu. Bu olmadan istemci tarafı,
        // sunucu durdurulduktan sonra bile bağlı olduğunu düşünmeye
        // devam edebiliyordu.
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PORT = 48631
    }
}
