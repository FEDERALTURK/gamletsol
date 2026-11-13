package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TextRegion

/**
 * OCR sonuçlarının (metin bloğu kutuları) normalize [TextRegion] ile
 * karşılaştırılması için saf geometri yardımcıları.
 *
 * Neden ayrı bir sınıf (2026-11-13 hata düzeltmesi): ML Kit, metin bloklarının
 * kutularını görüntü DÖNDÜRÜLDÜKTEN SONRAKİ (dik/upright) koordinatlarda
 * verir; ama `InputImage.getWidth()/getHeight()` döndürülmemiş ham sensör
 * boyutunu döndürür. Uygulama dikey (portrait) kilitli olduğu için kamera
 * karesi hemen her zaman 90° döndürülmüş gelir — eski kod kutuları ham
 * boyutlara bölerek normalize ettiği için altyazı bandı (ekranın alt
 * %74–96'sı) fiilen ekranın %55–72'si gibi YANLIŞ bir şeride kayıyordu.
 * Sonuç: altyazı kadrajda biraz aşağıda/yukarıda durunca "bazen çeviriyor,
 * bazen hiç çevirmiyor" davranışı. Bu mantık Android'e bağımlı olmadığı için
 * burada, birim testle doğrulanabilir şekilde tutulur.
 */
object OcrGeometry {

    /** Bir metin bloğunun dik (upright) koordinatlardaki kutusu, piksel. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /**
     * Ham sensör boyutu ([rawWidth]×[rawHeight]) ve [rotationDegrees] verildiğinde,
     * OCR kutularının ait olduğu dik görüntünün (genişlik, yükseklik) çifti.
     * 90° ve 270°'de eksenler yer değiştirir.
     */
    fun uprightSize(rawWidth: Int, rawHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return if (normalized == 90 || normalized == 270) rawHeight to rawWidth else rawWidth to rawHeight
    }

    /** Kutunun merkezi, dik görüntüye göre normalize edildiğinde [region] içinde mi? */
    fun isCenterInside(box: Box, uprightWidth: Int, uprightHeight: Int, region: TextRegion): Boolean {
        if (uprightWidth <= 0 || uprightHeight <= 0) return false
        val x = box.centerX / uprightWidth
        val y = box.centerY / uprightHeight
        return x in region.left..region.right && y in region.top..region.bottom
    }

    /**
     * Blokları okuma sırasına (yukarıdan aşağı, aynı satırdaysa soldan sağa)
     * dizer. ML Kit blok sırasını garanti etmez; sıra kareden kareye değişirse
     * birleştirilmiş metin de değişir ve iki-kare kararlılık testi hiç
     * geçilemeyebilir. [lineTolerancePx] içinde kalan üst kenarlar aynı satır
     * sayılır.
     */
    fun <T> readingOrder(items: List<T>, lineTolerancePx: Int = 12, boxOf: (T) -> Box): List<T> {
        // Not: "üst kenarlar yakınsa soldan sağa" kuralını doğrudan bir
        // Comparator'a yazmak geçişli (transitive) olmaz ve sıralama
        // algoritmasını bozabilir. Bunun yerine: önce üst kenara göre sırala,
        // sonra satırlara grupla, her satırı kendi içinde soldan sağa diz.
        val byTop = items.sortedBy { boxOf(it).top }
        val lines = mutableListOf<MutableList<T>>()
        var lineTop = Int.MIN_VALUE
        for (item in byTop) {
            val top = boxOf(item).top
            if (lines.isEmpty() || top - lineTop > lineTolerancePx) {
                lines += mutableListOf(item)
                lineTop = top
            } else {
                lines.last() += item
            }
        }
        return lines.flatMap { line -> line.sortedBy { boxOf(it).left } }
    }
}
