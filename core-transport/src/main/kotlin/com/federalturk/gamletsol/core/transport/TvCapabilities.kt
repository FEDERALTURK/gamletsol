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
) {
    fun encode(): String =
        "$PREFIX$protocolVersion;clear=$supportsClear;heartbeat=$supportsHeartbeat;layout=$supportsLayout"

    companion object {
        /** [TvEventClient]/[TvEventServer] el sıkışma satırının öneki. */
        const val PREFIX = "CAPS:"

        /**
         * Bu sürümün (app-tv + core-transport) ŞU AN GERÇEKTEN desteklediği
         * sabit değer seti:
         * - `clear = false` — protokolde henüz ayrı bir "temizle" mesaj türü
         *   yok ([TvRelaySubtitleSink.clear] no-op).
         * - `heartbeat = false` — henüz gerçek bir round-trip PING/PONG YOK;
         *   bağlı istemciler yalnızca soket seviyesinde "bağlı mı" kontrolü
         *   yapabilir (bkz. [TvReceiverAdapter.heartbeat] dokümantasyonu).
         *   Gerçek canlılık kontrolü yol haritası G028'e bırakıldı.
         * - `layout = false` — Akıllı Ekran (menü/envanter çevirisi, Pro
         *   katman) henüz yapılmadı.
         */
        val CURRENT = TvCapabilities(
            protocolVersion = 1,
            supportsClear = false,
            supportsHeartbeat = false,
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
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
