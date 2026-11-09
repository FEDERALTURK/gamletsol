package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTokenGeneratorTest {

    @Test
    fun `generated codes only use the unambiguous alphabet and the expected shape`() {
        repeat(200) {
            val token = PairingTokenGenerator.generate(nowMillis = 0L)
            // "XXXX-XXXX" şekli: 8 alfanümerik karakter + ortada bir tire.
            assertEquals(9, token.value.length)
            assertEquals('-', token.value[4])
            val chars = token.value.replace("-", "")
            assertTrue(
                "unexpected character in generated code: ${token.value}",
                chars.all { it in "23456789ABCDEFGHJKMNPQRSTUVWXYZ" },
            )
            // Karıştırılabilir karakterler kesinlikle olmamalı.
            assertFalse(chars.any { it in "01OIL" })
        }
    }

    @Test
    fun `two generated codes are (almost certainly) different`() {
        val a = PairingTokenGenerator.generate(nowMillis = 0L)
        val b = PairingTokenGenerator.generate(nowMillis = 0L)
        assertFalse(a.value == b.value)
    }

    @Test
    fun `default ttl matches the planned 60 second QR expiry`() {
        val token = PairingTokenGenerator.generate(nowMillis = 1_000L)
        assertFalse(token.isExpiredAt(1_000L + 59_000L))
        assertTrue(token.isExpiredAt(1_000L + 60_000L))
    }
}
