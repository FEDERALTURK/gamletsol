package com.federalturk.gamletsol.core.pairing

import java.util.Base64

/**
 * Telefonun "hatırladığı" (yol haritası G029 — "eşleştirmeyi hatırlama ve
 * iptal") bir TV'nin kaydı. `app-mobile`, GERÇEK bir fingerprint'e sahip
 * bir bağlantı (QR tarama ya da NSD keşfi — ikisi de [PairingQrPayload]/
 * `TvNsdAttributes` üzerinden gerçek bir fingerprint taşır; manuel giriş
 * YOLU fingerprint taşımadığı için "hatırlanamaz", bkz. [TvEventClient]
 * dokümantasyonu) başarıyla kurulduğunda, kullanıcı açıkça "Bu TV'yi
 * hatırla" seçerse bu kaydı oluşturur.
 *
 * NOT (dürüstlük ilkesi — güncelleme 2026-11-09): Bu kaydın GÜVENLİK değeri
 * artık GERÇEKTİR — `app-tv`, [com.federalturk.gamletsol.core.transport.TvTlsIdentity]'yi
 * elde etmek için `PersistentTvIdentity.loadOrCreate()`'i çağırıyor; bu,
 * Android'in kendi `AndroidKeyStore`'unda KALICI bir anahtar çifti/sertifika
 * kullanır, böylece [fingerprintSha256] TV YENİDEN BAŞLATILDIKTAN SONRA DA
 * GEÇERLİ kalır (eski sınır — her başlatmada yeni fingerprint — kalktı).
 * Bu kayıt mekanizmasının kendisi (hatırlama/unutma/listeleme) doğru çalışır
 * ve test edilmiştir.
 *
 * GERÇEK, AÇIK KALAN SINIR: `PersistentTvIdentity`'nin gerçek bir Android
 * cihazda/`AndroidKeyStore`'da çalıştığı bu kum havuzunda DOĞRULANAMADI —
 * yalnızca Android'in resmi, belgeli `KeyGenParameterSpec` davranışına
 * dayanır. `PersistentTvIdentity`, herhangi bir AndroidKeyStore hatasında
 * sessizce geçici (ephemeral) bir kimliğe düşer — bu durumda bu kaydın
 * [fingerprintSha256]'sı o TV yeniden başlatılınca yine geçersiz olur
 * (kullanıcı yeniden QR taramalıdır). Bu düşüş gerçek cihazlarda ne sıklıkla
 * olur, henüz ölçülmedi/bilinmiyor.
 *
 * [host]/[port] de yalnızca BİLGİ amaçlıdır (TV'nin yerel ağ adresi
 * değişebilir, ör. DHCP) — bir yeniden bağlanma denemesinde başlangıç
 * değeri olarak kullanılabilir ama [fingerprintSha256] GERÇEK güven
 * kaynağıdır.
 */
data class TrustedTv(
    val displayName: String,
    val host: String,
    val port: Int,
    val fingerprintSha256: String,
    val rememberedAtMillis: Long,
)

/**
 * [TrustedTv]'yi tek satırlık bir metne kodlar/çözer — `app-mobile`'ın
 * `SharedPreferences`'ta bir `Set<String>` olarak saklaması için (bkz.
 * `TrustedTvStore`, Android'e bağımlı olduğu için orada yaşar). Aynı
 * [com.federalturk.gamletsol.core.transport.CueEventCodec] deseni: metin
 * alanları (yalnızca [displayName], kullanıcı tarafından serbest metin
 * olarak girilebilir) Base64 ile kodlanır, `|` ayraç olarak kullanılır.
 */
object TrustedTvCodec {
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    fun encode(tv: TrustedTv): String {
        val name = encoder.encodeToString(tv.displayName.toByteArray(Charsets.UTF_8))
        return "$name|${tv.host}|${tv.port}|${tv.fingerprintSha256}|${tv.rememberedAtMillis}"
    }

    /** Geçersiz/bozuk bir satır için `null` döner — çağıran bunu yoksaymalı. */
    fun decode(line: String): TrustedTv? {
        val parts = line.split("|")
        if (parts.size != 5) return null
        return try {
            val name = String(decoder.decode(parts[0]), Charsets.UTF_8)
            val host = parts[1].takeIf { it.isNotBlank() } ?: return null
            val port = parts[2].toIntOrNull() ?: return null
            val fingerprint = parts[3].takeIf { it.isNotBlank() } ?: return null
            val rememberedAt = parts[4].toLongOrNull() ?: return null
            TrustedTv(
                displayName = name,
                host = host,
                port = port,
                fingerprintSha256 = fingerprint,
                rememberedAtMillis = rememberedAt,
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
