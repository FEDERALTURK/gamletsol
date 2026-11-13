package com.federalturk.gamletsol.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTokenRegistryTest {

    @Test
    fun `a token is single use`() {
        val registry = ResumeTokenRegistry()
        val token = registry.issue()

        assertEquals(32, token.length)
        assertTrue(registry.validateAndConsume(token))
        assertFalse("Aynı jeton ikinci kez kabul edilmemeli", registry.validateAndConsume(token))
    }

    @Test
    fun `tokens expire`() {
        var now = 0L
        val registry = ResumeTokenRegistry(ttlMillis = 1_000, clock = { now })
        val token = registry.issue()

        now = 1_000
        assertFalse(registry.validateAndConsume(token))
    }

    @Test
    fun `unknown tokens are rejected and tokens are unique`() {
        val registry = ResumeTokenRegistry()
        assertFalse(registry.validateAndConsume("deadbeef"))
        assertNotEquals(registry.issue(), registry.issue())
    }

    @Test
    fun `oldest token is evicted past the limit and revokeAll clears everything`() {
        val registry = ResumeTokenRegistry(maxTokens = 2)
        val first = registry.issue()
        val second = registry.issue()
        val third = registry.issue()

        assertFalse(registry.validateAndConsume(first))
        assertTrue(registry.validateAndConsume(third))

        registry.revokeAll()
        assertFalse(registry.validateAndConsume(second))
    }
}
