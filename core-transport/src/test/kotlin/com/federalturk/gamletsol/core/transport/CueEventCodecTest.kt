package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueEventCodecTest {

    @Test
    fun `encode then decode returns the original event with its sequence and epoch`() {
        val event = CueEvent(
            originalText = "Watch out, the enemy is behind you!",
            translatedText = "Dikkat, düşman arkanda!",
            timestampMs = 1_731_000_000_000L,
        )

        val decoded = CueEventCodec.decode(CueEventCodec.encode(event, sequence = 7L, epochId = "abc123"))

        assertEquals(event, decoded?.event)
        assertEquals(7L, decoded?.sequence)
        assertEquals("abc123", decoded?.epochId)
    }

    @Test
    fun `text containing the delimiter and newlines survives round trip`() {
        val event = CueEvent(
            originalText = "A | B\nC",
            translatedText = "Ç | Ş\nĞ",
            timestampMs = 0L,
        )

        val decoded = CueEventCodec.decode(CueEventCodec.encode(event, sequence = 0L, epochId = "e1"))

        assertEquals(event, decoded?.event)
    }

    @Test
    fun `malformed line decodes to null instead of throwing`() {
        assertNull(CueEventCodec.decode("not-a-valid-cue-line"))
        assertNull(CueEventCodec.decode("tooFew|parts"))
        assertNull(CueEventCodec.decode("###|###|notANumber|0|e1"))
    }

    @Test
    fun `G026 - the pre-G026 three-field format is rejected, not silently accepted`() {
        // Eski (G026 öncesi) 3-alanlı biçim artık GEÇERSİZ sayılmalı — bu
        // depodaki "geriye dönük uyumluluk şiması yok" ilkesiyle uyumlu
        // bilinçli bir breaking change.
        val oldStyleLine = "aGVsbG8=|bWVyaGFiYQ==|0"
        assertNull(CueEventCodec.decode(oldStyleLine))
    }

    @Test
    fun `G026 - an oversized raw line is rejected before parsing`() {
        val hugeLine = "x".repeat(CueEventCodec.MAX_LINE_LENGTH + 1)
        assertNull(CueEventCodec.decode(hugeLine))
    }

    @Test
    fun `G026 - a decoded text field exceeding the length limit is rejected`() {
        val event = CueEvent(
            originalText = "a".repeat(CueEventCodec.MAX_TEXT_LENGTH + 1),
            translatedText = "ok",
            timestampMs = 0L,
        )
        val line = CueEventCodec.encode(event, sequence = 0L, epochId = "e1")
        assertNull(CueEventCodec.decode(line))
    }

    @Test
    fun `G026 - a negative sequence is rejected`() {
        val event = CueEvent("hello", "merhaba", 0L)
        val line = CueEventCodec.encode(event, sequence = 0L, epochId = "e1")
        // sequence alanını elle negatif yap.
        val tamperedParts = line.split("|").toMutableList()
        tamperedParts[3] = "-1"
        assertNull(CueEventCodec.decode(tamperedParts.joinToString("|")))
    }

    @Test
    fun `G026 - a blank or oversized epochId is rejected`() {
        val event = CueEvent("hello", "merhaba", 0L)
        val blankEpoch = CueEventCodec.encode(event, sequence = 0L, epochId = "e1")
            .split("|").toMutableList().also { it[4] = "" }.joinToString("|")
        assertNull(CueEventCodec.decode(blankEpoch))

        val tooLongEpoch = "x".repeat(CueEventCodec.MAX_EPOCH_ID_LENGTH + 1)
        val line = CueEventCodec.encode(event, sequence = 0L, epochId = tooLongEpoch)
        assertNull(CueEventCodec.decode(line))
    }
}
