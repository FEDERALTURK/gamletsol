package com.federalturk.gamletsol.tv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.federalturk.gamletsol.core.transport.TvTlsIdentity
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.security.auth.x500.X500Principal

/**
 * G029 kimlik kalıcılığı (2026-11-09): [TvTlsIdentity]'nin — ve dolayısıyla
 * TV'nin TLS sertifika fingerprint'inin — Android'in kendi `AndroidKeyStore`
 * sağlayıcısında (sistem genelinde, uygulama verisiyle BİRLİKTE silinmez,
 * donanım destekli olduğunda private key hiç dışarı ÇIKMAZ) KALICI olarak
 * saklanmasını sağlar. `app-tv` her açıldığında artık
 * [com.federalturk.gamletsol.core.transport.TvTlsIdentity.generate] (geçici,
 * her çağrıda YENİ) yerine [loadOrCreate] çağırır — bu, roadmap'in G029'da
 * istediği "bu TV'yi hatırla" özelliğinin asıl GEREKSİNDİĞİ koşulu
 * (fingerprint TV yeniden başlatılınca DEĞİŞMEMELİ) karşılar.
 *
 * NASIL ÇALIŞIR:
 * 1. [ALIAS] altında zaten bir anahtar çifti var mı diye `AndroidKeyStore`
 *    kontrol edilir.
 * 2. Yoksa, `KeyGenParameterSpec` ile YENİ bir RSA anahtar çifti ÜRETİLİR —
 *    Android'in kendisi, bu çağrı sırasında otomatik olarak kendi-imzalı
 *    bir X.509 sertifika da oluşturup aynı girişe ekler (resmi, belgeli
 *    bir Android davranışı — `setCertificateSubject`/`setCertificateSerialNumber`/
 *    `setCertificateNotBefore`/`setCertificateNotAfter` bu sertifikanın
 *    alanlarını belirler).
 * 3. Sertifika + bu girişten türetilen `KeyManager`'lar okunur ve
 *    [TvTlsIdentity.fromKeyManagers] ile sarılır.
 *
 * Özel/gizli anahtar hiçbir zaman bu kodun eline GEÇMEZ (dışa aktarılmaz) —
 * yalnızca `AndroidKeyStore`'un kendi `KeyManagerFactory` entegrasyonu
 * üzerinden TLS el sıkışmasında KULLANILIR. Bu, AGENTS.md madde 3'ün
 * ("imzalama anahtarı koda/loglara/versiyon kontrolüne asla yazılmaz")
 * ruhuna tam uyumludur — anahtar zaten hiçbir zaman bir dosyaya/log'a
 * YAZILABİLECEK bir biçimde ortaya çıkmaz.
 *
 * NOT (dürüstlük ilkesi — gerçek sınır): Bu sınıf, `android.security.keystore`
 * ve `AndroidKeyStore` sağlayıcısının RESMİ, belgeli davranışına dayanır
 * (Android Developers dokümantasyonu) ama bu kum havuzunda bir Android SDK/
 * cihaz OLMADIĞI için GERÇEKTEN ÇALIŞTIRILAMADI/TEST EDİLEMEDİ — ne gerçek
 * bir `AndroidKeyStore` çağrısı yapıldı, ne de üretilen anahtarla gerçek bir
 * TLS el sıkışmasının (farklı cihaz/OEM/Android sürümlerinde) sorunsuz
 * çalıştığı doğrulandı (AndroidKeyStore-destekli anahtarlarla özel TLS
 * sunucuları kurmak, bazı OEM/Android sürüm kombinasyonlarında bilinen
 * uyumluluk tuhaflıkları olan bir alandır). Bu yüzden [loadOrCreate],
 * HERHANGİ bir istisna durumunda (ör. bu cihazda/OEM'de beklenmeyen bir
 * AndroidKeyStore davranışı) sessizce [TvTlsIdentity.generate]'e (geçici,
 * bellek-içi kimlik) DÜŞER — TV hiçbir zaman bu yüzden çökmez ya da
 * kullanılamaz hale gelmez, yalnızca o oturumda "hatırlama" kalıcılığı
 * kaybolur (kullanıcı QR'ı yeniden taramak zorunda kalır). Bu düşüş
 * [Log.w] ile loglanır.
 */
object PersistentTvIdentity {
    private const val TAG = "PersistentTvIdentity"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "gamletsol-tv-identity"

    // Kendi-imzalı sertifikanın geçerlilik süresi — roadmap'in "kalıcı
    // güven" beklentisiyle uyumlu, uzun bir süre (gerçek bir TV'nin yıllarca
    // aynı kimlikle çalışması beklenir). Süre dolduğunda [loadOrCreate]
    // davranışı bu sınıfta henüz ELE ALINMADI (bilinen, dar bir MVP sınırı —
    // 10 yıl sonrasının sorunu).
    private const val VALIDITY_DAYS = 3650L

    /**
     * `AndroidKeyStore`'da kalıcı olarak saklanan bir kimlik varsa onu
     * yükler; yoksa YENİ bir tane üretip saklar ve onu döner. Bir
     * istisna oluşursa (bkz. sınıf dokümantasyonundaki dürüstlük notu),
     * GERİYE dönüp geçici (ephemeral) bir [TvTlsIdentity.generate]'e düşer
     * — bu metod pratikte asla fırlatmaz.
     */
    fun loadOrCreate(): TvTlsIdentity {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(ALIAS)) {
                generateAndStore()
            }
            // Yeni üretilen girişi görebilmek için KeyStore'u YENİDEN yükle
            // (bazı sağlayıcı uygulamalarında aynı örnek, girişi hemen
            // yansıtmayabilir — tazelenmiş bir örnek kullanmak daha güvenli).
            val refreshed = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val certificate = refreshed.getCertificate(ALIAS) as X509Certificate

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                // AndroidKeyStore girişleri parola KULLANMAZ — `null` doğru kullanımdır.
                init(refreshed, null)
            }

            val fingerprint = TvTlsIdentity.sha256Hex(certificate.encoded)
            TvTlsIdentity.fromKeyManagers(certificate, keyManagerFactory.keyManagers, fingerprint)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "AndroidKeyStore'da kalıcı TV kimliği yüklenemedi/üretilemedi — " +
                    "bu oturum için geçici (ephemeral) bir kimliğe düşülüyor " +
                    "(hatırlanan TV'ler bu oturumda geçersiz olacak)",
                e,
            )
            TvTlsIdentity.generate()
        }
    }

    private fun generateAndStore() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
        val now = Date()
        val notAfter = Date(now.time + VALIDITY_DAYS * 24 * 60 * 60 * 1000)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(2048)
            // Farklı TLS sürümlerinin/cipher suite'lerin el sıkışma
            // imzasında kullanabileceği özetlerin GENİŞ bir alt kümesi —
            // tam olarak hangisinin gerçekte gerekeceği, ancak GERÇEK bir
            // istemci/sunucu el sıkışmasında ortaya çıkar (bkz. sınıfın
            // dürüstlük notu, bu kum havuzunda denenemedi).
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=gamletsol-tv"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(now)
            .setCertificateNotAfter(notAfter)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }
}
