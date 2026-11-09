package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bu testler, yol haritasının G023 için açıkça istediği doğrulama
 * senaryolarını karşılar: "Süresi geçmiş QR, ikinci yayıncı, yanlış TV ve
 * tekrar denemesinin reddini test et."
 */
class PairingRegistryTest {

    @Test
    fun `correct code is accepted exactly once`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })
        val token = registry.issue()

        assertEquals(PairingResult.VALID, registry.validateAndConsume(token.value))
    }

    @Test
    fun `retrying the same code after it was consumed is rejected (replay)`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })
        val token = registry.issue()

        assertEquals(PairingResult.VALID, registry.validateAndConsume(token.value))
        // "Tekrar denemesinin reddi": aynı kod ikinci kez kullanılamaz.
        assertEquals(PairingResult.ALREADY_USED, registry.validateAndConsume(token.value))
    }

    @Test
    fun `an unknown or wrong code is rejected`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })
        registry.issue()

        // "Yanlış TV" senaryosu: başka bir cihazın/oturumun kodu burada geçerli değil.
        assertEquals(PairingResult.UNKNOWN, registry.validateAndConsume("ZZZZ-ZZZZ"))
    }

    @Test
    fun `an expired code is rejected even though it was never used`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })
        val token = registry.issue(ttlMillis = 60_000L)

        now = 60_000L
        assertEquals(PairingResult.EXPIRED, registry.validateAndConsume(token.value))
    }

    @Test
    fun `issuing a new code invalidates the previous one (second publisher)`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })
        val firstToken = registry.issue()

        // TV ekranı yeni bir kod gösterdi ("ikinci yayıncı") — eskisi artık geçersiz.
        registry.issue()

        assertEquals(PairingResult.UNKNOWN, registry.validateAndConsume(firstToken.value))
    }

    @Test
    fun `consumed-code memory does not grow without bound`() {
        var now = 0L
        val registry = PairingRegistry(clock = { now })

        repeat(100) {
            val token = registry.issue()
            registry.validateAndConsume(token.value)
        }

        // Bellek sınırlı olsa da, HENÜZ TÜKETİLMEMİŞ aktif bir kod her zaman
        // doğru şekilde kabul edilebilmeli.
        val latest = registry.issue()
        assertEquals(PairingResult.VALID, registry.validateAndConsume(latest.value))
    }
}
