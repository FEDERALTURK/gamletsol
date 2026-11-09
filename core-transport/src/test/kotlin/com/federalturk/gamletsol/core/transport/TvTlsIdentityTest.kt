package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [TvTlsIdentity.fromKeyManagers] — G029 kimlik kalıcılığı (2026-11-09) —
 * `app-tv`'deki `PersistentTvIdentity`'nin `AndroidKeyStore`'dan okuduğu
 * sertifika/KeyManager'ları buraya SARACAĞI fabrikanın doğru çalıştığını
 * kanıtlar. `PersistentTvIdentity`'nin KENDİSİ Android'e bağımlı olduğu
 * için burada (core-transport, saf Kotlin/JVM) test edilemez — bu test,
 * [generate]'in ürettiği GERÇEK bir sertifika/KeyManager çiftini ödünç
 * alıp [fromKeyManagers]'a besler (tamamen bu testin kendi kolaylığı,
 * [PersistentTvIdentity]'nin gerçek davranışını simüle etmez).
 */
class TvTlsIdentityTest {

    @Test
    fun `fromKeyManagers wraps the given certificate, key managers and fingerprint without regenerating anything`() {
        val source = TvTlsIdentity.generate()

        val wrapped = TvTlsIdentity.fromKeyManagers(
            certificate = source.certificate,
            keyManagers = source.keyManagers,
            fingerprintSha256 = source.fingerprintSha256Hex,
        )

        assertSame(source.certificate, wrapped.certificate)
        assertSame(source.keyManagers, wrapped.keyManagers)
        assertEquals(source.fingerprintSha256Hex, wrapped.fingerprintSha256Hex)
    }

    @Test
    fun `fromKeyManagers does not need to match generate's own sha256Hex computation to be used correctly`() {
        val source = TvTlsIdentity.generate()
        val recomputedFingerprint = TvTlsIdentity.sha256Hex(source.certificate.encoded)

        // Gerçek bir AndroidKeyStore sertifikasının fingerprint'i de aynı
        // şekilde certificate.encoded üzerinden hesaplanacak (bkz.
        // PersistentTvIdentity.loadOrCreate) — bu test, generate()'in
        // ürettiği sertifikanın kendi fingerprint'iyle tutarlı olduğunu
        // (yani sha256Hex'in deterministik olduğunu) doğrular.
        assertEquals(source.fingerprintSha256Hex, recomputedFingerprint)

        val wrapped = TvTlsIdentity.fromKeyManagers(
            certificate = source.certificate,
            keyManagers = source.keyManagers,
            fingerprintSha256 = recomputedFingerprint,
        )
        assertArrayEquals(source.certificate.encoded, wrapped.certificate.encoded)
    }
}
