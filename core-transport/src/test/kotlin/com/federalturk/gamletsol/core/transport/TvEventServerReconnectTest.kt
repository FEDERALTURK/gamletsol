package com.federalturk.gamletsol.core.transport

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * G028 ("reconnect, snapshot ve süre sonu") — bu testler, [TvEventServer]'ın
 * yeni bir bağlantıyı (eşleştirme + yetenek el sıkışması tamamlandıktan
 * sonra) "birincil" yaptığını ve önceki birincil bağlantıyı (varsa, ör.
 * ağı kaybetmiş eski bir oturum) ZORLA kapattığını GERÇEK bir loopback
 * soketi üzerinde kanıtlar.
 *
 * NOT (dürüstlük ilkesi): Bu, gerçek bir ağ kesintisi/Wi-Fi kopması
 * SENARYOSUNU simüle ETMEZ (bu sandbox'ta gerçek bir Wi-Fi yok) — yalnızca
 * "ikinci bir bağlantı geldiğinde sunucunun davranışı" test edilir. Gerçek
 * bir telefon/TV arasında 10 saniyelik ağ kaybı + yeniden bağlanma hâlâ
 * denenmedi (bkz. README).
 */
class TvEventServerReconnectTest {

    private var server: TvEventServer? = null
    private var firstClient: TvEventClient? = null
    private var secondClient: TvEventClient? = null

    @After
    fun tearDown() {
        firstClient?.close()
        secondClient?.close()
        server?.stop()
    }

    @Test
    fun `a new connection becomes primary and the previous one is forcibly closed`() {
        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { received.offer(it) }
        server = s
        s.start()

        val first = TvEventClient.connect("127.0.0.1", s.boundPort)
        firstClient = first
        assertTrue("first connection should succeed", first != null)
        assertTrue(first!!.isConnected)

        // İkinci bağlantı — aynı telefonun yeniden bağlanmasını (veya
        // teorik olarak başka bir istemcinin devralmasını) simüler.
        val second = TvEventClient.connect("127.0.0.1", s.boundPort)
        secondClient = second
        assertTrue("second (reconnecting) connection should also succeed", second != null)

        // NOT: `Socket.isConnected` yalnızca `connect()` BİR KEZ çağrıldı mı
        // sorusuna cevap verir, karşı taraf soketi kapatınca OTOMATİK
        // `false` olmaz (bkz. JDK dokümantasyonu) — gerçekten kapandığını
        // kanıtlamak için (tıpkı `TvEventServerClientTest`'teki "send after
        // server stop" testindeki gibi) aktif bir yazma denemesi gerekir;
        // sunucunun eski soketi kapatması biraz zaman alabilir (ağ/iş
        // parçacığı zamanlaması), bu yüzden kısa bir poll penceresiyle
        // tekrar tekrar gönderim denenir.
        var sawFirstClientFail = false
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (!first.send(CueEvent("x", "y", 0L))) {
                sawFirstClientFail = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue("the stale first connection must be closed once a new primary connects", sawFirstClientFail)

        // Yeni (ikinci) bağlantı çalışır durumda olmalı.
        val event = CueEvent("hello", "merhaba", 0L)
        assertTrue(second!!.send(event))
        assertEquals(event, received.poll(2, TimeUnit.SECONDS))
    }
}
