package com.federalturk.gamletsol.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTest {
    private fun cue(seq: Long, createdAtMillis: Long = 0L, ttlMillis: Long = Cue.DEFAULT_SUBTITLE_TTL_MILLIS) = Cue(
        cueId = "cue-1",
        revision = 0,
        seq = seq,
        frame = FrameRef(sourceEpoch = 1, frameId = seq, capturedAtMillis = createdAtMillis),
        mode = CaptureMode.SUBTITLE,
        region = TextRegion.DEFAULT_SUBTITLE_BAND,
        originalText = "Hello",
        translatedText = "Merhaba",
        createdAtMillis = createdAtMillis,
        ttlMillis = ttlMillis,
    )

    @Test
    fun `higher seq supersedes lower seq`() {
        val older = cue(seq = 1)
        val newer = cue(seq = 2)
        assertTrue(older.isSupersededBy(newer))
        assertFalse(newer.isSupersededBy(older))
    }

    @Test
    fun `cue expires after its ttl elapses`() {
        val c = cue(seq = 1, createdAtMillis = 1_000L, ttlMillis = 4_000L)
        assertFalse(c.isExpiredAt(nowMillis = 4_999L))
        assertTrue(c.isExpiredAt(nowMillis = 5_000L))
    }
}
