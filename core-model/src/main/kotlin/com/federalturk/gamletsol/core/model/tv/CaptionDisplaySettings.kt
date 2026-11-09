package com.federalturk.gamletsol.core.model.tv

/**
 * Altyazının TV ekranındaki dikey konumu (yol haritası G027 — "TV yazı
 * yerleşimi ve okunabilirlik").
 */
enum class CaptionVerticalPosition {
    TOP,
    BOTTOM,
}

/**
 * TV ekranında altyazının NASIL çizileceğini belirleyen, kullanıcı tarafından
 * kumandayla ayarlanabilir görüntü ayarları (yol haritası G027).
 *
 * NOT (modül sınırı, bkz. AGENTS.md madde 5): Bu sınıf saf Kotlin/JVM'dir
 * (Android bağımlılığı YOK) — `app-tv`'deki gerçek D-pad/Compose UI kodu
 * bu veriyi TÜKETİR ama üretme/doğrulama (clamp) mantığının kendisi burada,
 * Android SDK'sız test edilebilir şekilde yaşar. Kalıcı depolama (ör.
 * Android `SharedPreferences`) `app-tv`'de yaşar — bu sınıf yalnızca
 * değerleri ve onları güvenli aralıkta tutan artırma/azaltma işlemlerini
 * tanımlar.
 *
 * Her alan, kumandanın tek bir tuşuyla (ör. D-pad sağ/sol) adım adım
 * değiştirilebilir ([withFontScaleDelta] vb.) — bu metodlar HER ZAMAN
 * sonucu [MIN_FONT_SCALE]..[MAX_FONT_SCALE] gibi güvenli bir aralığa
 * kelepçeler (clamp), bu yüzden çağıran kod (örn. tekrar tekrar "sağ" tuşuna
 * basılması) hiçbir zaman kullanılamaz/mantıksız bir değere ULAŞAMAZ.
 *
 * NOT (dürüstlük ilkesi / MVP sınırı): Bu sınıfın kendisi ve kelepçeleme
 * mantığı bu kum havuzunda birim test edildi — ama "3 metre izleme, büyük
 * font, uzun cümle ve overscan benzetimiyle kullanıcı kontrolü" (yol
 * haritasının G027 doğrulama kriteri) ve kumandanın GERÇEK davranışı
 * (D-pad tuş eşlemesi, fokus) yalnızca gerçek bir Android TV/Google TV
 * cihazında (ör. kullanıcının Profilo TV'si) denenebilir — HENÜZ
 * DENENMEDİ.
 */
data class CaptionDisplaySettings(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val position: CaptionVerticalPosition = CaptionVerticalPosition.BOTTOM,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    val safeAreaMarginDp: Int = DEFAULT_SAFE_AREA_MARGIN_DP,
) {
    /** [fontScale]'i bir adım (ör. D-pad sağ=+1/sol=-1) değiştirir, güvenli aralıkta kelepçeler. */
    fun withFontScaleDelta(steps: Int): CaptionDisplaySettings =
        copy(fontScale = clampAndRound(fontScale + steps * FONT_SCALE_STEP, MIN_FONT_SCALE, MAX_FONT_SCALE))

    /** [backgroundOpacity]'yi bir adım değiştirir, güvenli aralıkta kelepçeler. */
    fun withBackgroundOpacityDelta(steps: Int): CaptionDisplaySettings =
        copy(
            backgroundOpacity = clampAndRound(
                backgroundOpacity + steps * BACKGROUND_OPACITY_STEP,
                MIN_BACKGROUND_OPACITY,
                MAX_BACKGROUND_OPACITY,
            ),
        )

    /** [safeAreaMarginDp]'yi bir adım değiştirir, güvenli aralıkta kelepçeler. */
    fun withSafeAreaMarginDelta(steps: Int): CaptionDisplaySettings =
        copy(
            safeAreaMarginDp = (safeAreaMarginDp + steps * SAFE_AREA_MARGIN_STEP_DP)
                .coerceIn(MIN_SAFE_AREA_MARGIN_DP, MAX_SAFE_AREA_MARGIN_DP),
        )

    /** Dikey konumu (üst/alt) tersine çevirir. */
    fun withTogglePosition(): CaptionDisplaySettings =
        copy(
            position = when (position) {
                CaptionVerticalPosition.BOTTOM -> CaptionVerticalPosition.TOP
                CaptionVerticalPosition.TOP -> CaptionVerticalPosition.BOTTOM
            },
        )

    /** Tüm alanları fabrika varsayılanına döndürür. */
    fun reset(): CaptionDisplaySettings = DEFAULT

    companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_BACKGROUND_OPACITY = 0.6f
        const val DEFAULT_SAFE_AREA_MARGIN_DP = 48

        const val MIN_FONT_SCALE = 0.5f
        const val MAX_FONT_SCALE = 2.5f
        const val FONT_SCALE_STEP = 0.1f

        const val MIN_BACKGROUND_OPACITY = 0.0f
        const val MAX_BACKGROUND_OPACITY = 1.0f
        const val BACKGROUND_OPACITY_STEP = 0.1f

        const val MIN_SAFE_AREA_MARGIN_DP = 16
        const val MAX_SAFE_AREA_MARGIN_DP = 120
        const val SAFE_AREA_MARGIN_STEP_DP = 8

        val DEFAULT = CaptionDisplaySettings()

        /**
         * [value]'yu [min]..[max] aralığına kelepçeler VE 2 ondalık basamağa
         * yuvarlar — ondalık `Float` toplamalarının (ör. 0.1f'yi 7 kez
         * üst üste eklemek) birikimli hassasiyet hatası (floating-point
         * drift) üretmesini önler; kullanıcı aynı tuşa tekrar tekrar
         * bastığında değerler her zaman "temiz" (1.0, 1.1, 1.2, ...) kalır.
         */
        private fun clampAndRound(value: Float, min: Float, max: Float): Float {
            val clamped = value.coerceIn(min, max)
            return Math.round(clamped * 100f) / 100f
        }
    }
}
