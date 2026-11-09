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
}
