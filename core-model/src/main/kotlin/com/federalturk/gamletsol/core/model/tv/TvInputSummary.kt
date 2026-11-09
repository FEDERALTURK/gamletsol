package com.federalturk.gamletsol.core.model.tv

/**
 * Platformdan bağımsız özet — Android'in `TvInputInfo`sunun saf Kotlin
 * karşılığı. `app-tv` modülü gerçek `android.media.tv.TvInputManager`'dan
 * dönen listeyi bu tipe eşler; seçim mantığı (aşağıdaki
 * [selectPreferredHdmiPassthroughInput]) böylece Android SDK'sız test
 * edilebilir.
 *
 * NOT (dürüstlük ilkesi): `isHdmi`/`isPassthrough` alanlarının gerçek bir
 * Profilo/Android TV cihazında `TvInputInfo.getType()` ve
 * `TvInputInfo.isPassthroughInput()` ile birebir nasıl eşleneceği bu kum
 * havuzunda doğrulanmadı — bu dosya yalnızca seçim/sıralama MANTIĞINI
 * test eder, gerçek cihaz eşlemesini değil (bkz. `app-tv` modülündeki
 * eşleme kodu ve oradaki TODO yorumu).
 */
data class TvInputSummary(
    val inputId: String,
    val isHdmi: Boolean,
    val isPassthrough: Boolean,
    val label: String? = null,
)

/**
 * Xbox/konsol gibi bir kaynağın bağlı olabileceği, geçiş (passthrough) HDMI
 * girişlerinden ilkini seçer. Birden fazla HDMI passthrough girişi varsa
 * (örn. TV'de 3 HDMI portu), bu fonksiyon yalnızca **listedeki sırayı**
 * kullanır — gerçekte hangisine bir kaynağın bağlı/aktif olduğunu bilmez
 * (bu bilgi `TvInputInfo`dan/`HdmiControlManager`dan gelmeli, G002'nin
 * konusu). Hiçbir HDMI passthrough girişi yoksa `null` döner.
 */
fun selectPreferredHdmiPassthroughInput(inputs: List<TvInputSummary>): TvInputSummary? =
    inputs.firstOrNull { it.isHdmi && it.isPassthrough }
