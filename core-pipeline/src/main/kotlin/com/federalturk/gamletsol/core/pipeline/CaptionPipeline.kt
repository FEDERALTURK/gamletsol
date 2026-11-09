package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.CaptureMode
import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.model.FrameRef
import kotlinx.coroutines.flow.collect

/**
 * Yol haritasının "Kare işleme hattı" bölümünü uygular:
 *
 * kare → (ROI/döndürme zaten [FrameSource] tarafında yapılmış) → OCR →
 * normalizasyon → 2-kare kararlılık/fuzzy eşleşme → cue oluşturma →
 * cache/çeviri → TTL kontrolü → sink/ses çıkışı.
 *
 * Değişim algılama (OCR'ı her karede çalıştırmamak için) kasıtlı olarak bu
 * sınıfta YOK — bu, görüntü düzeyinde bir optimizasyondur ve platforma özgü
 * (feature-capture, P2) olarak [FrameSource] seviyesinde yapılması
 * planlanmıştır; burada her gelen kare OCR'a verilir.
 *
 * Bu sınıf tek bir eşzamanlı çağırandan (frame kaynağının `collect`'i)
 * kullanılmak üzere tasarlandı; iç durumu thread-safe değildir.
 */
class CaptionPipeline(
    private val frameSource: FrameSource,
    private val textRecognizer: TextRecognizer,
    private val translator: Translator,
    private val sinks: List<SubtitleSink>,
    private val speechOutput: SpeechOutput? = null,
    private val sourceLanguageCode: String = "en",
    private val targetLanguageCode: String = "tr",
    private val stabilityThreshold: Float = DEFAULT_STABILITY_THRESHOLD,
    private val cueTtlMillis: Long = Cue.DEFAULT_SUBTITLE_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var pendingCandidate: PendingCandidate? = null
    private var activeCue: Cue? = null
    private var nextSeq = 0L
    private val translationCache = mutableMapOf<String, String>()

    /** [frameSource]'tan gelen kareleri tüketir; sonsuz döngü, iptal edilene kadar çalışır. */
    suspend fun run() {
        frameSource.frames().collect { frame -> onFrame(frame) }
    }

    /** Tek bir kareyi işler. `run()` yerine testlerde/manuel sürümde doğrudan çağrılabilir. */
    suspend fun onFrame(frame: CapturedFrame) {
        expireActiveCueIfNeeded()

        val recognized = try {
            textRecognizer.recognize(frame.image, frame.region)
        } finally {
            // OCR bitti bitmedi, görüntü kaynağını hemen serbest bırak —
            // KEEP_ONLY_LATEST arabelleğinin OCR'dan hızlı dolmasını önler.
            frame.image.release()
        }
        val normalized = recognized?.text?.let(TextNormalizer::normalize).orEmpty()

        if (normalized.isEmpty()) {
            // Ekranda okunabilir metin yok (ara sahne, diyalogsuz an vb.) —
            // bekleyen adayı at, ama aktif cue'nun TTL'i doğal yoldan bitsin.
            pendingCandidate = null
            return
        }

        val candidate = pendingCandidate
        if (candidate != null && isStableMatch(candidate.text, normalized)) {
            pendingCandidate = null
            emitStableText(normalized, frame)
        } else {
            pendingCandidate = PendingCandidate(text = normalized, frame = frame.ref)
        }
    }

    private suspend fun emitStableText(text: String, frame: CapturedFrame) {
        val current = activeCue
        val continuedCue = current?.takeIf { isStableMatch(it.originalText, text) }

        val translated = translationCache.getOrPut(text) {
            translator.translate(text, sourceLanguageCode, targetLanguageCode)
        }

        val cue = if (continuedCue != null) {
            // Aynı metin ekranda kalmaya devam ediyor: yalnız tazelik/TTL'i
            // yenile, aynı cueId/seq ile — gereksiz yeniden çizim/TTS yok.
            continuedCue.copy(frame = frame.ref, createdAtMillis = clock(), ttlMillis = cueTtlMillis)
        } else {
            Cue(
                cueId = "cue-${frame.ref.sourceEpoch}-${nextSeq}",
                revision = 0,
                seq = nextSeq++,
                frame = frame.ref,
                mode = CaptureMode.SUBTITLE,
                region = frame.region,
                originalText = text,
                translatedText = translated,
                createdAtMillis = clock(),
                ttlMillis = cueTtlMillis,
            )
        }

        activeCue = cue
        sinks.forEach { it.show(cue) }
        if (continuedCue == null) {
            speechOutput?.speak(cue)
        }
    }

    private fun expireActiveCueIfNeeded() {
        val cue = activeCue ?: return
        if (cue.isExpiredAt(clock())) {
            sinks.forEach { it.clear(cue.cueId) }
            activeCue = null
        }
    }

    private fun isStableMatch(a: String, b: String): Boolean = TextSimilarity.similarity(a, b) >= stabilityThreshold

    private data class PendingCandidate(val text: String, val frame: FrameRef)

    companion object {
        /** Deneyimsel başlangıç değeri; gerçek cihazda hiç ölçülmedi (bkz. kök README "Doğrulama durumu"). */
        const val DEFAULT_STABILITY_THRESHOLD = 0.82f
    }
}
