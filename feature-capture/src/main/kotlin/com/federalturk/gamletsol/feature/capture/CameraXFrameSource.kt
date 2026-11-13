package com.federalturk.gamletsol.feature.capture

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.federalturk.gamletsol.core.model.FrameRef
import com.federalturk.gamletsol.core.model.TextRegion
import com.federalturk.gamletsol.core.pipeline.CapturedFrame
import com.federalturk.gamletsol.core.pipeline.FrameSource
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Yol haritasının "Yakalama kaynakları" bölümündeki `CameraSource` — kamerayı
 * ekrana doğrultma yoluyla cihaz-bağımsız yakalama. Kamera seçimi, önizleme
 * (Preview) ile birlikte bağlama ve izin akışı çağırana (app-mobile) aittir;
 * bu sınıf yalnızca [ImageAnalysis] use-case'ini kurar ve kareleri
 * [FrameSource] arayüzü üzerinden akışa verir.
 *
 * NOT (dürüstlük ilkesi): Yol haritasındaki "döndürme/perspektif düzeltme"
 * adımı burada henüz YOK — [ImageProxy.imageInfo.rotationDegrees] olduğu gibi
 * aktarılır, gerçek düzeltme OCR adımına (app-mobile'daki ML Kit
 * `TextRecognizer` uygulaması, `InputImage.fromMediaImage` ile rotation'ı
 * zaten dikkate alır) bırakılmıştır. Ayrıca "değişim algılama" (bir sonraki
 * kareyi öncekiyle karşılaştırıp aynıysa OCR'ı atlama) de P2 kapsamında
 * burada henüz uygulanmadı — şu an her kare analiz edilir.
 *
 * 2026-11-13 hata düzeltmesi ("bazen hiç çevirmiyor"): Eski sürüm kareleri
 * bir `MutableSharedFlow`'a `tryEmit` ediyordu. Hat henüz dinlemeye
 * başlamamışken (ör. açılışta çeviri modeli kontrol edilirken) gelen kare,
 * SharedFlow'un abonesiz durumda değeri sessizce düşürmesi yüzünden HİÇ
 * kapatılmıyordu; `KEEP_ONLY_LATEST` analizörü önceki kare kapanmadan yeni
 * kare vermediği için kamera analizi o oturum boyunca tamamen donuyordu
 * (önizleme çalışmaya devam ettiği için kullanıcı fark etmiyordu). Açılıştaki
 * zamanlamaya göre bazen olup bazen olmaması, şikâyetteki "bazen" ile
 * örtüşür. Artık kapasitesi 1 olan bir [Channel] kullanılıyor: dinleyici
 * yoksa kare bekletilir, taşmada atılan kare `onUndeliveredElement` ile
 * mutlaka kapatılır.
 *
 * Ayrıca analiz çözünürlüğü varsayılan 640×480 yerine ~1280×960 (4:3,
 * önizlemeyle aynı oran) istenir: TV'ye uzaktan doğrultulan kamerada altyazı
 * harfleri 640×480'de ML Kit'in güvenilir okuyabileceğinden küçük kalıyordu.
 * Bu bir varsayımdır, gerçek cihazda ölçülmedi; OCR süresi artarsa
 * `AdaptiveFrameThrottle` kare atlamayı artırır.
 */
class CameraXFrameSource(
    override val sourceEpoch: Long,
    private val roiProvider: () -> TextRegion = { TextRegion.DEFAULT_SUBTITLE_BAND },
) : FrameSource {

    private val frameCounter = AtomicLong(0L)

    private val _frames = Channel<CapturedFrame>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        // Taşma nedeniyle atılan (veya kanal kapanırken teslim edilemeyen)
        // kare mutlaka kapatılır — aksi halde CameraX yeni kare vermeyi keser.
        onUndeliveredElement = { frame -> frame.image.release() },
    )

    /** Tek tüketici (CaptionPipeline) içindir. */
    override fun frames(): Flow<CapturedFrame> = _frames.receiveAsFlow()

    /**
     * [executor] üzerinde çalışacak bir [ImageAnalysis] use-case'i kurar.
     * `STRATEGY_KEEP_ONLY_LATEST`: analiz meşgulken gelen kareler atlanır —
     * yol haritasının "en fazla 1 aktif iş + 1 bekleyen kare" hedefi.
     * Döndürülen [ImageAnalysis], çağıran tarafından `ProcessCameraProvider`
     * ile lifecycle'a bağlanmalıdır.
     */
    fun buildAnalysisUseCase(executor: Executor): ImageAnalysis {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()

        analysis.setAnalyzer(executor) { imageProxy ->
            val ref = FrameRef(
                sourceEpoch = sourceEpoch,
                frameId = frameCounter.getAndIncrement(),
                capturedAtMillis = System.currentTimeMillis(),
            )
            val frame = CapturedFrame(ref = ref, image = ImageProxyRawImage(imageProxy), region = roiProvider())

            val result = _frames.trySend(frame)
            if (!result.isSuccess) {
                // Kanal kapalı (oturum bitti): ImageProxy'yi hemen kapat,
                // yoksa CameraX yeni kare vermeyi durdurur.
                imageProxy.close()
            }
        }
        return analysis
    }

    private companion object {
        const val ANALYSIS_WIDTH = 1280
        const val ANALYSIS_HEIGHT = 960
    }
}
