package com.federalturk.gamletsol.core.pipeline

/**
 * Platformdan bağımsız ham görüntü işaretleyicisi. core-pipeline bu tipin
 * içeriğini hiç incelemez — yalnızca [TextRecognizer]'a geçirir. Gerçek tip
 * (ör. Android'de `ImageProxy`/`InputImage`) platform modülünde (feature-capture,
 * app-mobile) sağlanır. Döndürme/perspektif düzeltme ve ROI kırpma da bu
 * sınırın platform tarafında yapılır (bkz. yol haritası "Kare işleme hattı");
 * core-pipeline'a ulaşan görüntü zaten kırpılmış/düzeltilmiş kabul edilir.
 */
interface RawImage {
    /**
     * Bu görüntünün alttaki kaynağını serbest bırakır (ör. Android'de
     * `ImageProxy.close()`). [CaptionPipeline], OCR denemesi bittikten hemen
     * sonra — sonucu ne olursa olsun — bunu çağırır, böylece CameraX'in
     * `KEEP_ONLY_LATEST` arabelleği asla OCR'ın işlemesinden daha hızlı
     * dolmaz. Varsayılan boş gövde, görüntü kaynağı olmayan testler/sahte
     * uygulamalar için uygundur.
     */
    fun release() {}
}
