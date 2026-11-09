package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTvRegistryTest {

    private fun tv(name: String, fingerprint: String) =
        TrustedTv(displayName = name, host = "192.168.1.1", port = 48631, fingerprintSha256 = fingerprint, rememberedAtMillis = 0L)

    @Test
    fun `remember adds a new entry to an empty list`() {
        val result = TrustedTvRegistry.remember(emptyList(), tv("Salon TV", "fp1"))
        assertEquals(listOf(tv("Salon TV", "fp1")), result)
    }

    @Test
    fun `remembering the same fingerprint again replaces the old entry, not duplicates it`() {
        val first = TrustedTvRegistry.remember(emptyList(), tv("Eski isim", "fp1"))
        val second = TrustedTvRegistry.remember(first, tv("Yeni isim", "fp1"))

        assertEquals(1, second.size)
        assertEquals("Yeni isim", second.first().displayName)
    }

    @Test
    fun `remembering a different fingerprint keeps both entries`() {
        val list = TrustedTvRegistry.remember(
            TrustedTvRegistry.remember(emptyList(), tv("Salon", "fp1")),
            tv("Yatak Odası", "fp2"),
        )
        assertEquals(2, list.size)
    }

    @Test
    fun `fingerprint matching is case-insensitive`() {
        val list = TrustedTvRegistry.remember(emptyList(), tv("Salon", "AbCd"))
        assertTrue(TrustedTvRegistry.isTrusted(list, "abcd"))
        assertTrue(TrustedTvRegistry.isTrusted(list, "ABCD"))
    }

    @Test
    fun `forget removes the matching entry and nothing else`() {
        val list = TrustedTvRegistry.remember(
            TrustedTvRegistry.remember(emptyList(), tv("Salon", "fp1")),
            tv("Yatak Odası", "fp2"),
        )
        val afterForget = TrustedTvRegistry.forget(list, "fp1")

        assertEquals(1, afterForget.size)
        assertEquals("fp2", afterForget.first().fingerprintSha256)
    }

    @Test
    fun `G029 - after forgetting a tv, isTrusted never returns true for it again`() {
        val list = TrustedTvRegistry.remember(emptyList(), tv("Salon", "fp1"))
        assertTrue(TrustedTvRegistry.isTrusted(list, "fp1"))

        val afterForget = TrustedTvRegistry.forget(list, "fp1")
        assertFalse(TrustedTvRegistry.isTrusted(afterForget, "fp1"))
        assertNull(TrustedTvRegistry.find(afterForget, "fp1"))
    }

    @Test
    fun `forgetting a fingerprint that is not remembered is a harmless no-op`() {
        val list = TrustedTvRegistry.remember(emptyList(), tv("Salon", "fp1"))
        val result = TrustedTvRegistry.forget(list, "does-not-exist")
        assertEquals(list, result)
    }

    @Test
    fun `find returns the matching trusted tv or null`() {
        val list = TrustedTvRegistry.remember(emptyList(), tv("Salon", "fp1"))
        assertEquals("Salon", TrustedTvRegistry.find(list, "fp1")?.displayName)
        assertNull(TrustedTvRegistry.find(list, "unknown"))
    }
}
