package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-13 çeviri kütüphanesi: eşleşme toleransı, kalıplar, TSV biçimi, kişisel düzeltmeler. */
class TranslationLibraryTest {

    private fun pack(vararg lines: Pair<String, String>) = TranslationLinePack(
        packId = "test",
        gameProfileId = "genel",
        version = 1,
        lines = lines.map { TranslationLine(it.first, it.second) },
    )

    @Test
    fun `exact match ignores case, ellipsis style and trailing punctuation`() {
        val index = LinePackIndex(pack("Loading" to "Yükleniyor"), LinePackIndex.PREFIX_MATCH_DISABLED)

        assertEquals("Yükleniyor", index.lookup("LOADING...")?.translatedText)
        assertEquals("Yükleniyor", index.lookup("Loading…")?.translatedText)
        assertEquals("Yükleniyor", index.lookup("  loading  ")?.translatedText)
    }

    @Test
    fun `typographic apostrophes match straight ones`() {
        val index = LinePackIndex(pack("Let's go" to "Hadi gidelim"), LinePackIndex.PREFIX_MATCH_DISABLED)

        assertEquals("Hadi gidelim", index.lookup("Let’s go!")?.translatedText)
    }

    @Test
    fun `button prompt pattern fills the captured button name into the translation`() {
        val index = LinePackIndex(
            pack("Press {0} to continue" to "Devam etmek için {0} tuşuna basın"),
            LinePackIndex.PREFIX_MATCH_DISABLED,
        )

        assertEquals("Devam etmek için X tuşuna basın", index.lookup("Press X to continue")?.translatedText)
        assertEquals("Devam etmek için Square tuşuna basın", index.lookup("PRESS SQUARE TO CONTINUE")?.translatedText)
    }

    @Test
    fun `an exact line wins over a pattern that would also match it`() {
        val index = LinePackIndex(
            pack(
                "Press {0} to continue" to "Devam etmek için {0} tuşuna basın",
                "Press any button to continue" to "Devam etmek için herhangi bir tuşa basın",
            ),
            LinePackIndex.PREFIX_MATCH_DISABLED,
        )

        assertEquals("Devam etmek için herhangi bir tuşa basın", index.lookup("Press any button to continue")?.translatedText)
    }

    @Test
    fun `a pattern placeholder does not swallow a long free-text sentence`() {
        val index = LinePackIndex(
            pack("Press {0} to continue" to "Devam etmek için {0} tuşuna basın"),
            LinePackIndex.PREFIX_MATCH_DISABLED,
        )

        assertNull(index.lookup("Press the old rusty lever near the broken gate to continue"))
    }

    @Test
    fun `with prefix matching disabled a half-typed line does not hit an unrelated stock sentence`() {
        val index = LinePackIndex(pack("Let's get out of here" to "Hadi buradan çıkalım"), LinePackIndex.PREFIX_MATCH_DISABLED)

        assertNull(index.lookup("Let's get the"))
    }

    @Test
    fun `tsv round trip keeps metadata, escapes and optional fields`() {
        val original = TranslationLinePack(
            packId = "kisisel",
            gameProfileId = "genel",
            version = 7,
            lines = listOf(
                TranslationLine("Tab\there", "Sekme\tburada"),
                TranslationLine("Two\nlines", "İki\nsatır", speaker = "Joel"),
                TranslationLine("Back\\slash", "Ters\\eğik", speaker = null, style = "italic"),
            ),
        )

        val decoded = LinePackTsvCodec.decode(LinePackTsvCodec.encode(original))

        assertEquals(original, decoded.pack)
        assertEquals(0, decoded.skippedLines)
    }

    @Test
    fun `tsv decoder skips broken lines instead of failing the whole library`() {
        val text = "#gamletsol-linepack 1\n#version=2\nLoading\tYükleniyor\nonly-source-no-tab\n\nSaving\tKaydediliyor\n"

        val result = LinePackTsvCodec.decode(text, defaultPackId = "x")

        assertEquals(2, result.pack.lines.size)
        assertEquals(1, result.skippedLines)
        assertEquals(2, result.pack.version)
        assertEquals("x", result.pack.packId)
    }

    @Test
    fun `user library put replaces an existing correction for the same line and bumps the version`() {
        val library = UserTranslationLibrary()
        val v1 = library.snapshot().version

        library.put("Get down!", "Yere yat!")
        library.put("GET DOWN", "Eğil!")

        assertEquals(1, library.size)
        assertEquals("Eğil!", library.find("get down.")?.translatedText)
        assertEquals(v1 + 2, library.snapshot().version)
    }

    @Test
    fun `user library remove and clear`() {
        val library = UserTranslationLibrary()
        library.put("Hello there", "Merhaba")
        library.put("Follow me", "Beni takip et")

        assertTrue(library.remove("hello there!"))
        assertFalse(library.remove("not in library"))
        assertEquals(1, library.size)

        library.clear()
        assertEquals(0, library.size)
    }

    @Test
    fun `layered library - personal correction beats the stock phrase which beats live translation`() = runTest {
        val live = FakeTranslator()
        val stock = pack("Get down" to "Eğil!")
        val personal = UserTranslationLibrary()
        val translator = LinePackTranslator(
            delegate = LinePackTranslator(live, LinePackIndex.PREFIX_MATCH_DISABLED) { stock },
            minPrefixLength = LinePackIndex.PREFIX_MATCH_DISABLED,
        ) { personal.snapshot() }

        assertEquals("Eğil!", translator.translate("Get down!", "en", "tr"))
        assertEquals("TR:Something else", translator.translate("Something else", "en", "tr"))

        personal.put("Get down", "Yere yat!")
        assertEquals("Yere yat!", translator.translate("GET DOWN", "en", "tr"))
        assertEquals(listOf("Something else"), live.calls)
    }

    @Test
    fun `a library change re-translates the line that is still on screen`() = runTest {
        val personal = UserTranslationLibrary()
        val translator = LinePackTranslator(FakeTranslator(), LinePackIndex.PREFIX_MATCH_DISABLED) { personal.snapshot() }
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val pipeline = CaptionPipeline(
            frameSource = EmptyFrameSource(),
            textRecognizer = FakeTextRecognizer(MutableList(4) { "Get down!" }),
            translator = translator,
            sinks = listOf(sink),
            speechOutput = speech,
            clock = { 0L },
            cacheVersionTag = { personal.snapshot().versionTag },
        )

        pipeline.onFrame(testFrame(1))
        pipeline.onFrame(testFrame(2))
        assertEquals("TR:Get down!", sink.shown.last().translatedText)

        personal.put("Get down!", "Eğil!")
        pipeline.onFrame(testFrame(3))
        pipeline.onFrame(testFrame(4))

        assertEquals("Ekrandaki aynı satır düzeltmeyle güncellenmeli", "Eğil!", sink.shown.last().translatedText)
        assertEquals(sink.shown.first().cueId, sink.shown.last().cueId)
        assertEquals("Düzeltme aynı satırı yeniden seslendirmemeli", 1, speech.spoken.size)
    }
}
