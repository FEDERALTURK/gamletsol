package com.federalturk.gamletsol.core.transport

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Telefon tarafında çalışan basit TCP istemcisi — kullanıcının elle girdiği
 * TV IP adresi/port'una bağlanır ve [send] ile her yeni [CueEvent]'i gönderir.
 *
 * NOT (yol haritası, P3): Kimlik doğrulama/eşleştirme token'ı YOK — herhangi
 * bir cihaz, aynı IP/port'u bilen biri aynı ağdaysa bağlanabilir. Bu, G023-
 * G024'teki güvenli eşleştirme çalışması tamamlanana kadar bilinen bir
 * sınırdır (bkz. build.gradle.kts notu). Bağlantı koptuğunda otomatik
 * yeniden bağlanma YOK — çağıran kod (app-mobile) [isConnected] durumunu
 * kontrol edip gerekirse yeni bir [TvEventClient] oluşturmalıdır.
 */
class TvEventClient private constructor(private val socket: Socket) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

    val isConnected: Boolean get() = socket.isConnected && !socket.isClosed

    /** Gönderimi başarılıysa `true`, bağlantı/akış hatasında `false` döner. */
    fun send(event: CueEvent): Boolean {
        return try {
            writer.write(CueEventCodec.encode(event))
            writer.newLine()
            writer.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /**
         * `host`:`port`'a bağlanır. Bağlanılamazsa `null` döner (çağıran
         * kod kullanıcıya "TV'ye bağlanılamadı" gibi bir durum gösterebilir).
         */
        fun connect(host: String, port: Int, timeoutMs: Int = 4000): TvEventClient? {
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TvEventClient(socket)
            } catch (e: Exception) {
                null
            }
        }
    }
}
