package com.federalturk.gamletsol.core.pairing

/**
 * TV'nin ekranında gösterdiği QR kodunun TAŞIDIĞI veri (yol haritası G023 —
 * "Tek kullanımlık QR eşleştirme"). Kamera ile tarandığında telefonun elle
 * girmesi gereken ÜÇ alanı (IP, port, eşleştirme kodu) TEK adımda verir —
 * kullanıcı artık IP/port'u da elle yazmak zorunda değildir (bu, G025'in
 * istediği "otomatik keşif"in kendisi DEĞİL — gerçek NSD/mDNS broadcast'i
 * hâlâ yok; bu yalnızca QR üzerinden TEK SEFERLİK bir adres aktarımıdır).
 *
 * Manuel alan (host/port/kod elle yazma) hâlâ var ve KALDIRILMADI — bu,
 * kamera erişimi olmayan/çalışmayan durumlar için gereken yedek yoldur
 * (roadmap'in zaten istediği manuel yol).
 */
data class PairingQrPayload(
    val host: String,
    val port: Int,
    val code: String,
) {
    fun encode(): String = "$PREFIX$VERSION;host=$host;port=$port;code=$code"

    companion object {
        const val PREFIX = "gamletsol-pair:"
        const val VERSION = 1

        /**
         * [encode]'un tersi. Beklenen önekle başlamıyorsa, sürüm bu
         * istemcinin tanıdığından farklıysa, port sayı değilse veya
         * host/kod boşsa `null` döner — çağıran kod (örn.
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
                PairingQrPayload(host = host, port = port, code = code)
            } catch (e: Exception) {
                null
            }
        }
    }
}
