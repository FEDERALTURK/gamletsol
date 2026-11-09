package com.federalturk.gamletsol.tv

import android.content.Context
import com.federalturk.gamletsol.core.pairing.TrustedTv
import com.federalturk.gamletsol.core.pairing.TrustedTvCodec
import com.federalturk.gamletsol.core.pairing.TrustedTvRegistry

/**
 * [TrustedTv] listesinin Android `SharedPreferences` ile KALICI depolanması
 * (yol haritası G029 — "eşleştirmeyi hatırlama ve iptal"). Gerçek
 * hatırlama/unutma/tekilleştirme MANTIĞI [TrustedTvRegistry]'dedir (saf
 * Kotlin, `core-pairing`) — bu nesne yalnızca o mantığı `SharedPreferences`'a
 * BAĞLAR (bkz. AGENTS.md madde 5 — modül sınırları).
 *
 * NOT (dürüstlük ilkesi — güncelleme 2026-11-09, bkz. [TrustedTv]
 * dokümantasyonu): Bu depo doğru çalışır (eklenen/kaldırılan kayıt
 * kalıcıdır, test edilmiştir — bkz. `core-pairing`'deki
 * `TrustedTvRegistryTest`/`TrustedTvCodecTest`). Sakladığı fingerprint artık
 * GENELLİKLE TV yeniden başlatıldıktan sonra da geçerli kalır — `app-tv`
 * artık `PersistentTvIdentity` ile `AndroidKeyStore`'da KALICI bir TLS
 * kimliği kullanıyor. Bu, gerçek bir Android cihazda HENÜZ doğrulanamadı
 * (bu kum havuzunda Android SDK yok); bir AndroidKeyStore hatası olursa
 * `PersistentTvIdentity` geçici kimliğe düşer ve o durumda fingerprint
 * yine her başlatmada değişir.
 */
object TrustedTvStore {
    private const val PREFS_NAME = "trusted_tvs"
    private const val KEY_ENTRIES = "entries"

    fun load(context: Context): List<TrustedTv> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_ENTRIES, emptySet()) ?: emptySet()
        return raw.mapNotNull { TrustedTvCodec.decode(it) }
    }

    private fun persist(context: Context, trusted: List<TrustedTv>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_ENTRIES, trusted.map { TrustedTvCodec.encode(it) }.toSet())
            .apply()
    }

    /** [newTv]'yi hatırlanan TV'ler listesine ekler/günceller ve kalıcı olarak kaydeder; güncel listeyi döner. */
    fun remember(context: Context, newTv: TrustedTv): List<TrustedTv> {
        val updated = TrustedTvRegistry.remember(load(context), newTv)
        persist(context, updated)
        return updated
    }

    /** [fingerprintSha256]'ya sahip TV'yi unutur ve kalıcı olarak kaydeder; güncel listeyi döner. */
    fun forget(context: Context, fingerprintSha256: String): List<TrustedTv> {
        val updated = TrustedTvRegistry.forget(load(context), fingerprintSha256)
        persist(context, updated)
        return updated
    }
}
