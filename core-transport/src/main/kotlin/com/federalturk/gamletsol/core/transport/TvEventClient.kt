package com.federalturk.gamletsol.core.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Telefon tarafında çalışan basit TCP istemcisi — kullanıcının elle girdiği
 * TV IP adresi/port'una bağlanır ve [send] ile her yeni [CueEvent]'i gönderir.
 *
 * NOT (yol haritası, P3): [connect]'e bir `pairingCode` verilirse (G023),
 * bağlanır bağlanmaz `PAIR:<kod>` el sıkışma satırı gönderilir — TV bunu
 * reddederse (süresi geçmiş/yanlış/tekrar kullanılmış kod) sunucu bağlantıyı
 * hemen kapatır ve [connect] `null` döner. `pairingCode` verilmezse (eski
 * davranış) hiçbir el sıkışma yapılmaz — bu yalnızca karşı taraf da
 * eşleştirme istemiyorsa (`TvEventServer`'da `validatePairingCode = null`)
 * çalışır. Kanal hâlâ ŞİFRELENMEMİŞ — bu yalnızca KİMLİK DOĞRULAMA sağlar,
 * G024'teki TLS/fingerprint doğrulaması hâlâ eklenmedi (bkz. core-pairing
 * build.gradle.kts notu). Bağlantı koptuğunda otomatik yeniden bağlanma
 * YOK — çağıran kod (app-mobile) [isConnected] durumunu kontrol edip
 * gerekirse yeni bir [TvEventClient] oluşturmalıdır.
 *
 * G022 (TV yetenek sözleşmesi): eşleştirme adımından (varsa) SONRA, TV
 * HER ZAMAN bir [TvCapabilities] satırı gönderir — [connect] bunu okuyup
 * [capabilities]'e kaydeder; satır eksik/bozuksa (eski/uyumsuz bir sunucu,
 * ya da ağ hatası) bu da bağlantı hatası sayılır ve `connect()` `null` döner.
 */
class TvEventClient private constructor(private val socket: Socket) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

    // [connect]'teki handshake yanıtını VE yetenek sözleşmesi satırını
    // okumak için kullanılır — bunlardan sonra TV telefona hiçbir satır
    // göndermez (protokol tek yönlüdür, telefon → TV), bu yüzden bu
    // reader'ın normal [send] akışında bir karşılığı yoktur.
    private val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

    val isConnected: Boolean get() = socket.isConnected && !socket.isClosed

    /** [connect] sırasında TV'den alınan, bağlantı boyunca değişmeyen yetenek bildirimi. */
    lateinit var capabilities: TvCapabilities
        private set

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
         * `host`:`port`'a bağlanır. Bağlanılamazsa veya (bir `pairingCode`
         * verildiyse ve) TV eşleştirmeyi reddederse `null` döner — çağıran
         * kod kullanıcıya "TV'ye bağlanılamadı" gibi bir durum gösterebilir;
         * iki durum da aynı şekilde raporlanır, ayrıştırılmaz (MVP sınırı).
         */
        fun connect(host: String, port: Int, pairingCode: String? = null, timeoutMs: Int = 4000): TvEventClient? {
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val client = TvEventClient(socket)
                if (pairingCode != null) {
                    val accepted = try {
                        socket.soTimeout = timeoutMs
                        client.writer.write("${TvEventServer.PAIR_PREFIX}$pairingCode")
                        client.writer.newLine()
                        client.writer.flush()
                        // Sunucunun handshake'i GERÇEKTEN doğruladığını
                        // kanıtlayan yanıtı bekle — yalnızca yazmanın
                        // başarılı olması, sunucunun kodu kabul ettiği
                        // anlamına gelmez (bkz. sınıf dokümantasyonu).
                        client.reader.readLine() == TvEventServer.PAIR_OK_RESPONSE
                    } catch (e: Exception) {
                        false
                    } finally {
                        socket.soTimeout = 0
                    }
                    if (!accepted) {
                        client.close()
                        return null
                    }
                }

                // G022: eşleştirme adımı atlandıysa/başarıyla tamamlandıysa,
                // TV'nin bir sonraki (ve tek yönlü protokolde TV'den gelecek
                // SON) satırı her zaman yetenek bildirimi olmalıdır. Bu
                // satır eksik/bozuksa bağlantı kullanılamaz sayılır — eski/
                // uyumsuz bir sunucuyla (bu protokol sürümünden önceki) veya
                // ağ hatasıyla aynı şekilde ele alınır (MVP sınırı, ayrıştırılmaz).
                val capsLine = try {
                    socket.soTimeout = timeoutMs
                    client.reader.readLine()
                } catch (e: Exception) {
                    null
                } finally {
                    socket.soTimeout = 0
                }
                val capabilities = TvCapabilities.decode(capsLine)
                if (capabilities == null) {
                    client.close()
                    return null
                }
                client.capabilities = capabilities

                client
            } catch (e: Exception) {
                null
            }
        }
    }
}
