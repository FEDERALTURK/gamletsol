package com.federalturk.gamletsol.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G036 doğrulama (dürüstlük ilkesi): bu testler yalnız [AdaptiveFrameThrottle]'ın
 * "yük artınca atlama oranı kontrollü artar/azalır" MANTIĞINI doğrular — gerçek
 * bir telefonda 60-120 dakikalık pil/ısı/RAM profili DEĞİLDİR (bu kum
 * havuzunda hiç yapılamadı, gerçek cihaz gerektirir).
 */
class AdaptiveFrameThrottleTest {

    @Test
    fun `starts with no skipping - every frame is processed`() {
        val throttle = AdaptiveFrameThrottle()

        repeat(10) {
            assertTrue("Başlangıçta hiçbir kare atlanmamalı", throttle.shouldProcess())
        }
        assertEquals(1, throttle.currentSkipFactor())
    }

    @Test
    fun `sustained slow frames increase the skip factor`() {
        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 100L, windowSize = 3, maxSkipFactor = 4)

        // Hedef bütçe 100ms, aşırı yük eşiği 150ms (1.5x) — 3 kare üst üste
        // 300ms sürerse (çok yavaş), skipFactor 1'den artmalı.
        repeat(3) { throttle.recordProcessingDuration(300L) }

        assertTrue("Sürekli yavaş kareler sonrası skipFactor artmalı", throttle.currentSkipFactor() > 1)
    }

    @Test
    fun `skip factor never exceeds the configured maximum`() {
        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 10L, windowSize = 2, maxSkipFactor = 3)

        // Çok uzun süre aşırı yavaş kareler besle — skipFactor 3'ü aşmamalı.
        repeat(50) { throttle.recordProcessingDuration(1_000L) }

        assertEquals(3, throttle.currentSkipFactor())
    }

    @Test
    fun `a single slow frame within an unfilled window does not trigger skipping yet`() {
        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 10L, windowSize = 5, maxSkipFactor = 4)

        // Pencere 5 - yalnız 1 ölçüm var, henüz karar verilmemeli (histerezis).
        throttle.recordProcessingDuration(10_000L)

        assertEquals(1, throttle.currentSkipFactor())
    }

    @Test
    fun `recovering to fast frames brings the skip factor back down`() {
        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 100L, windowSize = 2, maxSkipFactor = 4)

        // Önce yavaşlat (skipFactor artsın)...
        repeat(2) { throttle.recordProcessingDuration(1_000L) }
        val elevated = throttle.currentSkipFactor()
        assertTrue(elevated > 1)

        // ...sonra sürekli hızlı kareler besle (bütçenin %80'inin altı) — geri düşmeli.
        repeat(10) { throttle.recordProcessingDuration(10L) }

        assertEquals(1, throttle.currentSkipFactor())
    }

    @Test
    fun `shouldProcess pattern matches the current skip factor`() {
        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 10L, windowSize = 1, maxSkipFactor = 4)

        // Tek ölçümle skipFactor'ü 2'ye çıkar.
        throttle.recordProcessingDuration(1_000L)
        assertEquals(2, throttle.currentSkipFactor())

        // skipFactor=2: atla, işle, atla, işle, ...
        val pattern = (1..6).map { throttle.shouldProcess() }
        assertEquals(listOf(false, true, false, true, false, true), pattern)
    }
}
