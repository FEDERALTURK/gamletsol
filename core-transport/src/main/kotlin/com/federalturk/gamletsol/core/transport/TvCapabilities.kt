package com.federalturk.gamletsol.core.transport

/**
 * TV alıcısının desteklediği protokol sürümü ve özellikler (yol haritası
 * G022 — "TV yetenek sözleşmesi"). [TvEventServer], (varsa) eşleştirme
 * adımından SONRA, cue döngüsüne girmeden önce bunu TEK SATIR olarak
 * gönderir ([encode]); [TvEventClient] bunu ayrıştırıp [TvEventClient.capabilities]
 * olarak saklar ([decode]).
 *
 * NOT (dürüstlük ilkesi, MVP sınırı): Sunucu şu an HER ZAMAN aynı sabit
 * [CURRENT] değerini gönderiyor — kod derleme zamanında ne desteklediğini
 * zaten biliyor, bu henüz TV'nin ÇALIŞMA ZAMANINDA donanım/sürüm farkına
 * göre değişen gerçek bir yetenek keşfi değil (şu an tek bir TV alıcısı
 * uygulaması var). Protokole sürüm numarası ve ayrı bayraklar eklendi ki
 * ileride gerçekten farklılaşan alıcılar (Samsung/LG, Akıllı Ekran) geldiğinde
 * bu sözleşme genişletilebilsin — bkz. kök yol haritasındaki
 * `TvReceiverAdapter (probeCapabilities, pair, sendSubtitle, sendLayout,
 * clear, heartbeat, disconnect)` arayüz tanımı.
 */
data class TvCapabilities(
    val protocolVersion: Int,
    val supportsClear: Boolean,
    val supportsHeartbeat: Boolean,
    val supportsLayout: Boolean,
    // Protokol v2 (2026-11-13): TV, eşleştirmeden sonra bir "yeniden bağlanma
    // jetonu" verir; telefon bağlantı koparsa yeni kod okutmadan bu jetonla
    // geri bağlanabilir (bkz. TvEventServer.RESUME_PREFIX).
    val supportsResume: Boolean = false,
) {
    fun encode(): String =
        "$PREFIX$protocolVersion;clear=$supportsClear;heartbeat=$supportsHeartbeat;layout=$supportsLayout;resume=$supportsResume"

    companion object {
        /** [TvEventClient]/[TvEventServer] el sıkışma satırının öneki. */
        const val PREFIX = "CAPS:"

        /**
         * Bu sürümün (app-tv + core-transport) ŞU AN GERÇEKTEN desteklediği
         * sabit değer seti:
         * - `clear = true` (v2) — telefon, altyazı ekrandan kalkınca
         *   `CTL:CLEAR` gönderir; TV altyazıyı kaldırır.
         * - `heartbeat = true` (v2) — telefon boştayken ~2 sn'de bir
         *   `CTL:PING` gönderir. Tek yönlüdür (PONG yok): TV, nabız gelmeyince
         *   telefonun gittiğini anlar; telefon, nabız YAZILAMAYINCA
         *   bağlantının koptuğunu anlar.
         * - `resume` sunucu tarafında, yeniden bağlanma jetonu verilip
         *   verilmediğine göre ayarlanır (bkz. TvEventServer).
         * - `layout = false` — Akıllı Ekran (menü/envanter çevirisi, Pro
         *   katman) henüz yapılmadı.
         */
        val CURRENT = TvCapabilities(
            // v2 (2026-11-13): "temizle" (CTL:CLEAR) ve "nabız" (CTL:PING)
            // mesajları artık destekleniyor — bkz. ControlMessageCodec.
            protocolVersion = 2,
            supportsClear = true,
            supportsHeartbeat = true,
            supportsLayout = false,
        )

        /**
         * [encode]'un tersi. `line` beklenen önekle başlamıyorsa, sürüm
         * sayı değilse veya bayraklar ayrıştırılamıyorsa `null` döner —
         * çağıran kod ([TvEventClient.connect]) bunu bir bağlantı hatası
         * gibi ele alır (eşleştirme reddi ile aynı: `connect()` `null` döner).
         */
        fun decode(line: String?): TvCapabilities? {
            if (line == null || !line.startsWith(PREFIX)) return null
            return try {
                val body = line.removePrefix(PREFIX)
                val parts = body.split(";")
                val version = parts[0].toInt()
                val flags = parts.drop(1).associate { part ->
                    val (key, value) = part.split("=", limit = 2)
                    key to value.toBoolean()
                }
                TvCapabilities(
                    protocolVersion = version,
                    supportsClear = flags["clear"] ?: false,
                    supportsHeartbeat = flags["heartbeat"] ?: false,
                    supportsLayout = flags["layout"] ?: false,
                    supportsResume = flags["resume"] ?: false,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
