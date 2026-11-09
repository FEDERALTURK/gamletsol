package com.federalturk.gamletsol.ocr

import androidx.camera.core.ExperimentalGetImage
import com.federalturk.gamletsol.core.model.TextRegion
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
        } catch (t: Throwable) {
            return null
        }

        val imageWidth = inputImage.width.toFloat()
        val imageHeight = inputImage.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return null

        val regionText = visionText.textBlocks
            .asSequence()
            .filter { block -> block.boundingBox?.let { box -> isInsideRegion(box, imageWidth, imageHeight, region) } == true }
            .joinToString(separator = "\n") { it.text }
            .trim()

        if (regionText.isEmpty()) return null
        return RecognizedText(text = regionText)
    }

    private fun isInsideRegion(
        box: android.graphics.Rect,
        imageWidth: Float,
        imageHeight: Float,
        region: TextRegion,
    ): Boolean {
        val centerXNorm = (box.left + box.right) / 2f / imageWidth
        val centerYNorm = (box.top + box.bottom) / 2f / imageHeight
        return centerXNorm in region.left..region.right && centerYNorm in region.top..region.bottom
    }
}

private suspend fun com.google.mlkit.vision.text.TextRecognizer.awaitResult(image: InputImage): Text =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
