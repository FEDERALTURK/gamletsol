package com.federalturk.gamletsol

import android.content.Context

private const val PREFS_NAME = "gamletsol_prefs"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

/**
 * Basit `SharedPreferences` sarmalayıcısı — yalnızca "tanıtım/kurulum
 * ekranları ilk açılışta gösterildi mi" bayrağını saklar.
 *
 * NOT (dürüstlük ilkesi / mimari sınır): Kalıcı ayarlar için planlanan
 * gerçek modül `data-local` (Room/DataStore, yol haritası P2-P3) henüz
 * eklenmedi. Bu tek bayrak için ayrı bir modül/kütüphane eklemek orantısız
 * olduğundan düz `SharedPreferences` kullanıldı; `data-local` eklendiğinde
 * bu sınıf oraya taşınabilir.
 */
class OnboardingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
}
