package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.Cue

/**
 * Bir cue'yu gösteren çıktı hedefi — telefon ekranı (Compose overlay) veya
 * ileride TV alıcısı (`TvReceiverAdapter` üzerinden). Pipeline, aynı anda
 * birden çok sink'e yayın yapabilir (ör. hem telefon hem TV).
 */
interface SubtitleSink {
    fun show(cue: Cue)

    /** [cueId]'li cue süresi doldu veya oturum bitti; ekrandan kaldır. */
    fun clear(cueId: String)
}
