package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.GameGenre
import com.federalturk.gamletsol.core.model.GameProfile
import com.federalturk.gamletsol.core.model.TermOverride
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * G031 doğrulama kriteri: "Sayılar, butonlar, HP/Armor ve isimleri
 * örneklerde korunduğunu kontrol et." — bu dosya tam olarak bunu test eder,
 * [FakeTranslator] ile (gerçek ML Kit değil, bkz. sınıf KDoc'u).
 */
class TermPreservingTranslatorTest {

    private val profile = GameProfile(
        profileId = "test-profile",
        displayName = "Test",
        genre = GameGenre.RPG,
        terms = listOf(
            TermOverride(sourceTerm = "HP"),
            TermOverride(sourceTerm = "Armor"),
            TermOverride(sourceTerm = "Geralt"),
        ),
    )

    @Test
    fun `numbers are preserved even with no active profile`() = runTest {
        val delegate = FakeTranslator()
        val translator = TermPreservingTranslator(delegate) { null }

        val result = translator.translate("You found 42 gold", "en", "tr")

        assertEquals("TR:You found 42 gold", result)
        // Maskelenmiş metin delegate'e gitmeli, "42" hiçbir zaman "TR:" ile
        // birlikte çevrilmiş görünmemeli (yani delegate asla "42" görmedi).
        assertFalse(delegate.calls.any { it.contains("42") })
    }

    @Test
    fun `button and stat abbreviations from the active profile are preserved`() = runTest {
        val delegate = FakeTranslator()
        val translator = TermPreservingTranslator(delegate) { profile }

        val result = translator.translate("HP is low, check your Armor", "en", "tr")

        assertEquals("TR:HP is low, check your Armor", result)
    }

    @Test
    fun `names in the active profile are preserved verbatim`() = runTest {
        val delegate = FakeTranslator()
        val translator = TermPreservingTranslator(delegate) { profile }

        val result = translator.translate("Geralt draws his sword", "en", "tr")

        assertEquals("TR:Geralt draws his sword", result)
    }

    @Test
    fun `matching is case-insensitive but does not match substrings inside other words`() = runTest {
        val delegate = FakeTranslator()
        val translator = TermPreservingTranslator(delegate) { profile }

        // "hp" küçük harfle de korunmalı; "Armored" kelimesinin İÇİNDEKİ
        // "Armor" ise YANLIŞLIKLA eşleşip kelimeyi bölmemeli.
        val result = translator.translate("hp critical, Armored truck arrives", "en", "tr")

        assertEquals("TR:hp critical, Armored truck arrives", result)
    }

    @Test
    fun `text with nothing to protect is translated unchanged by the delegate`() = runTest {
        val delegate = FakeTranslator()
        val translator = TermPreservingTranslator(delegate) { profile }

        val result = translator.translate("The door is locked", "en", "tr")

        assertEquals("TR:The door is locked", result)
        assertEquals(listOf("The door is locked"), delegate.calls)
    }

    private class ScriptedTranslator(private val transform: (String) -> String) : Translator {
        val calls = mutableListOf<String>()
        override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
            calls += text
            return transform(text)
        }
    }

    @Test
    fun `placeholders mangled with spaces by the engine are still restored`() = runTest {
        val delegate = ScriptedTranslator { it.replace("§0§", "§ 0 §") }
        val translator = TermPreservingTranslator(delegate) { null }

        val result = translator.translate("You have 25 arrows", "en", "tr")

        assertEquals("You have 25 arrows", result)
        assertEquals(1, delegate.calls.size)
    }

    @Test
    fun `a dropped placeholder falls back to translating the unmasked text instead of losing the number`() = runTest {
        val delegate = ScriptedTranslator { it.replace("§0§", "") }
        val translator = TermPreservingTranslator(delegate) { null }

        val result = translator.translate("You have 25 arrows", "en", "tr")

        assertEquals("You have 25 arrows", result)
        assertEquals(listOf("You have §0§ arrows", "You have 25 arrows"), delegate.calls)
    }
}
