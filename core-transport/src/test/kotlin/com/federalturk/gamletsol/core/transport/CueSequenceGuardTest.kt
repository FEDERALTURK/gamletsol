package com.federalturk.gamletsol.core.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSequenceGuardTest {

    private val event = CueEvent("hello", "merhaba", 0L)

    private fun decoded(sequence: Long, epochId: String) = DecodedCueEvent(event, sequence, epochId)

    @Test
    fun `first event of a new epoch is always accepted`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(sequence = 5L, epochId = "e1")))
    }

    @Test
    fun `strictly increasing sequence within the same epoch is accepted`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(0L, "e1")))
        assertTrue(guard.accept(decoded(1L, "e1")))
        assertTrue(guard.accept(decoded(2L, "e1")))
    }

    @Test
    fun `a repeated sequence (replay) is rejected`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(3L, "e1")))
        assertFalse(guard.accept(decoded(3L, "e1")))
    }

    @Test
    fun `a lower sequence (reorder) is rejected`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(5L, "e1")))
        assertFalse(guard.accept(decoded(2L, "e1")))
    }

    @Test
    fun `a new epoch resets the sequence tracking, even to a lower number`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(100L, "e1")))
        // G028: yeniden bağlanma — yeni bir epoch (ör. telefon yeniden
        // bağlandı) kendi 0'dan başlayan sayımıyla kabul edilmeli, eski
        // epoch'un son sırası ile kıyaslanmamalı.
        assertTrue(guard.accept(decoded(0L, "e2")))
        assertTrue(guard.accept(decoded(1L, "e2")))
    }

    @Test
    fun `after switching epochs, the old epoch's sequence numbers no longer apply`() {
        val guard = CueSequenceGuard()
        assertTrue(guard.accept(decoded(0L, "e1")))
        assertTrue(guard.accept(decoded(0L, "e2")))
        // e2 bağlamında sequence=0 zaten kabul edildi — tekrar 0 gelirse
        // (e2 içinde) reddedilmeli, e1'deki geçmişle değil kendi geçmişiyle
        // kıyaslanmalı.
        assertFalse(guard.accept(decoded(0L, "e2")))
    }
}
