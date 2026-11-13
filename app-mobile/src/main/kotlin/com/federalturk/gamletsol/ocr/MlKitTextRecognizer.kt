package com.federalturk.gamletsol.ocr

import androidx.camera.core.ExperimentalGetImage
import com.federalturk.gamletsol.core.model.TextRegion
import com.federalturk.gamletsol.core.pipeline.OcrGeometry
import com.federalturk.gamletsol.core.pipeline.RawImage
import com.federalturk.gamletsol.core.pipeline.RecognizedText
import com.federalturk.gamletsol.core.pipeline.TextRecognizer
import com.federalturk.gamletsol.feature.capture.ImageProxyRawImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit Latin Text Recognition (cihaz-üstü, ücretsiz, internet gerektirmez)
 * tabanlı [TextRecognizer] uygulaması.
 *
 * NOT (dürüstlük ilkesi / performans, P2'ye bırakılan iş): Bu ilk sürüm
 * gerçek bir önceden kırpılmış ROI görüntüsü değil, TAM kareyi ML Kit'e
 * verir, sonra yalnızca [region] içine düşen metin bloklarını filtreler. Bu
 * basit ve doğru sonuç verir ama yol haritasındaki "ROI kırpma → OCR"
 * sırasından daha pahalıdır (her karede tüm ekran taranır). Gerçek cihazda
 * ölçüm sonrası gerekirse bitmap düzeyinde önceden kırpmaya geçilebilir.
 *
 * 2026-11-13 hata düzeltmesi: ML Kit blok kutularını DİK (döndürülmüş)
 * görüntü koordinatlarında verir, `InputImage.width/height` ise ham sensör
 * boyutudur. Uygulama dikey kilitli olduğu için kare neredeyse her zaman 90°
 * döner; eski kod kutuları ham boyuta böldüğü için altyazı bandı yanlış
 * şeride kayıyor, altyazı kadrajda biraz aşağıdaysa hiç okunmuyordu. Artık
 * [OcrGeometry.uprightSize] ile doğru boyut kullanılıyor. Ayrıca bloklar
 * okuma sırasına diziliyor (ML Kit sırayı garanti etmez; sıra kareden kareye
 * değişirse iki-kare kararlılık testi geçilemiyordu).
 */
class MlKitTextRecognizer : TextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // NOT: `ImageProxy.getImage()` CameraX'te deneysel/opt-in bir API
    // (`@ExperimentalGetImage`) — gerçek bir CI lint çalıştırmasında bu
    // opt-in eksikliği `UnsafeOptInUsageError` olarak YAKALANDI ve
    // düzeltildi. Kullanımı CameraX'in kendi dokümantasyonundaki önerilen
    // desenle aynı: `ImageProxy` kapatılana kadar (bkz.
    // `ImageProxyRawImage.release()`) `Image` nesnesi geçerlidir.
    @ExperimentalGetImage
    override suspend fun recognize(image: RawImage, region: TextRegion): RecognizedText? {
        val proxyImage = image as? ImageProxyRawImage
            ?: error("MlKitTextRecognizer yalnızca ImageProxyRawImage ile çalışır, alınan: ${image::class}")

        val mediaImage = proxyImage.imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, proxyImage.imageProxy.imageInfo.rotationDegrees)

        val visionText = try {
            recognizer.awaitResult(inputImage)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Tek karelik OCR hatası (bulanıklık vb.) — boş kare say, hat devam eder.
            return null
        }

        val (uprightWidth, uprightHeight) = OcrGeometry.uprightSize(
            rawWidth = inputImage.width,
            rawHeight = inputImage.height,
            rotationDegrees = inputImage.rotationDegrees,
        )
        if (uprightWidth <= 0 || uprightHeight <= 0) return null

        val blocksInRegion = visionText.textBlocks.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            val box = OcrGeometry.Box(rect.left, rect.top, rect.right, rect.bottom)
            if (OcrGeometry.isCenterInside(box, uprightWidth, uprightHeight, region)) block to box else null
        }

        val regionText = OcrGeometry.readingOrder(blocksInRegion) { it.second }
            .joinToString(separator = "\n") { it.first.text }
            .trim()

        if (regionText.isEmpty()) return null
        return RecognizedText(text = regionText)
    }
}

private suspend fun com.google.mlkit.vision.text.TextRecognizer.awaitResult(image: InputImage): Text =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
