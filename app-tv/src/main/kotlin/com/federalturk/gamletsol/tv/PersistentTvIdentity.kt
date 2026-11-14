package com.federalturk.gamletsol.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.federalturk.gamletsol.core.transport.TvTlsIdentity
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.security.auth.x500.X500Principal

/**
 * TV'nin kalıcı TLS kimliği (yol haritası G024/G029): telefonun "hatırlanan
 * TV" kaydı, TV'nin sertifika fingerprint'ine dayandığı için bu kimlik TV
 * yeniden başlasa da AYNI kalmalıdır.
 *
 * İki yol denenir, bu sırayla:
 *
 * 1. **AndroidKeyStore** (donanım destekli, özel anahtar dışarı çıkarılamaz).
 * 2. **Uygulamanın özel alanındaki PKCS#12 dosyası** (yazılım anahtarı,
 *    [TvTlsIdentity.loadOrCreateFromFile]).
 *
 * Her iki durumda da üretilen kimlik, GERÇEK bir TLS el sıkışmasıyla
 * ([TvTlsIdentity.selfTestHandshake]) sınanır ve ancak sınavı geçerse
 * kullanılır.
 *
 * NEDEN öz-test ve yedek yol var (2026-11-14, gerçek cihaz kanıtı):
 * Profilo 4K Android TV'de (MediaTek, Android 11) AndroidKeyStore anahtarı
 * sorunsuz ÜRETİLDİ, ama ilk TLS el sıkışmasında Conscrypt'in ihtiyaç
 * duyduğu ham RSA işlemi cihazın keymaster'ı tarafından reddedildi:
 *
 * ```
 * W CryptoUpcalls: java.security.InvalidKeyException: Keystore operation failed
 *   Caused by: android.security.KeyStoreException: Incompatible padding mode
 * W CryptoUpcalls: Could not find provider for algorithm: RSA/ECB/NoPadding
 *   at ...TvEventServer.handleClient(TvEventServer.kt:171)
 * ```
 *
 * Sonuç: telefon TV'ye HİÇ bağlanamıyordu ve bu, kimlik "başarıyla"
 * üretildiği için hiçbir yerde hata olarak görünmüyordu. İki düzeltme
 * yapıldı: (a) anahtar artık ham RSA işlemine izin veren özelliklerle
 * üretiliyor (`ENCRYPTION_PADDING_NONE`, `DIGEST_NONE`), (b) kimlik
 * kullanılmadan önce el sıkışmayla sınanıyor ve geçemezse dosya tabanlı
 * yola düşülüyor. Keymaster'ın kullanılamaz olduğu bir kez anlaşılınca bu
 * [SharedPreferences]'a yazılır ve sonraki açılışlarda pahalı anahtar
 * üretimi hiç denenmez.
 *
 * NOT: Bu dosya artık ana iş parçacığından ÇAĞRILMAMALI — anahtar üretimi ve
 * öz-test saniyeler sürebilir (bkz. MainActivity'deki arka plan yüklemesi).
 */
object PersistentTvIdentity {
    private const val TAG = "PersistentTvIdentity"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    // v2: v1 anahtarları ham RSA işlemine izin vermeyen bir spesifikasyonla
    // üretilmişti (yukarıdaki hata). Alias değiştirilerek o anahtarların
    // yeniden kullanılması engellenir.
    private const val ALIAS = "gamletsol-tv-identity-v2"
    private const val LEGACY_ALIAS = "gamletsol-tv-identity"

    private const val IDENTITY_FILE_NAME = "tv-identity.p12"

    private const val PREFS_NAME = "tv_identity"
    private const val KEY_KEYSTORE_UNUSABLE = "android_keystore_unusable"

    // Kendi-imzalı sertifikanın geçerlilik süresi — gerçek bir TV'nin
    // yıllarca aynı kimlikle çalışması beklenir.
    private const val VALIDITY_DAYS = 3650L

    fun loadOrCreate(context: Context): TvTlsIdentity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_KEYSTORE_UNUSABLE, false)) {
            val keystoreIdentity = runCatching { loadOrCreateFromAndroidKeyStore() }
                .onFailure { Log.w(TAG, "AndroidKeyStore kimliği yüklenemedi/üretilemedi", it) }
                .getOrNull()

            if (keystoreIdentity != null && TvTlsIdentity.selfTestHandshake(keystoreIdentity)) {
                Log.i(TAG, "Donanım destekli (AndroidKeyStore) TV kimliği kullanılıyor")
                return keystoreIdentity
            }

            Log.w(
                TAG,
                "AndroidKeyStore kimliği TLS el sıkışmasını geçemedi — bu cihazın keymaster'ı " +
                    "kullanılamaz kabul edilip dosya tabanlı kalıcı kimliğe geçiliyor (kalıcı karar).",
            )
            prefs.edit().putBoolean(KEY_KEYSTORE_UNUSABLE, true).apply()
            runCatching { deleteKeyStoreAlias(ALIAS) }
        }

        val file = File(context.filesDir, IDENTITY_FILE_NAME)
        val fileIdentity = runCatching { TvTlsIdentity.loadOrCreateFromFile(file) }
            .onFailure { Log.w(TAG, "Dosya tabanlı kalıcı TV kimliği yüklenemedi/üretilemedi", it) }
            .getOrNull()

        if (fileIdentity != null && TvTlsIdentity.selfTestHandshake(fileIdentity)) {
            Log.i(TAG, "Dosya tabanlı kalıcı TV kimliği kullanılıyor")
            return fileIdentity
        }

        Log.w(
            TAG,
            "Kalıcı TV kimliği kurulamadı — bu oturum için geçici (ephemeral) kimliğe düşülüyor. " +
                "Telefondaki 'hatırlanan TV' kaydı bu oturumda geçersiz olacak.",
        )
        return TvTlsIdentity.generate()
    }

    private fun loadOrCreateFromAndroidKeyStore(): TvTlsIdentity {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        // v1 anahtarı artık kullanılmıyor; yer kaplamasın.
        if (keyStore.containsAlias(LEGACY_ALIAS)) {
            runCatching { deleteKeyStoreAlias(LEGACY_ALIAS) }
        }
        if (!keyStore.containsAlias(ALIAS)) {
            generateAndStore()
        }
        // Yeni üretilen girişi görebilmek için KeyStore'u YENİDEN yükle
        // (bazı sağlayıcı uygulamalarında aynı örnek girişi hemen yansıtmaz).
        val refreshed = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val certificate = refreshed.getCertificate(ALIAS) as X509Certificate

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            // AndroidKeyStore girişleri parola KULLANMAZ — `null` doğru kullanımdır.
            init(refreshed, null)
        }

        return TvTlsIdentity.fromKeyManagers(
            certificate = certificate,
            keyManagers = keyManagerFactory.keyManagers,
            fingerprintSha256 = TvTlsIdentity.sha256Hex(certificate.encoded),
        )
    }

    private fun deleteKeyStoreAlias(alias: String) {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(alias)
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
            // DIGEST_NONE ve ENCRYPTION_PADDING_NONE (2026-11-14): TLS yığını
            // (Conscrypt) sunucu imzasını kendisi hazırlayıp anahtardan HAM
            // bir RSA işlemi (`RSA/ECB/NoPadding`) ister. Bu ikisi olmadan
            // keymaster işlemi "Incompatible padding mode" ile reddediyor ve
            // el sıkışma çöküyordu.
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS,
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
            )
            .setCertificateSubject(X500Principal("CN=gamletsol-tv"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(now)
            .setCertificateNotAfter(notAfter)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }
}
