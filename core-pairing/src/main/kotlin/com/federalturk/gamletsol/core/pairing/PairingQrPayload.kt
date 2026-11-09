package com.federalturk.gamletsol.core.pairing

/**
 * TV'nin ekranında gösterdiği QR kodunun TAŞIDIĞI veri (yol haritası
 * G023/G024 — "Tek kullanımlık QR eşleştirme" + "TLS/fingerprint"). Kamera
 * ile tarandığında telefonun elle girmesi gereken DÖRT alanı (IP, port,
 * eşleştirme kodu, TLS sertifika fingerprint'i) TEK adımda verir —
 * kullanıcı artık bunların hiçbirini elle yazmak zorunda değildir (bu,
 * G025'in istediği "otomatik keşif"in kendisi DEĞİL — gerçek NSD/mDNS
 * broadcast'i hâlâ yok; bu yalnızca QR üzerinden TEK SEFERLİK bir adres/
 * kimlik aktarımıdır).
 *
 * [fingerprintSha256] (2026-11-09, G024): TV'nin kendi-imzalı TLS
 * sertifikasının SHA-256 özeti ([com.federalturk.gamletsol.core.transport.TvTlsIdentity.fingerprintSha256Hex]).
 * `VERSION` bu alanın eklenmesiyle 1'den 2'ye çıkarıldı — eski (v1) bir QR,
 * fingerprint TAŞIMADIĞI için artık [decode] tarafından reddedilir (`null`);
 * bu BİLEREK yapıldı: v1/v2 karışık bir TV+telefon çiftinin "yarı güvenli"
 * (kimliği doğrulanmayan) bir bağlantı kurmasına izin vermek yerine, net bir
 * uyumsuzluk hatası vermek tercih edildi.
 *
 * Manuel alan (host/port/kod elle yazma) hâlâ var ve KALDIRILMADI — bu,
 * kamera erişimi olmayan/çalışmayan durumlar için gereken yedek yoldur
 * (roadmap'in zaten istediği manuel yol). Manuel giriş fingerprint
 * İÇERMEZ (64 karakterlik bir hex'i elle yazmak pratik değil) — bu yolla
 * bağlanan istemciler TLS ile ŞİFRELENİR ama TV'nin kimliği PİNLENMEZ
 * (bkz. `TvEventClient.connect` dokümantasyonu).
 */
data class PairingQrPayload(
    val host: String,
    val port: Int,
    val code: String,
    val fingerprintSha256: String,
) {
    fun encode(): String = "$PREFIX$VERSION;host=$host;port=$port;code=$code;fingerprint=$fingerprintSha256"

    companion object {
        const val PREFIX = "gamletsol-pair:"
        const val VERSION = 2

        /**
         * [encode]'un tersi. Beklenen önekle başlamıyorsa, sürüm bu
         * istemcinin tanıdığından farklıysa, port sayı değilse veya
         * host/kod/fingerprint boşsa `null` döner — çağıran kod (örn.
         * `QrScannerScreen`) bunu "tanınmayan/bozuk QR" olarak ele almalı,
         * kullanıcıya manuel girişi önermelidir.
         */
        fun decode(text: String?): PairingQrPayload? {
            if (text == null || !text.startsWith(PREFIX)) return null
            return try {
                val body = text.removePrefix(PREFIX)
                val parts = body.split(";")
                val version = parts[0].toInt()
                if (version != VERSION) return null
                val fields = parts.drop(1).associate {
                    val (key, value) = it.split("=", limit = 2)
                    key to value
                }
                val host = fields["host"]?.takeIf { it.isNotBlank() } ?: return null
                val port = fields["port"]?.toIntOrNull() ?: return null
                val code = fields["code"]?.takeIf { it.isNotBlank() } ?: return null
                val fingerprint = fields["fingerprint"]?.takeIf { it.isNotBlank() } ?: return null
                PairingQrPayload(host = host, port = port, code = code, fingerprintSha256 = fingerprint)
            } catch (e: Exception) {
                null
            }
        }
    }
}
