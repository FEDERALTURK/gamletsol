package com.federalturk.gamletsol.core.transport

/**
 * G026 ("olay doğrulama ve sıra güvenliği") — tek bir bağlantı üzerinde
 * gelen [CueEvent]'lerin, aynı [CueEvent.epochId] (oturum/bağlantı kimliği)
 * içinde [CueEvent.sequence] değerinin KESİNLİKLE artan olmasını denetler.
 *
 * NOT (tasarım gerekçesi): Aynı TCP bağlantısı içinde paketler zaten SIRALI
 * teslim edilir (TCP'nin kendi garantisi) — bu sınıfın asıl kattığı şey,
 * [TvEventServer.handleClient]'ın TEK bir döngüde işlediği olayları, (a)
 * bozuk/elle üretilmiş bir istemcinin kasıtlı olarak eski bir `sequence`
 * göndermesine (tekrar oynatma/replay), ve (b) farklı bir [epochId] ile
 * gelen olayların ÖNCEKİ oturumdan "sızmış" sayılmamasına (bkz. G028 —
 * yeniden bağlanma, her yeni bağlantı yeni bir epoch açar) karşı tek bir
 * yerde, test edilebilir pure-Kotlin mantıkla korumaktır.
 *
 * Her [TvEventServer] bağlantısı (her `handleClient` çağrısı) kendi,
 * BAĞIMSIZ bir [CueSequenceGuard] örneği kullanmalıdır — bu sınıf
 * birden fazla eşzamanlı bağlantı arasında paylaşılacak şekilde
 * TASARLANMAMIŞTIR (thread-safe değildir, tek bir bağlantının tek bir
 * okuma iş parçacığı tarafından sırayla çağrılması beklenir — bkz.
 * [TvEventServer]'ın her istemciyi kendi iş parçacığında işlemesi).
 */
class CueSequenceGuard {
    private var currentEpochId: String? = null
    private var lastAcceptedSequence: Long = -1L

    /**
     * [decoded] işlenmeli mi (`true`) yoksa reddedilmeli mi (`false`,
     * sessizce yoksayılır — çağıran kod [TvEventServer.handleClient]'taki
     * `continue` ile aynı davranışı sürdürür).
     *
     * Kabul kuralları:
     * - İlk görülen `epochId` (veya önceki epoch'tan FARKLI bir epoch — yeni
     *   bir oturumun başladığı anlamına gelir) HER ZAMAN kabul edilir ve
     *   sayaç o olayın `sequence`'ine sıfırlanır (yeni oturumun kendi
     *   numaralandırması, 0'dan başlasa da başlamasa da kabul edilir —
     *   sunucu istemcinin iç sayaç başlangıcını zorunlu kılmaz).
     * - AYNI epoch içinde, `sequence` o ana kadar kabul edilmiş en yüksek
     *   değerden KESİNLİKLE büyük olmalıdır; değilse (eşit = tekrar/replay,
     *   küçük = sırası bozulmuş/reorder veya tekrar) reddedilir.
     */
    fun accept(decoded: DecodedCueEvent): Boolean = accept(decoded.sequence, decoded.epochId)

    /** v2: kontrol mesajları (CTL:CLEAR / CTL:PING) da aynı sıra/epoch kuralına tabidir. */
    fun accept(decoded: DecodedControlMessage): Boolean = accept(decoded.sequence, decoded.epochId)

    @Synchronized
    fun accept(sequence: Long, epochId: String): Boolean {
        if (epochId != currentEpochId) {
            currentEpochId = epochId
            lastAcceptedSequence = sequence
            return true
        }
        if (sequence <= lastAcceptedSequence) return false
        lastAcceptedSequence = sequence
        return true
    }
}
