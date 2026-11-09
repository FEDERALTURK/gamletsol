package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrPayloadTest {

    private val sampleFingerprint = "a1b2c3d4e5f6" + "0".repeat(52) // 64 hex karakter, gerçekçi uzunluk

    @Test
    fun `round-trips through encode and decode`() {
        val payload = PairingQrPayload(
            host = "192.168.1.23",
            port = 48631,
            code = "7F3K-9QXZ",
            fingerprintSha256 = sampleFingerprint,
        )
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
        assertNull(PairingQrPayload.decode("gamletsol-pair:3;host=1.2.3.4;port=1;code=X;fingerprint=$sampleFingerprint"))
    }

    @Test
    fun `decode returns null for the old pre-G024 v1 format without a fingerprint`() {
        // G023'ün ilk sürümünde üretilen bir QR (fingerprint alanı yok) —
        // bilerek reddedilir, bkz. sınıf dokümantasyonu (yarı güvenli bir
        // bağlantıya izin vermemek için).
        assertNull(PairingQrPayload.decode("gamletsol-pair:1;host=1.2.3.4;port=1;code=X"))
    }

    @Test
    fun `decode returns null when a required field is missing`() {
        assertNull(PairingQrPayload.decode("gamletsol-pair:2;host=1.2.3.4;code=X;fingerprint=$sampleFingerprint"))
    }

    @Test
    fun `decode returns null when the fingerprint field is missing`() {
        assertNull(PairingQrPayload.decode("gamletsol-pair:2;host=1.2.3.4;port=1;code=X"))
    }

    @Test
    fun `decode returns null for a malformed port`() {
        assertNull(
            PairingQrPayload.decode(
                "gamletsol-pair:2;host=1.2.3.4;port=not-a-number;code=X;fingerprint=$sampleFingerprint",
            ),
        )
    }

    @Test
    fun `decode rejects a QR belonging to some unrelated app`() {
        // Kullanıcı yanlışlıkla ilgisiz bir QR koda (ör. bir Wi-Fi QR'ı)
        // kamerayı doğrultursa, bunu sessizce "eşleştirme" olarak
        // yorumlamamalıyız.
        assertNull(PairingQrPayload.decode("WIFI:S:MyNetwork;T:WPA;P:secret;;"))
    }
}
