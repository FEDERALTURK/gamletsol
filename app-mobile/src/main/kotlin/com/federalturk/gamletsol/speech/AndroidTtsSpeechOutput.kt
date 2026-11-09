package com.federalturk.gamletsol.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.pipeline.SpeechOutput
import java.util.Locale

/**
 * Türkçe sesin kurulu olup olmadığı ve ağ gerektirip gerektirmediği (G032
 * kabul kriteri: "Kurulu Türkçe sesler/ağ gereksinimi gösterilir"). UI
 * ([com.federalturk.gamletsol.ui.CaptionScreen]) bunu okuyup kullanıcıya
 * gösterir — ALTYAZI GÖSTERİMİ bu durumdan bağımsızdır: [CaptionPipeline]
 * her durumda `sinks.forEach { it.show(cue) }` çağırır, `speechOutput` ayrı
 * ve isteğe bağlıdır (bkz. core-pipeline `CaptionPipeline.kt`).
 */
sealed interface TurkishVoiceStatus {
    /** TTS motoru henüz başlatılamadı (ilk kısa an) — geçici durum. */
    data object NotYetInitialized : TurkishVoiceStatus

    /** Cihazda hiç Türkçe ses yok / dil desteklenmiyor. */
    data object Unavailable : TurkishVoiceStatus

    /** Türkçe ses var; [requiresNetwork] `true` ise bu ses çevrimdışı ÇALIŞMAZ. */
    data class Available(val requiresNetwork: Boolean) : TurkishVoiceStatus
}

/**
 * Android `TextToSpeech` tabanlı genel Türkçe sesli çıktı — yol haritasının
 * "Türkçe sesli çıkış, iki ayrı ürün" bölümündeki *Sesli çeviri* ürünü.
 * Seçili oyunların karakter dublajı (hakları alınmış, ayrı indirilen ses
 * paketi) ayrı ve ileride eklenecek bir [SpeechOutput] uygulamasıdır.
 *
 * G032/G033 eklemeleri (2026-11-09):
 *  - [turkishVoiceStatus] ile kurulu Türkçe ses / ağ gereksinimi sorgulanabilir.
 *  - Her [speak] çağrısı önce ses odağını (`AudioFocus`) GEÇİCİ olarak ister
 *    (kulaklık/Bluetooth çıkışı dahil, sistem ses yönlendirmesine bırakılır)
 *    ve konuşma bitince/bir hata olunca otomatik olarak bırakır.
 *  - `TextToSpeech.QUEUE_FLUSH` kullanımı (önceden de vardı) eski/bekleyen
 *    bir ifadeyi her yeni [speak] çağrısında atar — kuyruk asla birikmez;
 *    buna ek olarak [stop] de ses odağını bırakır (ör. kullanıcı sahneyi
 *    değiştirip altyazı kapandığında).
 *
 * NOT (dürüstlük ilkesi): Bu sınıf Android'e özgüdür (core-model/core-pipeline
 * kuralı dışında, bkz. AGENTS.md madde 5) ve bu depoda Robolectric/enstrümantasyon
 * testi YOK (önceki sürümünde de yoktu) — ses odağı/gerçek TTS sesi davranışı
 * yalnız CI'nın gerçek `assembleDebug` derlemesiyle doğrulanır, gerçek
 * cihazda/kulaklıkla hiç denenmedi.
 */
class AndroidTtsSpeechOutput(context: Context) : SpeechOutput {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var ready = false

    @Volatile
    private var voiceStatus: TurkishVoiceStatus = TurkishVoiceStatus.NotYetInitialized

    private var focusRequest: AudioFocusRequest? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = TURKISH
            voiceStatus = resolveVoiceStatus()
        } else {
            voiceStatus = TurkishVoiceStatus.Unavailable
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = abandonAudioFocus()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = abandonAudioFocus()
            override fun onError(utteranceId: String?, errorCode: Int) = abandonAudioFocus()
        })
    }

    /** G032: UI'nın "Kurulu Türkçe sesler/ağ gereksinimi" göstermesi için okunur. */
    fun turkishVoiceStatus(): TurkishVoiceStatus = voiceStatus

    override fun speak(cue: Cue) {
        val text = cue.translatedText
        if (!ready || text.isNullOrBlank()) return
        // G033: kulaklık/Bluetooth dahil ses çıkışı için odak isteği — reddedilirse
        // (ör. bir telefon araması konuşma odağını tutuyorsa) hiç konuşmaya BAŞLAMAZ,
        // böylece sistemin diğer sesiyle çakışmaz.
        if (!requestAudioFocus()) return
        // QUEUE_FLUSH: bekleyen/eski her ifade burada atılır — kuyruk birikmez
        // (bkz. sınıf KDoc'u G033 notu).
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, cue.cueId)
    }

    override fun stop() {
        tts.stop()
        abandonAudioFocus()
    }

    fun shutdown() {
        abandonAudioFocus()
        tts.shutdown()
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true // AudioManager alınamadıysa engelleme, sessizce devam et.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusRequest = if (granted) request else null
        return granted
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun resolveVoiceStatus(): TurkishVoiceStatus = try {
        val availability = tts.isLanguageAvailable(TURKISH)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            TurkishVoiceStatus.Unavailable
        } else {
            val matchingVoice = tts.voices?.firstOrNull { it.locale == TURKISH }
            TurkishVoiceStatus.Available(requiresNetwork = matchingVoice?.isNetworkConnectionRequired ?: false)
        }
    } catch (e: Exception) {
        // NOT: `tts.voices` bazı OEM TTS motorlarında dokümante edilmemiş
        // şekilde hata fırlatabiliyor — bu kum havuzunda gerçek bir cihazda
        // hiç denenmedi, bu yüzden bilerek geniş bir yakalama var (dürüstlük
        // ilkesi: en kötü durumda "Unavailable" gösterilir, çökmez).
        TurkishVoiceStatus.Unavailable
    }

    companion object {
        private val TURKISH = Locale("tr", "TR")
    }
}
