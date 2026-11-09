package com.federalturk.gamletsol.feature.capture

import androidx.camera.core.ImageProxy
import com.federalturk.gamletsol.core.pipeline.RawImage

/**
 * CameraX'in verdiği [ImageProxy]'yi core-pipeline'ın platformdan bağımsız
 * [RawImage] sınırına taşır. app-mobile'daki ML Kit tabanlı
 * `TextRecognizer` uygulaması bu sınıfa cast ederek alttaki [imageProxy]'ye
 * (ve `mediaImage`/`imageInfo.rotationDegrees`'e) erişir.
 */
class ImageProxyRawImage(val imageProxy: ImageProxy) : RawImage {
    override fun release() {
        imageProxy.close()
    }
}
