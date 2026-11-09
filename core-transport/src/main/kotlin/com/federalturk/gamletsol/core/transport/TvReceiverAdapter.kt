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
     * Belirtilen cue'yu TV ekranından temizlemesini ister.
     *
     * NOT (dürüstlük ilkesi): Protokol henüz bir "temizle" mesaj türü
     * TANIMLAMIYOR ([TvCapabilities.supportsClear] `false`) — bu metod
     * her zaman `false` döner, hiçbir şey TV'ye gönderilmez. TV ekranında
     * son cue, yenisi gelene kadar görünmeye devam eder (bilinen MVP sınırı).
     */
    fun clear(cueId: String): Boolean

    /**
     * NOT (dürüstlük ilkesi): Gerçek bir round-trip PING/PONG DEĞİL —
     * yalnızca [isConnected] soket durumunu döner. Karşı taraf (TV) ağ
     * bağlantısını sessizce kaybetmişse (ör. Wi-Fi kopması, uygulama
     * çökmesi, ağ kablosu/yönlendirici arızası) bu hâlâ `true` dönebilir —
     * işletim sistemi RST/FIN'i henüz işlemciye bildirmemiş olabilir.
     * Gerçek bir canlılık kontrolü (karşı taraftan aktif bir yanıt istemek)
     * yol haritası G028'e (yeniden bağlanma/oturum yönetimi) bırakıldı.
     */
    fun heartbeat(): Boolean

    /** Bağlantıyı kapatır; tekrar çağrılması güvenlidir. */
    fun disconnect()
}
