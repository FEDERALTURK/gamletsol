package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGeometryTest {

    @Test
    fun `portrait camera frames swap width and height`() {
        assertEquals(480 to 640, OcrGeometry.uprightSize(rawWidth = 640, rawHeight = 480, rotationDegrees = 90))
        assertEquals(480 to 640, OcrGeometry.uprightSize(rawWidth = 640, rawHeight = 480, rotationDegrees = 270))
        assertEquals(640 to 480, OcrGeometry.uprightSize(rawWidth = 640, rawHeight = 480, rotationDegrees = 0))
        assertEquals(640 to 480, OcrGeometry.uprightSize(rawWidth = 640, rawHeight = 480, rotationDegrees = 180))
    }

    @Test
    fun `a subtitle at the bottom of a portrait frame is inside the default band`() {
        // Dik görüntü 480×640; altyazı alt %85 civarında (y ≈ 544).
        val (w, h) = OcrGeometry.uprightSize(rawWidth = 640, rawHeight = 480, rotationDegrees = 90)
        val subtitle = OcrGeometry.Box(left = 60, top = 530, right = 420, bottom = 558)

        assertTrue(OcrGeometry.isCenterInside(subtitle, w, h, TextRegion.DEFAULT_SUBTITLE_BAND))
        // Eski (hatalı) normalizasyon: ham 640×480'e bölmek bu satırı bandın dışına atıyordu.
        assertFalse(OcrGeometry.isCenterInside(subtitle, 640, 480, TextRegion.DEFAULT_SUBTITLE_BAND))
    }

    @Test
    fun `text in the middle of the frame is outside the subtitle band`() {
        val middle = OcrGeometry.Box(left = 60, top = 300, right = 420, bottom = 330)
        assertFalse(OcrGeometry.isCenterInside(middle, 480, 640, TextRegion.DEFAULT_SUBTITLE_BAND))
    }

    @Test
    fun `reading order is top to bottom then left to right regardless of input order`() {
        val secondLineRight = "D" to OcrGeometry.Box(300, 105, 400, 130)
        val firstLineRight = "B" to OcrGeometry.Box(250, 52, 400, 80)
        val secondLineLeft = "C" to OcrGeometry.Box(20, 100, 280, 130)
        val firstLineLeft = "A" to OcrGeometry.Box(20, 50, 230, 80)

        val ordered = OcrGeometry.readingOrder(listOf(secondLineRight, firstLineRight, secondLineLeft, firstLineLeft)) { it.second }

        assertEquals(listOf("A", "B", "C", "D"), ordered.map { it.first })
    }
}
