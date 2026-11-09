package com.federalturk.gamletsol.core.model.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionDisplaySettingsTest {

    @Test
    fun `defaults match the documented factory values`() {
        val defaults = CaptionDisplaySettings.DEFAULT
        assertEquals(CaptionDisplaySettings.DEFAULT_FONT_SCALE, defaults.fontScale)
        assertEquals(CaptionVerticalPosition.BOTTOM, defaults.position)
        assertEquals(CaptionDisplaySettings.DEFAULT_BACKGROUND_OPACITY, defaults.backgroundOpacity)
        assertEquals(CaptionDisplaySettings.DEFAULT_SAFE_AREA_MARGIN_DP, defaults.safeAreaMarginDp)
    }

    @Test
    fun `font scale increases by one step and clamps at the maximum`() {
        var settings = CaptionDisplaySettings.DEFAULT
        settings = settings.withFontScaleDelta(1)
        assertEquals(1.1f, settings.fontScale, 0.0001f)

        // Çok fazla artırma denemesi tavanı AŞMAMALI.
        repeat(100) { settings = settings.withFontScaleDelta(1) }
        assertEquals(CaptionDisplaySettings.MAX_FONT_SCALE, settings.fontScale, 0.0001f)
    }

    @Test
    fun `font scale decreases and clamps at the minimum, never going negative`() {
        var settings = CaptionDisplaySettings.DEFAULT
        repeat(100) { settings = settings.withFontScaleDelta(-1) }
        assertEquals(CaptionDisplaySettings.MIN_FONT_SCALE, settings.fontScale, 0.0001f)
    }

    @Test
    fun `repeated small steps do not drift due to floating point accumulation`() {
        // 0.1f'yi 7 kez üst üste eklemek, klasik IEEE-754 testinde
        // 0.70000000000000001 gibi "temiz olmayan" bir değer üretebilir —
        // [CaptionDisplaySettings] bunu her adımda 2 ondalığa yuvarlayarak
        // önler.
        var settings = CaptionDisplaySettings.DEFAULT
        repeat(7) { settings = settings.withFontScaleDelta(1) }
        assertEquals(1.7f, settings.fontScale, 0.0001f)
    }

    @Test
    fun `background opacity clamps within 0 and 1`() {
        var settings = CaptionDisplaySettings.DEFAULT
        repeat(100) { settings = settings.withBackgroundOpacityDelta(1) }
        assertEquals(CaptionDisplaySettings.MAX_BACKGROUND_OPACITY, settings.backgroundOpacity, 0.0001f)

        repeat(100) { settings = settings.withBackgroundOpacityDelta(-1) }
        assertEquals(CaptionDisplaySettings.MIN_BACKGROUND_OPACITY, settings.backgroundOpacity, 0.0001f)
    }

    @Test
    fun `safe area margin clamps within its documented bounds`() {
        var settings = CaptionDisplaySettings.DEFAULT
        repeat(100) { settings = settings.withSafeAreaMarginDelta(1) }
        assertEquals(CaptionDisplaySettings.MAX_SAFE_AREA_MARGIN_DP, settings.safeAreaMarginDp)

        repeat(100) { settings = settings.withSafeAreaMarginDelta(-1) }
        assertEquals(CaptionDisplaySettings.MIN_SAFE_AREA_MARGIN_DP, settings.safeAreaMarginDp)
    }

    @Test
    fun `toggling position flips between top and bottom`() {
        var settings = CaptionDisplaySettings.DEFAULT
        assertEquals(CaptionVerticalPosition.BOTTOM, settings.position)
        settings = settings.withTogglePosition()
        assertEquals(CaptionVerticalPosition.TOP, settings.position)
        settings = settings.withTogglePosition()
        assertEquals(CaptionVerticalPosition.BOTTOM, settings.position)
    }

    @Test
    fun `reset returns to factory defaults after arbitrary adjustments`() {
        var settings = CaptionDisplaySettings.DEFAULT
            .withFontScaleDelta(5)
            .withBackgroundOpacityDelta(-3)
            .withSafeAreaMarginDelta(2)
            .withTogglePosition()

        settings = settings.reset()

        assertEquals(CaptionDisplaySettings.DEFAULT, settings)
    }
}
