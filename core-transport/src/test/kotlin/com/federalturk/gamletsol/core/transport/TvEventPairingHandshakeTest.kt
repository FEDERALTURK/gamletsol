package com.federalturk.gamletsol.core.transport

import com.federalturk.gamletsol.core.pairing.PairingRegistry
import com.federalturk.gamletsol.core.pairing.PairingResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * G023 (core-pairing) ile core-transport'un GERÇEK bir loopback soketi
 * üzerinde birlikte çalıştığını kanıtlayan bütünleşik testler — her ikisi de
 * kendi modülünde ayrıca birim test edildi, bu dosya yalnızca ikisinin
 * protokol seviyesinde doğru bağlandığını doğrular.
 */
class TvEventPairingHandshakeTest {

    private var server: TvEventServer? = null
    private var client: TvEventClient? = null

    @After
    fun tearDown() {
        client?.close()
        server?.stop()
    }

    @Test
    fun `correct pairing code lets the connection through and cues are received`() {
        val registry = PairingRegistry()
        val token = registry.issue()

        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(
            port = 0,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { received.offer(it) }
        server = s
        s.start()

        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = token.value)
        client = c
        assertTrue("handshake with the correct code should succeed", c != null)

        val event = CueEvent("hello", "merhaba", 0L)
        assertTrue(c!!.send(event))
        assertEquals(event, received.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `wrong pairing code is rejected and no cue is ever processed`() {
        val registry = PairingRegistry()
        registry.issue() // bir kod aktif ama istemci bunu kullanmayacak

        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(
            port = 0,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { received.offer(it) }
        server = s
        s.start()

        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = "ZZZZ-ZZZZ")
        client = c
        assertNull("handshake with the wrong code must fail connect()", c)

        assertNull("no cue should ever have been processed", received.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `reusing an already-consumed pairing code is rejected (replay)`() {
        val registry = PairingRegistry()
        val token = registry.issue()

        val s = TvEventServer(
            port = 0,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        val firstClient = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = token.value)
        assertTrue(firstClient != null)
        firstClient?.close()

        // Aynı kodla İKİNCİ bir bağlantı denemesi — tek kullanımlık olduğu için reddedilmeli.
        val secondClient = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = token.value)
        client = secondClient
        assertNull("the same pairing code must not work twice", secondClient)
    }

    @Test
    fun `missing pairing code when one is required is rejected`() {
        val registry = PairingRegistry()
        registry.issue()

        val s = TvEventServer(
            port = 0,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        // Hiç handshake göndermeyen eski/uyumsuz bir istemciyi simüle et.
        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = null)
        client = c
        assertTrue("connect() itself succeeds without a code", c != null)
        // Ama sunucu PAIR: satırı beklerken herhangi bir cue satırı gelirse
        // onu asla doğru şekilde bir handshake olarak kabul etmeyecek, bu
        // yüzden bağlantı kullanılamaz hale gelir (ilk satır handshake
        // olarak YORUMLANIR ve reddedilir).
        val sendResult = (1..10).map { c!!.send(CueEvent("x", "y", 0L)) }
        // En azından ilk gönderimlerden biri, sunucu bağlantıyı kapattıktan
        // sonra başarısız olmalı (TCP zamanlamasına bağlı, bu yüzden birden
        // fazla deneme + kısa gecikme ile sağlamlaştırıldı).
        var sawFailure = sendResult.any { !it }
        if (!sawFailure) {
            val deadline = System.currentTimeMillis() + 2_000
            while (!sawFailure && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
                sawFailure = !c!!.send(CueEvent("x", "y", 0L))
            }
        }
        assertTrue("sending without a pairing code should eventually fail", sawFailure)
    }
}
