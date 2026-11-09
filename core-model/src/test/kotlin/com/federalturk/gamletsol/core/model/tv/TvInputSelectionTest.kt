package com.federalturk.gamletsol.core.model.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvInputSelectionTest {

    @Test
    fun `empty list returns null`() {
        assertNull(selectPreferredHdmiPassthroughInput(emptyList()))
    }

    @Test
    fun `no hdmi passthrough input returns null`() {
        val inputs = listOf(
            TvInputSummary(inputId = "tuner-1", isHdmi = false, isPassthrough = true),
            TvInputSummary(inputId = "hdmi-app-1", isHdmi = true, isPassthrough = false),
        )
        assertNull(selectPreferredHdmiPassthroughInput(inputs))
    }

    @Test
    fun `single hdmi passthrough input is selected`() {
        val target = TvInputSummary(inputId = "hdmi-1", isHdmi = true, isPassthrough = true)
        val inputs = listOf(
            TvInputSummary(inputId = "tuner-1", isHdmi = false, isPassthrough = true),
            target,
        )
        assertEquals(target, selectPreferredHdmiPassthroughInput(inputs))
    }

    @Test
    fun `first hdmi passthrough input wins when several exist`() {
        val first = TvInputSummary(inputId = "hdmi-1", isHdmi = true, isPassthrough = true)
        val second = TvInputSummary(inputId = "hdmi-2", isHdmi = true, isPassthrough = true)
        val inputs = listOf(first, second)
        assertEquals(first, selectPreferredHdmiPassthroughInput(inputs))
    }
}
