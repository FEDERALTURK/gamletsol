package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvNsdAttributesTest {

    @Test
    fun `round-trips through toTxtRecordMap and fromTxtRecordMap`() {
        val original = TvNsdAttributes(
            pairingCode = "7F3K-9QXZ",
            fingerprintSha256 = "a1b2c3d4e5f6" + "0".repeat(52),
            protocolVersion = 1,
        )
        val decoded = TvNsdAttributes.fromTxtRecordMap(original.toTxtRecordMap())
        assertEquals(original, decoded)
    }

    @Test
    fun `fromTxtRecordMap returns null when pairing code is missing`() {
        val attrs = mapOf(
            TvNsdAttributes.KEY_FINGERPRINT to "abc".toByteArray(),
            TvNsdAttributes.KEY_PROTOCOL_VERSION to "1".toByteArray(),
        )
        assertNull(TvNsdAttributes.fromTxtRecordMap(attrs))
    }

    @Test
    fun `fromTxtRecordMap returns null when fingerprint is missing`() {
        val attrs = mapOf(
            TvNsdAttributes.KEY_PAIRING_CODE to "7F3K-9QXZ".toByteArray(),
            TvNsdAttributes.KEY_PROTOCOL_VERSION to "1".toByteArray(),
        )
        assertNull(TvNsdAttributes.fromTxtRecordMap(attrs))
    }

    @Test
    fun `fromTxtRecordMap returns null when protocol version is missing or malformed`() {
        val missing = mapOf(
            TvNsdAttributes.KEY_PAIRING_CODE to "7F3K-9QXZ".toByteArray(),
            TvNsdAttributes.KEY_FINGERPRINT to "abc".toByteArray(),
        )
        assertNull(TvNsdAttributes.fromTxtRecordMap(missing))

        val malformed = missing + (TvNsdAttributes.KEY_PROTOCOL_VERSION to "not-a-number".toByteArray())
        assertNull(TvNsdAttributes.fromTxtRecordMap(malformed))
    }

    @Test
    fun `fromTxtRecordMap returns null for a value that is a null byte array (unresolved NSD attribute)`() {
        // Gerçek Android NsdServiceInfo.attributes, henüz tam çözümlenmemiş
        // bir girişte değeri `null` olarak dönebilir — bu durumu simüle eder.
        val attrs = mapOf<String, ByteArray?>(
            TvNsdAttributes.KEY_PAIRING_CODE to null,
            TvNsdAttributes.KEY_FINGERPRINT to "abc".toByteArray(),
            TvNsdAttributes.KEY_PROTOCOL_VERSION to "1".toByteArray(),
        )
        assertNull(TvNsdAttributes.fromTxtRecordMap(attrs))
    }

    @Test
    fun `fromTxtRecordMap returns null for an unrelated TXT record set`() {
        assertNull(TvNsdAttributes.fromTxtRecordMap(mapOf("unrelated" to "value".toByteArray())))
    }

    @Test
    fun `fromTxtRecordMap returns null for an empty map`() {
        assertNull(TvNsdAttributes.fromTxtRecordMap(emptyMap()))
    }
}
