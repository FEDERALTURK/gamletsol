package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.FrameRef
import com.federalturk.gamletsol.core.model.TextRegion
import kotlinx.coroutines.flow.Flow

/**
 * Yakalanmış, ROI'ye kırpılmış ve döndürme/perspektif düzeltmesi tamamlanmış
 * tek bir kare. [region], bu görüntünün kaynak karedeki normalize konumudur
 * (overlay'i geri eşlemek ve OCR maskesi uygulamak için — bkz. yol haritası
 * "Kamera→aynı TV geri besleme sorunu").
 */
data class CapturedFrame(
    val ref: FrameRef,
    val image: RawImage,
    val region: TextRegion,
)

/**
 * Bir yakalama kaynağı — kamera (`CameraSource`, feature-capture) veya
 * ileride konsol/ekran projeksiyonu (`ProjectionSource`). Yol haritasındaki
 * "Yakalama kaynakları (iki ayrı yol)" bölümüne karşılık gelir.
 */
interface FrameSource {
    /**
     * Bu kaynağın mevcut oturum kimliği. Kaynak yeniden başlatıldığında
     * (kamera kapanıp açıldığında, yeniden bağlanıldığında) artması beklenir;
     * böylece eski oturumdan kalan kareler asla yeni oturumla karışmaz.
     */
    val sourceEpoch: Long

    /** Yakalanan kareler akışı. Üretici en fazla bir aktif + bir bekleyen kare
     * tutmalı (`KEEP_ONLY_LATEST` semantiği) — geriye basınç biriktirmemeli. */
    fun frames(): Flow<CapturedFrame>
}
