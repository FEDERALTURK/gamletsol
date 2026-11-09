package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.Cue

/**
 * Türkçe sesli okuma çıktısı (Android `TextToSpeech`). Pipeline bunu yalnızca
 * gerçekten YENİ bir cue için çağırır — aynı metnin TTL yenilemesi veya eski/
 * tutarsız bir yanıt asla TTS'i tekrar tetiklemez (bkz. "Kimlik takibi").
 */
interface SpeechOutput {
    fun speak(cue: Cue)

    fun stop()
}
