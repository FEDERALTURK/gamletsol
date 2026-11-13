package com.federalturk.gamletsol.core.transport

import com.federalturk.gamletsol.core.pairing.PairingRegistry
import com.federalturk.gamletsol.core.pairing.PairingResult
import com.federalturk.gamletsol.core.pairing.ResumeTokenRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protokol v2 (2026-11-13) — telefon↔TV senkronizasyonu için gerçek
 * loopback TLS soketi üzerinde uçtan uca testler: temizle, nabız/zaman
 * aşımı, birincil istemci bağlandı/koptu bildirimi, yeniden bağlanma jetonu.
 */
class TvSyncProtocolTest {

    private val servers = mutableListOf<TvEventServer>()
    private val clients = mutableListOf<TvEventClient>()

    @After
    fun tearDown() {
        clients.forEach { it.close() }
        servers.forEach { it.stop() }
    }

    private class Events {
        val cues = LinkedBlockingQueue<CueEvent>()
        val clears = LinkedBlockingQueue<Boolean>()
        val primary = LinkedBlockingQueue<Boolean>()
    }

    private fun startServer(
        events: Events,
        pairing: PairingRegistry? = null,
        resume: ResumeTokenRegistry? = null,
        heartbeatTimeoutMillis: Int = TvEventServer.DEFAULT_HEARTBEAT_TIMEOUT_MILLIS,
    ): TvEventServer {
        val server = TvEventServer(
            port = 0,
            tlsIdentity = TvTlsIdentity.generate(),
            validatePairingCode = pairing?.let { registry ->
                { code: String ->
                    registry.validateAndConsume(code) == PairingResult.VALID
                }
            },
            validateResumeToken = resume?.let { registry -> { token: String -> registry.validateAndConsume(token) } },
            issueResumeToken = resume?.let { registry -> { registry.issue() } },
            onClear = { events.clears.offer(true) },
            onPrimaryClientChanged = { events.primary.offer(it) },
            heartbeatTimeoutMillis = heartbeatTimeoutMillis,
        ) { events.cues.offer(it) }
        server.start()
        servers += server
        return server
    }

    private fun TvEventClient?.track(): TvEventClient? = this?.also { clients += it }

    @Test
    fun `clear is delivered in order after the cue it follows`() {
        val events = Events()
        val server = startServer(events)
        val client = TvEventClient.connect("127.0.0.1", server.boundPort).track()!!

        assertTrue(client.send(CueEvent("Hello", "Merhaba", 1L)))
        assertTrue(client.sendClear())

        assertEquals("Merhaba", events.cues.poll(2, TimeUnit.SECONDS)?.translatedText)
        assertEquals(true, events.clears.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `the TV is told when the phone connects and when it goes away`() {
        val events = Events()
        val server = startServer(events)
        val client = TvEventClient.connect("127.0.0.1", server.boundPort).track()!!

        assertEquals(true, events.primary.poll(2, TimeUnit.SECONDS))
        client.close()
        assertEquals(false, events.primary.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `a silent phone is dropped after the heartbeat timeout once it started sending pings`() {
        val events = Events()
        val server = startServer(events, heartbeatTimeoutMillis = 500)
        val client = TvEventClient.connect("127.0.0.1", server.boundPort).track()!!
        assertEquals(true, events.primary.poll(2, TimeUnit.SECONDS))

        assertTrue(client.sendPing())
        // Ardından hiçbir şey gönderilmiyor (ör. telefonun Wi-Fi'ı sessizce gitti).
        assertEquals(false, events.primary.poll(3, TimeUnit.SECONDS))
    }

    @Test
    fun `a phone that never pings is not dropped by the heartbeat timeout`() {
        val events = Events()
        val server = startServer(events, heartbeatTimeoutMillis = 300)
        TvEventClient.connect("127.0.0.1", server.boundPort).track()!!
        assertEquals(true, events.primary.poll(2, TimeUnit.SECONDS))

        assertNull("Nabız göndermeyen eski istemci düşürülmemeli", events.primary.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun `after pairing the phone can reconnect with the resume token instead of a new code`() {
        val events = Events()
        val pairing = PairingRegistry()
        val resume = ResumeTokenRegistry()
        val server = startServer(events, pairing = pairing, resume = resume)
        val code = pairing.issue().value

        val first = TvEventClient.connect("127.0.0.1", server.boundPort, pairingCode = code).track()!!
        assertTrue(first.capabilities.supportsResume)
        val token = assertNotNull(first.resumeToken).let { first.resumeToken!! }
        first.close()

        val second = TvEventClient.connect("127.0.0.1", server.boundPort, resumeToken = token).track()
        assertNotNull("Jetonla yeniden bağlanma kabul edilmeli", second)
        assertTrue(second!!.send(CueEvent("Back", "Geri döndüm", 2L)))
        assertEquals("Geri döndüm", events.cues.poll(2, TimeUnit.SECONDS)?.translatedText)

        val rotated = second.resumeToken
        assertNotNull(rotated)
        assertNotEquals("Jeton her bağlantıda yenilenmeli", token, rotated)
    }

    @Test
    fun `a used or bogus resume token is rejected`() {
        val events = Events()
        val pairing = PairingRegistry()
        val resume = ResumeTokenRegistry()
        val server = startServer(events, pairing = pairing, resume = resume)

        val first = TvEventClient.connect("127.0.0.1", server.boundPort, pairingCode = pairing.issue().value).track()!!
        val token = first.resumeToken!!
        first.close()

        assertNotNull(TvEventClient.connect("127.0.0.1", server.boundPort, resumeToken = token).track())
        assertNull("Aynı jeton ikinci kez kullanılamamalı", TvEventClient.connect("127.0.0.1", server.boundPort, resumeToken = token).track())
        assertNull(TvEventClient.connect("127.0.0.1", server.boundPort, resumeToken = "00112233445566778899aabbccddeeff").track())
    }

    @Test
    fun `control messages round trip and reject garbage`() {
        val line = ControlMessageCodec.encode(ControlType.PING, 7, "abc")
        assertEquals(DecodedControlMessage(ControlType.PING, 7, "abc"), ControlMessageCodec.decode(line))
        assertNull(ControlMessageCodec.decode("CTL:BOOM|1|abc"))
        assertNull(ControlMessageCodec.decode("CTL:CLEAR|-1|abc"))
        assertNull("v1 altyazı çözücüsü kontrol satırını altyazı sanmamalı", CueEventCodec.decode(line))
        assertFalse(ControlMessageCodec.isControl("aGVsbG8=|bWVyaGFiYQ==|0|0|abc"))
    }
}
