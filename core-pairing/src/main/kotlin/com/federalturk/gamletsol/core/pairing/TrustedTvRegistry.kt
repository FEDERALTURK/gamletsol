package com.federalturk.gamletsol.core.pairing

/**
 * [TrustedTv] listeleri üzerinde çalışan saf, bağımsız (stateless)
 * fonksiyonlar (yol haritası G029). KASITLI olarak sınıf/nesne durumu
 * TUTMAZ — çağıran kod (örn. `app-mobile`'daki `TrustedTvStore`,
 * `SharedPreferences`'tan okuduğu listeyi bu fonksiyonlara verir, sonucu
 * geri yazar) kalıcılığın sahibidir; bu, mantığı Android'den tamamen
 * bağımsız ve birim test edilebilir tutar (bkz. AGENTS.md madde 5).
 */
object TrustedTvRegistry {
    /**
     * [newTv]'yi [trusted] listesine ekler. Aynı [TrustedTv.fingerprintSha256]'ya
     * sahip bir kayıt ZATEN varsa (ör. kullanıcı aynı TV'yi tekrar "hatırla"
     * dedi — belki adını değiştirdi ya da IP'si değişti), o kayıt YENİSİYLE
     * DEĞİŞTİRİLİR (tekilleştirme fingerprint'e göre yapılır, isim/host'a
     * göre DEĞİL — fingerprint gerçek kimliktir).
     */
    fun remember(trusted: List<TrustedTv>, newTv: TrustedTv): List<TrustedTv> =
        trusted.filterNot { it.fingerprintSha256.equals(newTv.fingerprintSha256, ignoreCase = true) } + newTv

    /**
     * [fingerprintSha256]'ya sahip kaydı listeden kaldırır — yol haritasının
     * G029 kabul kriteri olan "cihaz iptalinde anahtar erişimi biter"
     * davranışı budur: kayıt silindikten sonra [isTrusted] bu fingerprint
     * için bir daha ASLA `true` dönmez (yeniden "hatırlanmadığı" sürece).
     */
    fun forget(trusted: List<TrustedTv>, fingerprintSha256: String): List<TrustedTv> =
        trusted.filterNot { it.fingerprintSha256.equals(fingerprintSha256, ignoreCase = true) }

    /** [fingerprintSha256]'ya sahip, hatırlanan bir TV var mı. */
    fun isTrusted(trusted: List<TrustedTv>, fingerprintSha256: String): Boolean =
        trusted.any { it.fingerprintSha256.equals(fingerprintSha256, ignoreCase = true) }

    /** [fingerprintSha256]'ya sahip hatırlanan kaydı döner, yoksa `null`. */
    fun find(trusted: List<TrustedTv>, fingerprintSha256: String): TrustedTv? =
        trusted.firstOrNull { it.fingerprintSha256.equals(fingerprintSha256, ignoreCase = true) }
}
