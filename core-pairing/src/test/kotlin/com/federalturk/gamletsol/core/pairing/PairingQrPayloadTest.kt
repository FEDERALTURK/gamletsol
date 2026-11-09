package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrPayloadTest {

    @Test
    fun `round-trips through encode and decode`() {
        val payload = PairingQrPayload(host = "192.168.1.23", port = 48631, code = "7F3K-9QXZ")
        assertEquals(payload, PairingQrPayload.decode(payload.encode()))
    }

    @Test
    fun `decode returns null for a null string`() {
        assertNull(PairingQrPayload.decode(null))
    }

    @Test
    fun `decode returns null without the expected prefix`() {
        assertNull(PairingQrPayload.decode("CAPS:1;clear=false"))
    }

    @Test
    fun `decode returns null for an unrecognized future version`() {
        assertNull(PairingQrPayload.decode("gamletsol-pair:2;host=1.2.3.4;port=1;code=X"))
    }

    @Test
    fun `decode returns null when a required field is missing`() {
        assertNull(PairingQrPayload.decode("gamletsol-pair:1;host=1.2.3.4;code=X"))
    }

    @Test
    fun `decode returns null for a malformed port`() {
        assertNull(PairingQrPayload.decode("gamletsol-pair:1;host=1.2.3.4;port=not-a-number;code=X"))
    }

    @Test
    fun `decode rejects a QR belonging to some unrelated app`() {
        // Kullanıcı yanlışlıkla ilgisiz bir QR koda (ör. bir Wi-Fi QR'ı)
        // kamerayı doğrultursa, bunu sessizce "eşleştirme" olarak
        // yorumlamamalıyız.
        assertNull(PairingQrPayload.decode("WIFI:S:MyNetwork;T:WPA;P:secret;;"))
    }
}
