package com.federalturk.gamletsol.core.model

/**
 * Yol haritasındaki "İki ayrı mod" ayrımı:
 * - [SUBTITLE]: alt bant/ROI, 1-3 satır, kısa TTL (2-6 sn) — sürekli canlı akış.
 * - [SMART_SCREEN]: Akıllı Ekran (Pro), tam görüntü blok tespiti, ilk sürümde
 *   "durdur ve çevir" — sürekli tam ekran çevirisi değil.
 */
enum class CaptureMode {
    SUBTITLE,
    SMART_SCREEN,
}
