package com.federalturk.gamletsol.core.model

/**
 * [GameProfile] koleksiyonu için basit, bellek-içi bir depo.
 *
 * Yol haritası G031 kabul kriteri "profil silme ve sürüm değişimi güvenli"
 * burada iki garanti olarak uygulanır:
 *  1. [remove] — var olmayan bir [GameProfile.profileId] için çağrılması
 *     hata fırlatmaz, sessizce no-op'tur (bkz. `MutableMap.remove` sözleşmesi).
 *  2. [upsert] — [GameProfile.schemaVersion] güncel [GameProfile.CURRENT_SCHEMA_VERSION]'dan
 *     eskiyse, kaydetmeden önce otomatik olarak [migrate] edilir; böylece
 *     uygulama güncellendiğinde eski kayıtlı bir profil asla bozuk/eksik
 *     alanlarla geri yüklenmez.
 *
 * NOT (dürüstlük ilkesi): Bu sınıf kalıcı depolama YAPMAZ — süreç/VM hayatta
 * olduğu sürece bellekte tutar. Gerçek kalıcılık (DataStore/Room), `app-mobile`
 * tarafında bu sınıfı sarmalayan ayrı bir adaptör olarak eklenecek (henüz
 * eklenmedi); bu sınıf yalnız saf mantığı (silme/migrasyon güvenliği) temsil
 * eder ve test eder.
 */
class GameProfileStore {
    private val profilesById = linkedMapOf<String, GameProfile>()

    /** [profile] eski şemalıysa önce [migrate] edilir, sonra kaydedilir/güncellenir. */
    fun upsert(profile: GameProfile) {
        profilesById[profile.profileId] = migrate(profile)
    }

    /** [profileId] bulunamazsa sessizce hiçbir şey yapmaz — çağıran taraf hata kontrolü yapmak zorunda değildir. */
    fun remove(profileId: String) {
        profilesById.remove(profileId)
    }

    fun get(profileId: String): GameProfile? = profilesById[profileId]

    /** Eklenme sırasına göre (kararlı) tüm profiller. */
    fun all(): List<GameProfile> = profilesById.values.toList()

    fun clear() {
        profilesById.clear()
    }

    private fun migrate(profile: GameProfile): GameProfile {
        if (profile.schemaVersion >= GameProfile.CURRENT_SCHEMA_VERSION) return profile
        // Şu an tek şema (v1) var; v1'den eski bir sürüm pratikte mümkün değil
        // ama olası bir gelecek alan-dönüşümü için bu kademeli yapı korunuyor.
        // Bilinmeyen/daha eski bir sürüm, veri kaybetmeden güncel sürüm
        // numarasına yükseltilir (alan dönüşümü gerektiğinde burada eklenir).
        return profile.copy(schemaVersion = GameProfile.CURRENT_SCHEMA_VERSION)
    }
}
