package com.federalturk.gamletsol.core.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Telefon tarafında çalışan basit TCP istemcisi — kullanıcının elle girdiği
 * (veya QR ile taradığı) TV IP adresi/port'una bağlanır ve [send] ile her
 * yeni [CueEvent]'i gönderir.
 *
 * NOT (yol haritası, P3): [connect]'e bir `pairingCode` verilirse (G023),
 * bağlanır bağlanmaz `PAIR:<kod>` el sıkışma satırı gönderilir — TV bunu
 * reddederse (süresi geçmiş/yanlış/tekrar kullanılmış kod) sunucu bağlantıyı
 * hemen kapatır ve [connect] `null` döner. `pairingCode` verilmezse (eski
 * davranış) hiçbir el sıkışma yapılmaz — bu yalnızca karşı taraf da
 * eşleştirme istemiyorsa (`TvEventServer`'da `validatePairingCode = null`)
 * çalışır. Bağlantı koptuğunda otomatik yeniden bağlanma YOK — çağıran kod
 * (app-mobile) [isConnected] durumunu kontrol edip gerekirse yeni bir
 * [TvEventClient] oluşturmalıdır.
 *
 * G022 (TV yetenek sözleşmesi): eşleştirme adımından (varsa) SONRA, TV
 * HER ZAMAN bir [TvCapabilities] satırı gönderir — [connect] bunu okuyup
 * [capabilities]'e kaydeder; satır eksik/bozuksa (eski/uyumsuz bir sunucu,
 * ya da ağ hatası) bu da bağlantı hatası sayılır ve `connect()` `null` döner.
 *
 * G024 (TLS + fingerprint, 2026-11-09): Bağlantı ARTIK HER ZAMAN TLS
 * üzerinden kurulur (düz TCP YOK, [TvEventServer] da artık her zaman
 * `SSLServerSocket` dinler) — kanal her durumda ŞİFRELİ'dir. Sunucunun
 * KİMLİĞİ ise [expectedFingerprintSha256]'ya bağlıdır:
 * - Verilirse (QR ile tarandıysa, bkz.
 *   `com.federalturk.gamletsol.core.pairing.PairingQrPayload.fingerprintSha256`):
 *   TV'nin sunduğu sertifikanın SHA-256 özeti bu değerle TAM eşleşmelidir
 *   — eşleşmezse el sıkışma reddedilir ve `connect()` `null` döner. "Tüm
 *   sertifikaları kabul et" YOKTUR; bu gerçek bir pinning'tir (MITM'e karşı
 *   korumalı).
 * - `null` verilirse (manuel IP/port/kod girişi — kullanıcı 64 karakterlik
 *   bir fingerprint'i elle YAZAMAZ): istemci HANGİ sertifika sunulursa
 *   kabul eder. Kanal hâlâ ŞİFRELİDİR (pasif ağ dinlemesine karşı
 *   korumalı) ama TV'nin KİMLİĞİ doğrulanmaz (aktif bir MITM, kendi
 *   sertifikasını sunarak araya girebilir). Bu, dürüstlük ilkesi gereği
 *   açıkça belgelenen bir MVP sınırıdır — tam güvenlik için QR ile
 *   eşleştirme önerilir.
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
         * `host`:`port`'a TLS üzerinden bağlanır. Bağlanılamazsa, TLS el
         * sıkışması/fingerprint doğrulaması başarısız olursa, veya (bir
         * `pairingCode` verildiyse ve) TV eşleştirmeyi reddederse `null`
         * döner — çağıran kod kullanıcıya "TV'ye bağlanılamadı" gibi bir
         * durum gösterebilir; bu durumlar ayrıştırılmaz (MVP sınırı).
         */
        fun connect(
            host: String,
            port: Int,
            pairingCode: String? = null,
            expectedFingerprintSha256: String? = null,
            timeoutMs: Int = 4000,
        ): TvEventClient? {
            return try {
                val plainSocket = Socket()
                plainSocket.connect(InetSocketAddress(host, port), timeoutMs)

                // G024: düz TCP soketi, bir TLS oturumuna yükseltilir.
                // `createSocket(plainSocket, host, port, true)`, TLS'i ZATEN
                // kurulmuş bağlantının ÜZERİNE bindirir (yeni bir bağlantı
                // AÇMAZ) — `autoClose=true` sayesinde SSLSocket kapatıldığında
                // alttaki düz soket de kapanır.
                val trustManager = pinningTrustManager(expectedFingerprintSha256)
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), SecureRandom())
                }
                val sslSocket = sslContext.socketFactory
                    .createSocket(plainSocket, host, port, true) as SSLSocket
                sslSocket.soTimeout = timeoutMs
                // El sıkışmayı BURADA, açıkça tetikle — `trustManager`
                // fingerprint eşleşmezse burada bir `CertificateException`
                // fırlatır (aşağıdaki `catch` tarafından yakalanır).
                sslSocket.startHandshake()

                val client = TvEventClient(sslSocket)
                if (pairingCode != null) {
                    val accepted = try {
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
                    client.reader.readLine()
                } catch (e: Exception) {
                    null
                }
                val capabilities = TvCapabilities.decode(capsLine)
                if (capabilities == null) {
                    client.close()
                    return null
                }
                client.capabilities = capabilities

                sslSocket.soTimeout = 0
                client
            } catch (e: Exception) {
                null
            }
        }

        /**
         * [expectedFingerprintSha256] verilmişse sunucunun sertifikasının
         * SHA-256 özetini bu değerle karşılaştıran, verilmemişse HER
         * sertifikaya güvenen bir [X509TrustManager] döner — bkz. sınıf
         * dokümantasyonundaki G024 notu. İstemci sertifikası (client auth)
         * hiç KULLANILMADIĞI için [checkClientTrusted] boş bırakılmıştır.
         */
        private fun pinningTrustManager(expectedFingerprintSha256: String?): X509TrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                    if (expectedFingerprintSha256 == null) return
                    val leaf = chain.firstOrNull()
                        ?: throw CertificateException("TV sertifika zinciri boş")
                    val actual = TvTlsIdentity.sha256Hex(leaf.encoded)
                    if (!actual.equals(expectedFingerprintSha256, ignoreCase = true)) {
                        throw CertificateException(
                            "TV sertifika fingerprint'i eşleşmiyor (beklenen=$expectedFingerprintSha256, gerçek=$actual) — " +
                                "bu, araya başka bir cihazın girdiği (MITM) veya yanlış TV'ye bağlanıldığı anlamına gelebilir",
                        )
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
    }
}
