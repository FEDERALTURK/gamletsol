package com.federalturk.gamletsol.tv

import android.content.Context

/**
 * "Oyun görüntüsü" tercihleri (2026-11-13): telefon bağlıyken HDMI girişindeki
 * oyun görüntüsünün bu uygulamanın içinde, altyazının arkasında gösterilip
 * gösterilmeyeceği ve hangi HDMI girişinin kullanılacağı. TV yeniden
 * açıldığında korunur.
 */
object GameViewPrefs {
    private const val PREFS_NAME = "game_view"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_INPUT_ID = "input_id"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun inputId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_INPUT_ID, null)

    fun setInputId(context: Context, inputId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_INPUT_ID, inputId).apply()
    }
}
