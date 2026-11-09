package com.federalturk.gamletsol.core.transport

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * [SocketTvReceiverAdapter]'ın, gerçek bir loopback soketi üzerinde
 * [TvReceiverAdapter] sözleşmesini doğru şekilde uyguladığını kanıtlayan
 * bütünleşik testler (G022).
 */
class SocketTvReceiverAdapterTest {

    private var server: TvEventServer? = null
    private var adapter: TvReceiverAdapter? = null

    @After
    fun tearDown() {
        adapter?.disconnect()
        server?.stop()
    }

    @Test
    fun `adapter reports the TV's capabilities and forwards sent cues`() {
        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { received.offer(it) }
        server = s
        s.start()

        val client = TvEventClient.connect("127.0.0.1", s.boundPort)
        assertTrue(client != null)
        val a = SocketTvReceiverAdapter(client!!)
        adapter = a

        assertTrue(a.isConnected)
        assertEquals(TvCapabilities.CURRENT, a.capabilities)

        val event = CueEvent("hello", "merhaba", 0L)
        assertTrue(a.sendSubtitle(event))
        assertEquals(event, received.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `clear is honestly unsupported and never sends anything`() {
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { }
        server = s
        s.start()

        val client = TvEventClient.connect("127.0.0.1", s.boundPort)
        val a = SocketTvReceiverAdapter(client!!)
        adapter = a

        assertFalse("clear() must report unsupported, matching TvCapabilities.supportsClear=false", a.clear("any-id"))
    }

    @Test
    fun `heartbeat and disconnect reflect socket state`() {
        val s = TvEventServer(port = 0, tlsIdentity = TvTlsIdentity.generate()) { }
        server = s
        s.start()

        val client = TvEventClient.connect("127.0.0.1", s.boundPort)
        val a = SocketTvReceiverAdapter(client!!)
        adapter = a

        assertTrue(a.heartbeat())
        a.disconnect()
        assertFalse(a.heartbeat())
        assertFalse(a.isConnected)
    }
}
