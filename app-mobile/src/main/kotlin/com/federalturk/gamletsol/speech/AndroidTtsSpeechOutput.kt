package com.federalturk.gamletsol.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SpeechOutput
import java.util.Locale

/**
 * Android `TextToSpeech` tabanlı genel Türkçe sesli çıktı — yol haritasının
 * "Türkçe sesli çıkış, iki ayrı ürün" bölümündeki *Sesli çeviri* ürünü.
 * Seçili oyunların karakter dublajı (hakları alınmış, ayrı indirilen ses
 * paketi) ayrı ve ileride eklenecek bir [SpeechOutput] uygulamasıdır.
 */
class AndroidTtsSpeechOutput(context: Context) : SpeechOutput {

    @Volatile
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
        }
    }

    init {
        tts.language = Locale("tr", "TR")
    }

    override fun speak(cue: Cue) {
        val text = cue.translatedText
        if (!ready || text.isNullOrBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, cue.cueId)
    }

    override fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
