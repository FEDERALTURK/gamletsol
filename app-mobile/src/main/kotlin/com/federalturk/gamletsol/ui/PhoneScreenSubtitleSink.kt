package com.federalturk.gamletsol.ui

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SubtitleSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pipeline'dan gelen cue'ları Compose'un gözlemleyebileceği bir [StateFlow]'a
 * aktaran sink — telefon ekranı (varsayılan/fallback) için. TV gösterimine
 * (P3, `TvReceiverAdapter`) ayrı bir [SubtitleSink] uygulaması eklenecek;
 * pipeline aynı anda birden çok sink'e yayın yapabildiği için ikisi bir arada
 * çalışabilecek.
 */
class PhoneScreenSubtitleSink : SubtitleSink {
    private val _activeCue = MutableStateFlow<Cue?>(null)
    val activeCue: StateFlow<Cue?> = _activeCue.asStateFlow()

    override fun show(cue: Cue) {
        _activeCue.value = cue
    }

    override fun clear(cueId: String) {
        if (_activeCue.value?.cueId == cueId) {
            _activeCue.value = null
        }
    }
}
