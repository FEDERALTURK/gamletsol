package com.federalturk.gamletsol.core.transport

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 2026-11-14: Gerçek bir TV'de (Profilo 4K, Android 11) TLS el sıkışması
 * `Incompatible padding mode` ile başarısız olduğu ve telefon TV'ye hiç
 * bağlanamadığı için eklendi. Buradaki testler iki şeyi garanti eder:
 * kimlik gerçekten KALICI (fingerprint sabit) ve kimlikle gerçekten EL
 * SIKIŞMA yapılabiliyor.
 */
class TvTlsIdentityPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a generated identity can actually complete a TLS handshake`() {
        assertTrue(TvTlsIdentity.selfTestHandshake(TvTlsIdentity.generate()))
    }

    @Test
    fun `a file-backed identity keeps the same fingerprint across restarts`() {
        val file = File(tempFolder.newFolder("identity"), "tv-identity.p12")

        val first = TvTlsIdentity.loadOrCreateFromFile(file)
        assertTrue("Kimlik dosyası diske yazılmalı", file.isFile)

        val second = TvTlsIdentity.loadOrCreateFromFile(file)

        assertEquals(
            "Aynı dosyadan yüklenen kimliğin fingerprint'i değişmemeli",
            first.fingerprintSha256Hex,
            second.fingerprintSha256Hex,
        )
        assertTrue(TvTlsIdentity.selfTestHandshake(second))
    }

    @Test
    fun `a different file yields a different identity`() {
        val first = TvTlsIdentity.loadOrCreateFromFile(File(tempFolder.newFolder("a"), "tv-identity.p12"))
        val second = TvTlsIdentity.loadOrCreateFromFile(File(tempFolder.newFolder("b"), "tv-identity.p12"))

        assertNotEquals(first.fingerprintSha256Hex, second.fingerprintSha256Hex)
    }

    @Test
    fun `a corrupt identity file is replaced instead of failing`() {
        val file = File(tempFolder.newFolder("corrupt"), "tv-identity.p12")
        file.writeText("bu bir PKCS12 dosyası değil")

        val identity = TvTlsIdentity.loadOrCreateFromFile(file)

        assertEquals(64, identity.fingerprintSha256Hex.length)
        assertTrue(TvTlsIdentity.selfTestHandshake(identity))
    }

    @Test
    fun `the file-backed identity really serves the TV event server`() {
        val file = File(tempFolder.newFolder("server"), "tv-identity.p12")
        val identity = TvTlsIdentity.loadOrCreateFromFile(file)
        val received = java.util.concurrent.LinkedBlockingQueue<CueEvent>()
        val server = TvEventServer(port = 0, tlsIdentity = identity) { received.offer(it) }

        try {
            server.start()
            val client = TvEventClient.connect(
                "127.0.0.1",
                server.boundPort,
                expectedFingerprintSha256 = identity.fingerprintSha256Hex,
            )
            assertTrue("Kalıcı kimlikle gerçek bağlantı kurulabilmeli", client != null)
            assertTrue(client!!.send(CueEvent("Hello", "Merhaba", 1L)))
            assertEquals("Merhaba", received.poll(3, java.util.concurrent.TimeUnit.SECONDS)?.translatedText)
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `self test rejects an identity whose key cannot sign`() {
        // Anahtar yöneticisi hiç anahtar sunmazsa el sıkışma kurulamaz —
        // öz-testin gerçekten bir şeyi doğruladığının kanıtı (yoksa her
        // zaman true dönen bir test işe yaramazdı).
        val broken = TvTlsIdentity.fromKeyManagers(
            certificate = TvTlsIdentity.generate().certificate,
            keyManagers = emptyArray(),
            fingerprintSha256 = "0".repeat(64),
        )

        assertFalse(TvTlsIdentity.selfTestHandshake(broken, timeoutMillis = 2_000))
    }
}
