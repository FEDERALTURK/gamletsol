package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.CaptureMode
import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.model.FrameRef
import kotlin.coroutines.cancellation.CancellationException
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
 *
 * 2026-11-13 hata düzeltmeleri ("yazılar bazen çevriliyor, bazen hiç
 * çevrilmiyor" şikâyeti üzerine):
 * - Çeviri hatası (ör. ML Kit modeli henüz inmemiş, geçici hata) artık
 *   hattı ÖLDÜRMEZ: o kare atlanır, sonuç cache'e yazılmaz, sonraki karede
 *   yeniden denenir. Önceden tek bir istisna `run()`'ı sonlandırıyordu.
 * - Ekranda kalan bir cue'nun metni değişirse (daktilo efektiyle uzayan
 *   satır, ilk okumadaki OCR hatasının düzelmesi) artık YENİ metin çevrilip
 *   gösterilir ([Cue.revision] artar). Önceden ilk okumanın (çoğu zaman
 *   yarım/hatalı) çevirisi ekranda takılı kalıyordu.
 * - Tek bir boş/okunamayan kare (bulanıklık, odak arama) bekleyen adayı
 *   artık hemen silmez; [maxConsecutiveEmptyFrames] kadar ardışık boş kareye
 *   tolerans var. Önceden aralıklı bulanıklık kararlılık testini hiç
 *   geçirmeyebiliyordu.
 * - Çeviri cache'i sınırlı boyutlu (LRU) — uzun oturumlarda bellek
 *   sınırsız büyümez.
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
    // G036: yük artınca (OCR+normalizasyon süresi uzadıkça) kare atlama
    // oranını kademeli artırır — bkz. AdaptiveFrameThrottle KDoc'u.
    private val frameThrottle: AdaptiveFrameThrottle = AdaptiveFrameThrottle(),
    // G019/G073: çeviri cache anahtarına eklenen bir sürüm etiketi — oyun
    // profili, terim sözlüğü veya (X6) aktif çeviri paketi değiştiğinde bu
    // etiket de değişmeli, aksi halde eski bir sürümden gelen çeviri yeni
    // sürüm aktifken de cache'te takılı kalabilir. Varsayılan boş dize: G019
    // henüz tam uygulanmadığı için şu an yalnız X6 paket sürümünü kapsar;
    // profil/sözlük sürümünü de kapsaması G019'un kendi görevidir (bu sınıf
    // bunu iddia etmez — dürüstlük ilkesi).
    private val cacheVersionTag: () -> String = { "" },
    private val maxConsecutiveEmptyFrames: Int = DEFAULT_MAX_CONSECUTIVE_EMPTY_FRAMES,
    private val translationCacheSize: Int = DEFAULT_TRANSLATION_CACHE_SIZE,
    // Çeviri hatalarını gözlemlemek için (ör. UI'da "çeviri modeli hazır
    // değil" göstermek). Hattın kendisi hatayı yutar ve devam eder.
    private val onTranslationError: (Throwable) -> Unit = {},
    // 2026-11-14: Teşhis logları — "çeviri yapmıyor" şikâyetlerinde sorunun
    // hangi aşamada olduğunu (OCR boş mu, kararlılık geçmiyor mu, çeviri
    // hatası mı) belirlemek için. Android tarafı bunu Log.d'ye bağlar.
    private val logger: (String) -> Unit = {},
) {
    private var pendingCandidate: PendingCandidate? = null
    private var consecutiveEmptyFrames = 0
    private var activeCue: Cue? = null

    // Aktif cue'nun çevirisi üretilirken geçerli olan cacheVersionTag. Etiket
    // değişirse (ör. kullanıcı çeviri kütüphanesine düzeltme ekledi) ekranda
    // DURAN aynı satır da yeniden çevrilir — yoksa düzeltme ancak satır
    // değişince görünürdü.
    private var activeCueTag: String? = null
    private var nextSeq = 0L
    private val translationCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > translationCacheSize
    }

    /** [frameSource]'tan gelen kareleri tüketir; sonsuz döngü, iptal edilene kadar çalışır. */
    suspend fun run() {
        frameSource.frames().collect { frame -> onFrame(frame) }
    }

    /** Tek bir kareyi işler. `run()` yerine testlerde/manuel sürümde doğrudan çağrılabilir. */
    suspend fun onFrame(frame: CapturedFrame) {
        expireActiveCueIfNeeded()

        if (!frameThrottle.shouldProcess()) {
            // G036: cihaz yük altında/ısınmışsa bu kare OCR'a HİÇ verilmez —
            // doğrudan serbest bırakılır (bkz. AdaptiveFrameThrottle KDoc'u).
            frame.image.release()
            return
        }

        val startedAtMillis = clock()
        val recognized = try {
            textRecognizer.recognize(frame.image, frame.region)
        } finally {
            // OCR bitti bitmedi, görüntü kaynağını hemen serbest bırak —
            // KEEP_ONLY_LATEST arabelleğinin OCR'dan hızlı dolmasını önler.
            frame.image.release()
        }
        frameThrottle.recordProcessingDuration(clock() - startedAtMillis)
        val normalized = recognized?.text?.let(TextNormalizer::normalize).orEmpty()

        if (normalized.isEmpty()) {
            consecutiveEmptyFrames++
            if (consecutiveEmptyFrames == 1) {
                logger("OCR boş kare (bulanık/altyazısız sahne)")
            }
            if (consecutiveEmptyFrames > maxConsecutiveEmptyFrames) {
                pendingCandidate = null
            }
            return
        }
        consecutiveEmptyFrames = 0

        val candidate = pendingCandidate
        if (candidate != null && isStableMatch(candidate.text, normalized)) {
            logger("Kararlı metin: \"${normalized.take(60)}\"")
            pendingCandidate = null
            emitStableText(normalized, frame)
        } else {
            if (candidate != null) {
                val sim = TextSimilarity.similarity(candidate.text, normalized)
                logger("Kararsız: sim=%.2f eşik=%.2f önceki=\"${candidate.text.take(40)}\" şimdi=\"${normalized.take(40)}\"".format(sim, stabilityThreshold))
            } else {
                logger("İlk aday: \"${normalized.take(60)}\"")
            }
            pendingCandidate = PendingCandidate(text = normalized, frame = frame.ref)
        }
    }

    private suspend fun emitStableText(text: String, frame: CapturedFrame) {
        val current = activeCue
        val continuedCue = current?.takeIf { isStableMatch(it.originalText, text) }

        val tag = cacheVersionTag()

        // Aynı cue, metin birebir aynı ve kütüphane/sözlük değişmedi: çeviriye
        // gerek yok, yalnız TTL'i yenile.
        if (continuedCue != null &&
            continuedCue.originalText == text &&
            !continuedCue.translatedText.isNullOrBlank() &&
            activeCueTag == tag
        ) {
            val refreshed = continuedCue.copy(frame = frame.ref, createdAtMillis = clock(), ttlMillis = cueTtlMillis)
            activeCue = refreshed
            sinks.forEach { it.show(refreshed) }
            return
        }

        val translated = translateCached(text, tag) ?: return

        val cue = if (continuedCue != null) {
            // Aynı altyazı ekranda kalıyor ama metni değişti (daktilo efekti,
            // OCR düzeltmesi): aynı cueId, yeni revizyon, YENİ çeviri.
            // Yeniden seslendirilmez (aynı satırın tekrar tekrar okunmasını önler).
            continuedCue.copy(
                revision = continuedCue.revision + 1,
                seq = nextSeq++,
                frame = frame.ref,
                originalText = text,
                translatedText = translated,
                createdAtMillis = clock(),
                ttlMillis = cueTtlMillis,
            )
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
        activeCueTag = tag
        sinks.forEach { it.show(cue) }
        if (continuedCue == null) {
            speechOutput?.speak(cue)
        }
    }

    /** Başarısız çeviri `null` döner ve cache'e YAZILMAZ — sonraki stabil karede tekrar denenir. */
    private suspend fun translateCached(text: String, tag: String): String? {
        val cacheKey = "$text|$tag"
        translationCache[cacheKey]?.let { return it }
        val translated = try {
            translator.translate(text, sourceLanguageCode, targetLanguageCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger("Çeviri hatası: ${e::class.simpleName}: ${e.message?.take(80)}")
            onTranslationError(e)
            return null
        }
        if (translated.isBlank()) {
            logger("Çeviri boş döndü: \"${text.take(60)}\"")
            return null
        }
        translationCache[cacheKey] = translated
        return translated
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
        /**
         * 2026-11-14: 0.82 → 0.70 düşürüldü. Kamera ile TV ekranına doğrultmada
         * OCR çıktısı kareler arası çok değişken oluyor (titreme, odak kayması,
         * ışık yansıması): %82 eşik, altyazı ekrandayken bile iki ardışık karede
         * "kararlı" kabul edilmesini engelliyordu — çeviri HİÇ tetiklenmiyordu.
         * %70 hâlâ rastgele gürültüyü (tamamen farklı sahne) süzer, ama gerçek
         * altyazıda küçük OCR farklarını tolere eder.
         * Varsayım, ölçüm değil — gerçek cihaz ölçümü yapılmadı.
         */
        const val DEFAULT_STABILITY_THRESHOLD = 0.70f

        /**
         * Bekleyen aday silinmeden önce tolere edilen ardışık boş kare sayısı.
         * 2026-11-14: 2→4 artırıldı. Kamerayla TV ekranına doğrultmada ara sıra
         * odak kaybı / ışık yansıması / titreme 2-3 ardışık boş kare üretebilir;
         * eski değer bunlarda adayı erken siliyordu. Varsayım, ölçüm değil.
         */
        const val DEFAULT_MAX_CONSECUTIVE_EMPTY_FRAMES = 4

        /** Oturum içi çeviri cache'inin üst sınırı (girdi sayısı). */
        const val DEFAULT_TRANSLATION_CACHE_SIZE = 512
    }
}
