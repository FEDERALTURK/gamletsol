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
            tlsIdentity = TvTlsIdentity.generate(),
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
            tlsIdentity = TvTlsIdentity.generate(),
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
            tlsIdentity = TvTlsIdentity.generate(),
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
            tlsIdentity = TvTlsIdentity.generate(),
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        // Hiç handshake göndermeyen eski/uyumsuz bir istemciyi simüle et.
        //
        // DÜZELTME (G022 eklenmesiyle davranış değişti, kodun/testin eskisi
        // yerine): Önceden connect() burada `null` DEĞİL döndürüyordu —
        // bağlantı "başarılı" sayılıyor ama kullanılamaz hale geliyordu
        // (ilk cue satırı sunucu tarafında handshake olarak yorumlanıp
        // reddediliyordu, bu da ancak sonraki bir `send()` başarısızlığıyla
        // fark edilebiliyordu — flaky bir "eventually fails" testiydi).
        // Artık TV, eşleştirmeden SONRA her zaman bir yetenek satırı
        // ([TvCapabilities]) gönderiyor; kodsuz istemci hiç PAIR: yazmadığı
        // için sunucu o satırı asla okuyamaz (sunucu okumayı, istemci de
        // okumayı bekler — karşılıklı kilitlenme), istemcinin okuması
        // zaman aşımına uğrar ve connect() temiz bir şekilde `null` döner.
        // Bu, önceki flaky kontrolden daha doğru ve daha hızlı bir sonuçtur.
        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = null, timeoutMs = 1_000)
        client = c
        assertNull("connect() without a code must fail outright when the TV requires pairing", c)
    }

    @Test
    fun `successful handshake also yields the TV's capability declaration`() {
        val registry = PairingRegistry()
        val token = registry.issue()

        val s = TvEventServer(
            port = 0,
            tlsIdentity = TvTlsIdentity.generate(),
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = token.value)
        client = c
        assertTrue("handshake should succeed", c != null)
        assertEquals(
            "client should learn the TV's current capability declaration after pairing",
            TvCapabilities.CURRENT,
            c!!.capabilities,
        )
    }

    // --- G024: TLS + fingerprint pinleme ---

    @Test
    fun `connecting with the TV's real fingerprint succeeds (true pinning)`() {
        val registry = PairingRegistry()
        val token = registry.issue()
        val tlsIdentity = TvTlsIdentity.generate()

        val s = TvEventServer(
            port = 0,
            tlsIdentity = tlsIdentity,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        val c = TvEventClient.connect(
            "127.0.0.1",
            s.boundPort,
            pairingCode = token.value,
            expectedFingerprintSha256 = tlsIdentity.fingerprintSha256Hex,
        )
        client = c
        assertTrue("connecting with the TV's REAL fingerprint must succeed", c != null)
    }

    @Test
    fun `connecting with the wrong fingerprint is rejected even with a correct pairing code (MITM protection)`() {
        val registry = PairingRegistry()
        val token = registry.issue()
        val realTlsIdentity = TvTlsIdentity.generate()
        val someOtherTlsIdentity = TvTlsIdentity.generate() // "saldırganın" sertifikasını simüle eder

        val s = TvEventServer(
            port = 0,
            tlsIdentity = realTlsIdentity,
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { }
        server = s
        s.start()

        // Doğru eşleştirme kodu verilse DAHİ, fingerprint yanlışsa (ör. QR
        // başka bir TV'den/bir MITM'den geldiyse) bağlantı reddedilmelidir —
        // bu, G024'ün asıl amacıdır: eşleştirme kodu KİMLİK doğrulamaz,
        // TLS fingerprint pinning doğrular.
        val c = TvEventClient.connect(
            "127.0.0.1",
            s.boundPort,
            pairingCode = token.value,
            expectedFingerprintSha256 = someOtherTlsIdentity.fingerprintSha256Hex,
        )
        client = c
        assertNull("a fingerprint mismatch must reject the connection even with the right pairing code", c)
    }

    @Test
    fun `connecting without a fingerprint (manual entry path) still succeeds over an encrypted channel`() {
        val registry = PairingRegistry()
        val token = registry.issue()

        val received = LinkedBlockingQueue<CueEvent>()
        val s = TvEventServer(
            port = 0,
            tlsIdentity = TvTlsIdentity.generate(),
            validatePairingCode = { code -> registry.validateAndConsume(code) == PairingResult.VALID },
        ) { received.offer(it) }
        server = s
        s.start()

        // expectedFingerprintSha256 verilmiyor — manuel giriş yolunu simüle
        // eder (kullanıcı 64 karakterlik bir hash'i elle yazamaz). Bağlantı
        // hâlâ TLS üzerindendir (şifreli) ama TV'nin kimliği PİNLENMEZ.
        val c = TvEventClient.connect("127.0.0.1", s.boundPort, pairingCode = token.value)
        client = c
        assertTrue("manual entry (no fingerprint) must still work over the now-mandatory TLS channel", c != null)

        val event = CueEvent("hello", "merhaba", 0L)
        assertTrue(c!!.send(event))
        assertEquals(event, received.poll(2, TimeUnit.SECONDS))
    }
}
