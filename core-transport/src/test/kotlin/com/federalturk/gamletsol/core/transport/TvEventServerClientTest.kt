package com.federalturk.gamletsol.core.transport

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * NOT (dürüstlük ilkesi): Bu testler GERÇEK TCP+TLS soketleri (127.0.0.1
 * loopback) üzerinden çalışır — mock/sahte bir bağlantı değil (G024'ten
 * beri `TvEventServer`/`TvEventClient` artık her zaman TLS üzerinden
 * konuşur; bu dosyadaki testler `expectedFingerprintSha256` VERMEDEN
 * bağlanır — bu, manuel giriş yolunun "şifreli ama doğrulanmamış" davranışını
 * sınar; fingerprint PİNLEME testleri `TvEventPairingHandshakeTest`'tedir).
 * Bu, satır bazlı okuma, Base64 kodlama, TLS el sıkışması ve bağlantı
 * kapatma davranışının gerçekten çalıştığını kanıtlar. Doğrulanmayan şey:
 * gerçek bir telefon ile gerçek bir TV arasında, gerçek bir Wi-Fi ağı
 * üzerinden (NAT, gecikme, Wi-Fi kesintisi vb. koşullarda) davranış — bu
 * yalnızca gerçek donanımda denendiğinde doğrulanabilir.
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
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { received.offer(it) }
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
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { received.offer(it) }
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
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { }
        server = s
        s.start()
        val c = TvEventClient.connect("127.0.0.1", s.boundPort)
        client = c
        assertTrue(c != null)

        s.stop()

        // DÜZELTME (gerçek test çalıştırmalarında bulunan flaky test, 2
        // kademede): (1) soketin kapandığını (RST/FIN) işletim sisteminin
        // fark etmesi anlık değildir — art arda 5 hızlı gönderim arasında
        // süre tanımadığı için hep "başarılı" dönüyordu (gerçek CI
        // run'larında 2 kez AssertionError ile doğrulandı) → denemeler
        // arasına bekleme eklendi. (2) `TvEventServer.stop()` artık
        // istemci soketlerini `SO_LINGER(true,0)` ile ZORLA (RST) kapatıyor
        // — bu normalde anında algılanır, ama kaynak yoğunluğu altında
        // (ör. bu kum havuzunda art arda çalıştırmalarda ~1/8 oranında)
        // RST'nin işlenmesi birkaç saniyeyi bulabiliyor; bu yüzden pencere
        // 2'den 10 saniyeye çıkarıldı — bu, testin "asla başarısız olmaz"
        // bir garanti olmadığını, yalnızca gerçekçi bir üst sınır
        // koyduğunu dürüstçe not etmek gerekir.
        var sawFailure = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (!c!!.send(CueEvent("x", "y", 0L))) {
                sawFailure = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue("a send after server stop should eventually fail within 10s", sawFailure)
    }
}
