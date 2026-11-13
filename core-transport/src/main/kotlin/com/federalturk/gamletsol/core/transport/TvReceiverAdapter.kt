package com.federalturk.gamletsol.core.transport

/**
 * TV alıcısıyla konuşmanın soyut sözleşmesi (yol haritası G022 — "TV
 * yetenek sözleşmesi"). Kök yol haritasındaki arayüz listesinde adı geçen
 * `TvReceiverAdapter (probeCapabilities, pair, sendSubtitle, sendLayout,
 * clear, heartbeat, disconnect)` burada şu şekilde karşılanıyor:
 *
 * - `pair`, AYRI bir metod DEĞİL — eşleştirme [TvEventClient.connect]'in
 *   kendisinin bir parçası (TCP el sıkışmasıyla iç içe, bkz. o sınıfın
 *   dokümantasyonu); bu yüzden bu arayüz zaten bağlanmış/eşleşmiş bir
 *   istemciyi sarar, henüz bağlanmamış bir "TV adayını" temsil etmez.
 * - `probeCapabilities`, ayrı bir çağrı değil — [capabilities], bağlantı
 *   kurulurken ([TvEventClient.connect]) zaten alınmış değeri döner (TV her
 *   bağlantıda bunu kendiliğinden gönderiyor, istemcinin ayrıca sorması
 *   gerekmiyor).
 * - `sendLayout`, burada YOK — Akıllı Ekran (menü/envanter/görev çevirisi,
 *   Pro katman) henüz yapılmadı; eklendiğinde bu arayüze bir metod olarak
 *   katılacak ve [TvCapabilities.supportsLayout] `true` olacak.
 */
interface TvReceiverAdapter {
    /** Alttaki soket hâlâ açık mı — bkz. [heartbeat] dokümantasyonundaki sınır. */
    val isConnected: Boolean

    /** Bağlantı kurulurken TV'den alınan, değişmeyen yetenek bildirimi. */
    val capabilities: TvCapabilities

    /** Yeni bir altyazı cue'sunu gönderir; gönderim başarısızsa `false` döner. */
    fun sendSubtitle(event: CueEvent): Boolean

    /**
     * Belirtilen cue'yu TV ekranından temizlemesini ister (protokol v2,
     * `CTL:CLEAR`). TV desteklemiyorsa ([TvCapabilities.supportsClear]
     * `false`) hiçbir şey göndermez ve `false` döner.
     */
    fun clear(cueId: String): Boolean

    /**
     * Protokol v2 nabzı (`CTL:PING`). Tek yönlüdür (yanıt beklenmez): TV
     * nabız gelmeyince telefonun gittiğini anlar; burada yazma başarısız
     * olursa (`false`) bağlantı kopmuş demektir. TV nabzı desteklemiyorsa
     * yalnız soketin açık olup olmadığını döner (o durumda sessiz bir
     * kopmayı hemen fark etmeyebilir).
     */
    fun heartbeat(): Boolean

    /** v2: TV'nin verdiği yeniden bağlanma jetonu (yoksa `null`). */
    val resumeToken: String? get() = null

    /** Bağlantıyı kapatır; tekrar çağrılması güvenlidir. */
    fun disconnect()
}
