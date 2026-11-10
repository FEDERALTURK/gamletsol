package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinePackTranslatorTest {

    private fun pack(version: Int = 1, vararg lines: TranslationLine) = TranslationLinePack(
        packId = "test-pack",
        gameProfileId = "test-profile",
        version = version,
        lines = lines.toList(),
    )

    @Test
    fun `no active pack falls through to delegate unchanged (G010-G047 behavior preserved)`() = runTest {
        val delegate = FakeTranslator()
        val translator = LinePackTranslator(delegate) { null }

        val result = translator.translate("Anything at all", "en", "tr")

        assertEquals("TR:Anything at all", result)
        assertEquals(listOf("Anything at all"), delegate.calls)
    }

    @Test
    fun `a line present in the pack is returned without ever calling the delegate`() = runTest {
        val delegate = FakeTranslator()
        val activePack = pack(lines = arrayOf(TranslationLine(sourceText = "Hello there", translatedText = "Merhaba")))
        val translator = LinePackTranslator(delegate) { activePack }

        val result = translator.translate("Hello there", "en", "tr")

        assertEquals("Merhaba", result)
        assertTrue("Pakette bulunan satır için canlı çeviri hiç çağrılmamalı", delegate.calls.isEmpty())
    }

    @Test
    fun `a line missing from the pack falls through to the delegate`() = runTest {
        val delegate = FakeTranslator()
        val activePack = pack(lines = arrayOf(TranslationLine(sourceText = "Hello there", translatedText = "Merhaba")))
        val translator = LinePackTranslator(delegate) { activePack }

        val result = translator.translate("Totally different line", "en", "tr")

        assertEquals("TR:Totally different line", result)
        assertEquals(listOf("Totally different line"), delegate.calls)
    }

    @Test
    fun `switching to a new pack version is picked up without stale lookups`() = runTest {
        val delegate = FakeTranslator()
        var activePack = pack(
            version = 1,
            lines = arrayOf(TranslationLine(sourceText = "Hello there", translatedText = "Merhaba (v1)")),
        )
        val translator = LinePackTranslator(delegate) { activePack }

        assertEquals("Merhaba (v1)", translator.translate("Hello there", "en", "tr"))

        // Editoryal düzeltme: paket yeni sürüme yükseltildi (G073/G075'in
        // beklediği senaryo — bkz. TranslationLinePack.versionTag KDoc'u).
        activePack = pack(
            version = 2,
            lines = arrayOf(TranslationLine(sourceText = "Hello there", translatedText = "Merhaba (v2, düzeltildi)")),
        )

        assertEquals("Merhaba (v2, düzeltildi)", translator.translate("Hello there", "en", "tr"))
    }
}
