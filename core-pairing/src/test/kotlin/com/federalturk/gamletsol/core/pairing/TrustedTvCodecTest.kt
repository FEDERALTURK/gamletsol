package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedTvCodecTest {

    @Test
    fun `encode then decode returns the original trusted tv`() {
        val tv = TrustedTv(
            displayName = "Oturma Odası Profilo",
            host = "192.168.1.42",
            port = 48631,
            fingerprintSha256 = "ab".repeat(32),
            rememberedAtMillis = 1_731_000_000_000L,
        )
        assertEquals(tv, TrustedTvCodec.decode(TrustedTvCodec.encode(tv)))
    }

    @Test
    fun `display name containing the delimiter survives round trip`() {
        val tv = TrustedTv(
            displayName = "TV | Salon",
            host = "10.0.0.5",
            port = 1,
            fingerprintSha256 = "ff".repeat(32),
            rememberedAtMillis = 0L,
        )
        assertEquals(tv, TrustedTvCodec.decode(TrustedTvCodec.encode(tv)))
    }

    @Test
    fun `malformed line decodes to null instead of throwing`() {
        assertNull(TrustedTvCodec.decode("not-a-valid-line"))
        assertNull(TrustedTvCodec.decode("tooFew|parts"))
        assertNull(TrustedTvCodec.decode("###|host|notANumber|fp|0"))
    }

    @Test
    fun `blank host or fingerprint decodes to null`() {
        val tv = TrustedTv("name", "", 1, "fp", 0L)
        assertNull(TrustedTvCodec.decode(TrustedTvCodec.encode(tv)))
    }
}
