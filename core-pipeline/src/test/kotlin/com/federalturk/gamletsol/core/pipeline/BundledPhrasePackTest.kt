package com.federalturk.gamletsol.core.pipeline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * app-mobile'a gömülü genel oyun ifadeleri kütüphanesinin (assets) bu
 * çözücüyle sorunsuz okunduğunu ve tipik OCR çıktılarını doğru eşlediğini
 * doğrular. Dosya düzenlenirken bozulursa (eksik sekme vb.) CI'da yakalanır.
 */
class BundledPhrasePackTest {

    private val file = File("../app-mobile/src/main/assets/library/genel-oyun-ifadeleri.tsv")

    private fun index(): LinePackIndex {
        val result = LinePackTsvCodec.decode(file.readText(Charsets.UTF_8))
        assertEquals("Gömülü kütüphanede bozuk satır olmamalı", 0, result.skippedLines)
        assertEquals("genel-oyun-ifadeleri", result.pack.packId)
        assertTrue(result.pack.lines.size > 100)
        return LinePackIndex(result.pack, LinePackIndex.PREFIX_MATCH_DISABLED)
    }

    @Test
    fun `bundled pack parses and matches typical OCR readings`() {
        val index = index()

        assertEquals("Yükleniyor", index.lookup("LOADING...")?.translatedText)
        assertEquals("Kontrol noktasına ulaşıldı", index.lookup("Checkpoint Reached")?.translatedText)
        assertEquals("Devam etmek için X tuşuna basın", index.lookup("Press X to continue")?.translatedText)
        assertEquals("Etkileşim için E tuşunu basılı tutun", index.lookup("Hold E to interact")?.translatedText)
        assertEquals("Eğil!", index.lookup("Get down!")?.translatedText)
        assertNull("Kütüphanede olmayan serbest diyalog canlı çeviriye kalmalı", index.lookup("The bridge collapsed last winter."))
    }

    @Test
    fun `bundled pack has no duplicate source lines`() {
        val pack = LinePackTsvCodec.decode(file.readText(Charsets.UTF_8)).pack
        val duplicates = pack.lines.groupBy { LinePackIndex.matchKey(it.sourceText) }.filterValues { it.size > 1 }.keys
        assertTrue("Yinelenen kaynak satırlar: $duplicates", duplicates.isEmpty())
    }
}
