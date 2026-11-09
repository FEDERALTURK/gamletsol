package com.federalturk.gamletsol.feature.capture

import androidx.camera.core.ImageAnalysis
import com.federalturk.gamletsol.core.model.FrameRef
import com.federalturk.gamletsol.core.model.TextRegion
import com.federalturk.gamletsol.core.pipeline.CapturedFrame
import com.federalturk.gamletsol.core.pipeline.FrameSource
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
 */
class CameraXFrameSource(
    override val sourceEpoch: Long,
    private val roiProvider: () -> TextRegion = { TextRegion.DEFAULT_SUBTITLE_BAND },
) : FrameSource {

    private val frameCounter = AtomicLong(0L)

    private val _frames = MutableSharedFlow<CapturedFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun frames(): Flow<CapturedFrame> = _frames.asSharedFlow()

    /**
     * [executor] üzerinde çalışacak bir [ImageAnalysis] use-case'i kurar.
     * `STRATEGY_KEEP_ONLY_LATEST`: analiz meşgulken gelen kareler atlanır —
     * yol haritasının "en fazla 1 aktif iş + 1 bekleyen kare" hedefi.
     * Döndürülen [ImageAnalysis], çağıran tarafından `ProcessCameraProvider`
     * ile lifecycle'a bağlanmalıdır.
     */
    fun buildAnalysisUseCase(executor: Executor): ImageAnalysis {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor) { imageProxy ->
            val ref = FrameRef(
                sourceEpoch = sourceEpoch,
                frameId = frameCounter.getAndIncrement(),
                capturedAtMillis = System.currentTimeMillis(),
            )
            val frame = CapturedFrame(ref = ref, image = ImageProxyRawImage(imageProxy), region = roiProvider())

            val delivered = _frames.tryEmit(frame)
            if (!delivered) {
                // Dinleyen yok ya da arabellek dolu: ImageProxy'yi hemen kapat,
                // yoksa CameraX yeni kare vermeyi durdurur.
                imageProxy.close()
            }
        }
        return analysis
    }
}
