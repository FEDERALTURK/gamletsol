package com.federalturk.gamletsol.core.transport

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TV'nin TLS kimliği (yol haritası G024) — kendi-imzalı bir X.509 sertifika
 * + RSA anahtar çifti. `app-tv`, uygulama her açıldığında [generate] ile
 * YENİ bir kimlik üretir (kalıcı DEĞİL, Keystore'da SAKLANMAZ — bu MVP
 * sınırıdır, bkz. aşağıdaki NOT); [fingerprintSha256Hex], eşleştirme QR'ına
 * ([com.federalturk.gamletsol.core.pairing.PairingQrPayload]) gömülür ve
 * telefon tarafı ([TvEventClient.connect]) bunu PİNLER — "tüm sertifikaları
 * kabul et" YOKTUR, yalnızca bu TAM fingerprint'e sahip bir sertifika kabul
 * edilir (bkz. o sınıfın dokümantasyonu).
 *
 * Bouncy Castle (`bcprov`/`bcpkix`) kullanılır çünkü Android'in ART
 * çalışma zamanı, standart JDK'nın (OpenJDK) `sun.security.x509.*` iç
 * sınıflarını İÇERMEZ — bu yüzden bu modülde (core-transport, Android
 * SDK'sız saf Kotlin/JVM) kendi-imzalı bir sertifika üretmenin platformdan
 * bağımsız, yaygın kullanılan yolu budur. Bouncy Castle'ın kendisi de saf
 * Java'dır, bu modülün "Android SDK gerektirmez" kuralını bozmaz.
 *
 * NOT (2026-11-09 güncellemesi — G029 kimlik kalıcılığı): [generate] burada
 * KASITLI olarak EPHEMERAL (geçici, bellek-içi) kalır — bu kum havuzunda
 * Android SDK'sı olmadan çalışabilen, `core-transport`'un TÜM soket/TLS
 * testlerinin (ör. `TvEventPairingHandshakeTest`) dayandığı yoldur. Gerçek
 * `app-tv` ARTIK bu metodu çağırmıyor — onun yerine [fromKeyManagers]
 * fabrikasını, Android'in kendi `AndroidKeyStore`'unda (donanım destekli,
 * KALICI) ürettiği/yüklediği bir anahtar çifti + sertifikayla kullanıyor
 * (bkz. `app-tv`'deki `PersistentTvIdentity`). Bu sayede G029'un ("bu
 * TV'yi hatırla") beklediği "TV yeniden başlatılınca fingerprint
 * DEĞİŞMEZ" koşulu artık GERÇEKTEN karşılanıyor — eski MVP sınırı
 * (her yeniden başlatmada yeni fingerprint) kalktı. `generate()`'in
 * KENDİSİ hâlâ her çağrıda yeni bir kimlik üretir (bu, birim testler
 * için TASARLANMIŞ davranıştır, değiştirilmedi).
 */
class TvTlsIdentity private constructor(
    val certificate: X509Certificate,
    internal val keyManagers: Array<KeyManager>,
    val fingerprintSha256Hex: String,
) {
    companion object {
        private const val ALIAS = "gamletsol-tv"

        // Bu, bir sunucu TLS anahtar deposunu (yalnızca bellek içi, bu
        // çalıştırma boyunca YAŞAR, diske YAZILMAZ) açmak için KeyStore
        // API'sinin zorunlu kıldığı bir şifredir — "sır" değildir, kalıcı
        // da değildir, bu yüzden sabit bir değer olması güvenlik açığı
        // YARATMAZ (bkz. KeyStore.load/getInstance dokümantasyonu).
        private val EPHEMERAL_KEYSTORE_PASSWORD = "gamletsol-ephemeral".toCharArray()

        private const val VALIDITY_DAYS = 365L

        private const val PKCS12 = "PKCS12"

        /**
         * Kalıcı kimlik dosyasını açmak için KeyStore API'sinin ZORUNLU
         * kıldığı parola. Dosya uygulamanın kendi özel alanında durduğu için
         * bu bir "sır" değildir (dosyayı okuyabilen biri parolayı da zaten
         * koddan okuyabilir) — tıpkı yukarıdaki geçici parola gibi.
         */
        private val FILE_KEYSTORE_PASSWORD = "gamletsol-tv-identity".toCharArray()

        private const val SELF_TEST_TIMEOUT_MILLIS = 5_000
        private const val SELF_TEST_BYTE = 0x47 // 'G'


        /** Her çağrıda TAMAMEN YENİ bir kendi-imzalı sertifika/anahtar çifti üretir. */
        /**
         * Diske YAZILMAYAN, yalnız bu çalıştırma boyunca yaşayan bir kimlik.
         * Uygulama yeniden açılınca fingerprint değişir, yani telefondaki
         * "hatırlanan TV" kaydı geçersiz olur — bu yüzden yalnız son çare
         * olarak kullanılmalı (bkz. [loadOrCreateFromFile]).
         */
        fun generate(): TvTlsIdentity {
            val (privateKey, certificate) = createSelfSignedPair()
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry(ALIAS, privateKey, EPHEMERAL_KEYSTORE_PASSWORD, arrayOf<Certificate>(certificate))
            }
            return fromKeyStore(keyStore, EPHEMERAL_KEYSTORE_PASSWORD)
        }

        /**
         * KALICI yazılım kimliği: [file] (PKCS#12) varsa oradan yüklenir,
         * yoksa bir kez üretilip atomik olarak oraya yazılır. Aynı dosya
         * durdukça fingerprint sabit kalır, yani telefondaki "hatırlanan TV"
         * kaydı uygulama/TV yeniden başlasa da geçerliliğini korur.
         *
         * Neden AndroidKeyStore yerine bu da gerekiyor (2026-11-14, GERÇEK
         * cihaz kanıtı): Profilo 4K Android TV'de (MediaTek, Android 11)
         * donanım destekli AndroidKeyStore anahtarıyla TLS el sıkışması
         * `Incompatible padding mode` / `RSA/ECB/NoPadding` hatasıyla
         * BAŞARISIZ oldu — yani telefon TV'ye hiç bağlanamıyordu. Bu dosya
         * tabanlı yol, TV üreticisinin keymaster uygulamasına hiç bağımlı
         * değildir. Özel anahtar uygulamanın kendi özel alanında durur
         * (`allowBackup=false`); bu kimlik bir kullanıcı kimlik bilgisi
         * değil, yalnız yerel ağda TV'yi telefona tanıtan bir sertifikadır.
         */
        fun loadOrCreateFromFile(file: File, password: CharArray = FILE_KEYSTORE_PASSWORD): TvTlsIdentity {
            if (file.isFile) {
                val loaded = runCatching { loadFromFile(file, password) }.getOrNull()
                if (loaded != null) return loaded
            }
            val (privateKey, certificate) = createSelfSignedPair()
            val keyStore = KeyStore.getInstance(PKCS12).apply {
                load(null, null)
                setKeyEntry(ALIAS, privateKey, password, arrayOf<Certificate>(certificate))
            }
            file.parentFile?.mkdirs()
            // Önce geçici dosya, sonra yerine taşı: yazma yarıda kalırsa
            // (elektrik kesildi, disk doldu) bozuk bir kimlik dosyası kalmaz.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.outputStream().use { keyStore.store(it, password) }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            return fromKeyStore(keyStore, password)
        }

        private fun loadFromFile(file: File, password: CharArray): TvTlsIdentity {
            val keyStore = KeyStore.getInstance(PKCS12).apply {
                file.inputStream().use { load(it, password) }
            }
            return fromKeyStore(keyStore, password)
        }

        private fun fromKeyStore(keyStore: KeyStore, password: CharArray): TvTlsIdentity {
            val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, password) }
            return TvTlsIdentity(
                certificate = certificate,
                keyManagers = keyManagerFactory.keyManagers,
                fingerprintSha256Hex = sha256Hex(certificate.encoded),
            )
        }

        private fun createSelfSignedPair(): Pair<PrivateKey, X509Certificate> {
            val provider = BouncyCastleProvider()
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048, SecureRandom())
            }.generateKeyPair()

            val now = Date()
            val notAfter = Date(now.time + VALIDITY_DAYS * 24 * 60 * 60 * 1000)
            val subject = X500Name("CN=gamletsol-tv")
            // Pozitif, rastgele bir seri numarası — negatif olamaz (X.509 kuralı).
            val serial = BigInteger(128, SecureRandom())

            val builder = JcaX509v3CertificateBuilder(subject, serial, now, notAfter, subject, keyPair.public)
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keyPair.private)
            val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer))
            return keyPair.private to certificate
        }

        /**
         * [identity] ile GERÇEK bir TLS el sıkışması yapılabildiğini, yerel
         * geri döngü (loopback) üzerinde kurulan gerçek bir sunucu/istemci
         * çiftiyle doğrular.
         *
         * Neden var (2026-11-14): Bir TLS kimliğinin "üretilebilmiş olması",
         * onunla el sıkışma yapılabileceğini GÖSTERMEZ. Gerçek bir TV'de
         * AndroidKeyStore anahtarı sorunsuz üretildi ama ilk el sıkışmada
         * ham RSA işlemi reddedildi ve özellik tamamen çalışmaz hale geldi;
         * kimliği üretirken bu testi çalıştırmak, aynı sınıf hatayı
         * kullanıcıya ulaşmadan yakalar ve çalışan bir yola düşmeyi sağlar.
         *
         * İstemci tarafı kasıtlı olarak her sertifikaya güvenir: burada
         * doğrulanan şey SUNUCU anahtarının el sıkışmada kullanılabilirliği,
         * güven zinciri değil (gerçek güven, telefon tarafında fingerprint
         * pinning ile sağlanır).
         */
        fun selfTestHandshake(identity: TvTlsIdentity, timeoutMillis: Int = SELF_TEST_TIMEOUT_MILLIS): Boolean {
            var serverSocket: SSLServerSocket? = null
            return try {
                val serverContext = SSLContext.getInstance("TLS")
                    .apply { init(identity.keyManagers, null, SecureRandom()) }
                serverSocket = serverContext.serverSocketFactory
                    .createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
                serverSocket.soTimeout = timeoutMillis
                val port = serverSocket.localPort

                val serverFailure = AtomicReference<Throwable?>(null)
                val serverThread = Thread {
                    try {
                        (serverSocket.accept() as SSLSocket).use { socket ->
                            socket.soTimeout = timeoutMillis
                            socket.startHandshake()
                            socket.outputStream.apply {
                                write(SELF_TEST_BYTE)
                                flush()
                            }
                        }
                    } catch (t: Throwable) {
                        serverFailure.set(t)
                    }
                }.apply {
                    isDaemon = true
                    start()
                }

                val trustEverything = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    },
                )
                val clientContext = SSLContext.getInstance("TLS")
                    .apply { init(null, trustEverything, SecureRandom()) }
                val echoed = (clientContext.socketFactory.createSocket() as SSLSocket).use { client ->
                    client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), timeoutMillis)
                    client.soTimeout = timeoutMillis
                    client.startHandshake()
                    client.inputStream.read()
                }
                serverThread.join(timeoutMillis.toLong())
                echoed == SELF_TEST_BYTE && serverFailure.get() == null
            } catch (t: Throwable) {
                false
            } finally {
                runCatching { serverSocket?.close() }
            }
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        /**
         * [certificate] + [keyManagers] + [fingerprintSha256] ÖNCEDEN
         * başka bir yerde üretilmiş/yüklenmiş bir TLS kimliğinden
         * [TvTlsIdentity] oluşturur — [generate]'in tersine, bu fabrika
         * KENDİSİ hiçbir anahtar/sertifika ÜRETMEZ, yalnızca zaten var
         * olanları SARAR.
         *
         * Bu, `app-tv`'deki `PersistentTvIdentity`'nin gerçek
         * `android.security.keystore.KeyGenParameterSpec` ile
         * `AndroidKeyStore`'da ürettiği/yüklediği KALICI anahtar çifti +
         * kendi-imzalı sertifikayı buraya taşımasının yolu — bu modül
         * (core-transport) Android'e bağımlı OLMADIĞI için gerçek
         * `AndroidKeyStore` çağrıları burada YAŞAMAZ (bkz. AGENTS.md
         * madde 5), yalnızca sonucu kabul eder.
         */
        fun fromKeyManagers(
            certificate: X509Certificate,
            keyManagers: Array<KeyManager>,
            fingerprintSha256: String,
        ): TvTlsIdentity = TvTlsIdentity(
            certificate = certificate,
            keyManagers = keyManagers,
            fingerprintSha256Hex = fingerprintSha256,
        )
    }
}
