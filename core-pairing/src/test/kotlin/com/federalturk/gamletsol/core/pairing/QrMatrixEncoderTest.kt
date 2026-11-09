package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatrixEncoderTest {

    @Test
    fun `encodes a real pairing payload into a square matrix with at least one dark cell`() {
        val payload = PairingQrPayload(host = "192.168.1.23", port = 48631, code = "7F3K-9QXZ")
        val matrix = QrMatrixEncoder.encode(payload.encode())

        assertTrue("QR matrisi en az bir hücre içermeli", matrix.size > 0)

        var sawDark = false
        var sawLight = false
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) sawDark = true else sawLight = true
            }
        }
        assertTrue("gerçek bir QR hem karanlık hem açık hücreler içermeli (düz siyah/beyaz olmamalı)", sawDark && sawLight)
    }

    @Test
    fun `longer text produces a larger (or equal) matrix than shorter text`() {
        val shortMatrix = QrMatrixEncoder.encode("gamletsol-pair:1;host=1.1.1.1;port=1;code=A")
        val longMatrix = QrMatrixEncoder.encode(
            "gamletsol-pair:1;host=255.255.255.255;port=65535;code=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        )
        assertTrue(
            "daha uzun metin daha küçük bir QR ızgarası üretmemeli",
            longMatrix.size >= shortMatrix.size,
        )
    }

    @Test
    fun `blank text is rejected rather than silently producing an empty QR`() {
        assertThrows(IllegalArgumentException::class.java) {
            QrMatrixEncoder.encode("")
        }
    }

    @Test
    fun `isDark throws for out-of-bounds coordinates rather than returning a wrong default`() {
        val matrix = QrMatrixEncoder.encode("gamletsol-pair:1;host=1.1.1.1;port=1;code=A")
        assertThrows(IllegalArgumentException::class.java) {
            matrix.isDark(matrix.size, 0)
        }
    }
}
