package com.federalturk.gamletsol.core.transport

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * NOT (dürüstlük ilkesi): Bu testler GERÇEK TCP soketleri (127.0.0.1
 * loopback) üzerinden çalışır — mock/sahte bir bağlantı değil. Bu, satır
 * bazlı okuma, Base64 kodlama ve bağlantı kapatma davranışının gerçekten
 * çalıştığını kanıtlar. Doğrulanmayan şey: gerçek bir telefon ile gerçek
 * bir TV arasında, gerçek bir Wi-Fi ağı üzerinden (NAT, gecikme, Wi-Fi
 * kesintisi vb. koşullarda) davranış — bu yalnızca gerçek donanımda
 * denendiğinde doğrulanabilir.
 */
class TvEventServerClientTest {

    private var server: TvEventServer? = null
    private var client: TvEventClient? = null

    @After
    fun tearDown() {
        client?.close()
        server?.stop()
    }

    @Test
    fun `client can send a cue and server receives it over a real loopback socket`() {
        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(port = 0) { received.offer(it) }
        server = s
        s.start()

        val c = TvEventClient.connect("127.0.0.1", s.boundPort)
        client = c
        assertTrue("client should connect to the loopback server", c != null)

        val event = CueEvent(
            originalText = "Press X to continue",
            translatedText = "Devam etmek için X'e basın",
            timestampMs = 123_456L,
        )
        assertTrue(c!!.send(event))

        val receivedEvent = received.poll(2, TimeUnit.SECONDS)
        assertEquals(event, receivedEvent)
    }

    @Test
    fun `multiple cues sent in sequence all arrive in order`() {
        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(port = 0) { received.offer(it) }
        server = s
        s.start()

        val c = TvEventClient.connect("127.0.0.1", s.boundPort)
        client = c

        val events = (1..5).map {
            CueEvent(originalText = "line $it", translatedText = "satır $it", timestampMs = it.toLong())
        }
        events.forEach { assertTrue(c!!.send(it)) }

        val receivedEvents = (1..5).map { received.poll(2, TimeUnit.SECONDS) }
        assertEquals(events, receivedEvents)
    }

    @Test
    fun `connecting to a port with no server listening fails gracefully`() {
        // 1 numaralı port ayrıcalıklı/kullanılmıyor olmalı — bağlantı reddedilmeli.
        val c = TvEventClient.connect("127.0.0.1", 1, timeoutMs = 500)
        assertFalse("connect() should return null, not throw, when nothing is listening", c != null)
    }

    @Test
    fun `send after server stop eventually returns false instead of throwing`() {
        val s = TvEventServer(port = 0) { }
        server = s
        s.start()
        val c = TvEventClient.connect("127.0.0.1", s.boundPort)
        client = c
        assertTrue(c != null)

        s.stop()

        // DÜZELTME (gerçek CI çalıştırmasında bulunan flaky test): soketin
        // kapandığını (RST/FIN) işletim sisteminin fark etmesi anlık
        // değildir — bu kum havuzunda rastlantıyla hemen oluyordu ama
        // CI'nın daha hızlı/yoğun makinesinde art arda 5 hızlı gönderim
        // arasında süre tanımadığı için hep "başarılı" dönüyordu (gerçek
        // CI run'larında 2 kez AssertionError ile doğrulandı). Artık
        // denemeler arasına gerçek bir bekleme süresi konuyor ve en fazla
        // 2 saniye boyunca deneniyor.
        var sawFailure = false
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (!c!!.send(CueEvent("x", "y", 0L))) {
                sawFailure = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue("a send after server stop should eventually fail within 2s", sawFailure)
    }
}
