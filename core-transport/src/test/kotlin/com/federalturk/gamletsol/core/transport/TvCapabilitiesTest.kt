package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvCapabilitiesTest {

    @Test
    fun `CURRENT round-trips through encode and decode`() {
        val encoded = TvCapabilities.CURRENT.encode()
        assertEquals(TvCapabilities.CURRENT, TvCapabilities.decode(encoded))
    }

    @Test
    fun `decode is tolerant of flags being reordered`() {
        val reordered = "CAPS:1;layout=false;heartbeat=false;clear=false"
        assertEquals(TvCapabilities.CURRENT, TvCapabilities.decode(reordered))
    }

    @Test
    fun `decode returns null for a line missing the CAPS prefix`() {
        assertNull(TvCapabilities.decode("PAIR_OK"))
    }

    @Test
    fun `decode returns null for a null line`() {
        assertNull(TvCapabilities.decode(null))
    }

    @Test
    fun `decode returns null for a malformed version number`() {
        assertNull(TvCapabilities.decode("CAPS:not-a-number;clear=false"))
    }

    @Test
    fun `decode defaults missing flags to false rather than throwing`() {
        // Gelecekte eski bir istemci, yeni bir sunucunun henüz bilmediği
        // bayrakları İÇERMEYEN bir satır alırsa (ör. protokol v1'den önceki
        // bir sürüm, varsayımsal) çökmemeli, bilinmeyen bayrağı false kabul
        // etmelidir.
        val minimal = TvCapabilities.decode("CAPS:1")
        assertEquals(TvCapabilities(1, supportsClear = false, supportsHeartbeat = false, supportsLayout = false), minimal)
    }
}
