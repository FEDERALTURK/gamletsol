package com.federalturk.gamletsol.core.transport

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

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

        /** Her çağrıda TAMAMEN YENİ bir kendi-imzalı sertifika/anahtar çifti üretir. */
        fun generate(): TvTlsIdentity {
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

            val fingerprint = sha256Hex(certificate.encoded)

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry(ALIAS, keyPair.private, EPHEMERAL_KEYSTORE_PASSWORD, arrayOf<java.security.cert.Certificate>(certificate))
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, EPHEMERAL_KEYSTORE_PASSWORD)
            }

            return TvTlsIdentity(
                certificate = certificate,
                keyManagers = keyManagerFactory.keyManagers,
                fingerprintSha256Hex = fingerprint,
            )
        }

        /** [bytes]'ın SHA-256 özetini küçük harf hex string olarak döner — QR/log/karşılaştırma için ortak format. */
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
