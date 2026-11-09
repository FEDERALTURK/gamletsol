package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueEventCodecTest {

    @Test
    fun `encode then decode returns the original event`() {
        val event = CueEvent(
            originalText = "Watch out, the enemy is behind you!",
            translatedText = "Dikkat, düşman arkanda!",
            timestampMs = 1_731_000_000_000L,
        )

        val decoded = CueEventCodec.decode(CueEventCodec.encode(event))

        assertEquals(event, decoded)
    }

    @Test
    fun `text containing the delimiter and newlines survives round trip`() {
        val event = CueEvent(
            originalText = "A | B\nC",
            translatedText = "Ç | Ş\nĞ",
            timestampMs = 0L,
        )

        val decoded = CueEventCodec.decode(CueEventCodec.encode(event))

        assertEquals(event, decoded)
    }

    @Test
    fun `malformed line decodes to null instead of throwing`() {
        assertNull(CueEventCodec.decode("not-a-valid-cue-line"))
        assertNull(CueEventCodec.decode("tooFew|parts"))
        assertNull(CueEventCodec.decode("###|###|notANumber"))
    }
}
