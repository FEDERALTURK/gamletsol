package com.federalturk.gamletsol.tv

import android.content.Context
import com.federalturk.gamletsol.core.model.tv.CaptionDisplaySettings
import com.federalturk.gamletsol.core.model.tv.CaptionVerticalPosition

/**
 * [CaptionDisplaySettings]'in (yol haritası G027) Android `SharedPreferences`
 * ile KALICI depolanması — TV yeniden başlatıldığında kullanıcının seçtiği
 * font/konum/arka plan/kenar boşluğu ayarları KAYBOLMAZ (G027'nin kendisi
 * saf Kotlin/JVM'dir, bu dosya onu Android'e bağlayan tek yerdir, bkz.
 * AGENTS.md madde 5 — modül sınırları).
 */
object CaptionDisplayPrefs {
    private const val PREFS_NAME = "caption_display_settings"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_POSITION = "position"
    private const val KEY_BACKGROUND_OPACITY = "background_opacity"
    private const val KEY_SAFE_AREA_MARGIN_DP = "safe_area_margin_dp"

    fun load(context: Context): CaptionDisplaySettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = CaptionDisplaySettings.DEFAULT
        return CaptionDisplaySettings(
            fontScale = prefs.getFloat(KEY_FONT_SCALE, defaults.fontScale),
            position = prefs.getString(KEY_POSITION, null)?.let { stored ->
                runCatching { CaptionVerticalPosition.valueOf(stored) }.getOrNull()
            } ?: defaults.position,
            backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, defaults.backgroundOpacity),
            safeAreaMarginDp = prefs.getInt(KEY_SAFE_AREA_MARGIN_DP, defaults.safeAreaMarginDp),
        )
    }

    fun save(context: Context, settings: CaptionDisplaySettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_FONT_SCALE, settings.fontScale)
            .putString(KEY_POSITION, settings.position.name)
            .putFloat(KEY_BACKGROUND_OPACITY, settings.backgroundOpacity)
            .putInt(KEY_SAFE_AREA_MARGIN_DP, settings.safeAreaMarginDp)
            .apply()
    }
}
