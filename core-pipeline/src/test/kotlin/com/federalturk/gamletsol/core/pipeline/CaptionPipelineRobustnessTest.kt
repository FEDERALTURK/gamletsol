package com.federalturk.gamletsol.core.pipeline

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-13: "yazılar bazen çevriliyor, bazen hiç çevrilmiyor" şikâyetinin
 * çekirdek-hattaki nedenleri için regresyon testleri (bkz. CaptionPipeline
 * KDoc'undaki düzeltme listesi).
 */
class CaptionPipelineRobustnessTest {

    /** İlk [failuresBeforeSuccess] çağrıda istisna fırlatan, sonra normal çeviren sahte çevirmen. */
    private class FlakyTranslator(private var failuresBeforeSuccess: Int) : Translator {
        val calls = mutableListOf<String>()

        override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
            calls += text
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw IllegalStateException("Çeviri modeli henüz hazır değil")
            }
            return "TR:$text"
        }
    }

    private fun pipeline(
        recognizer: TextRecognizer,
        translator: Translator,
        sink: RecordingSubtitleSink,
        speech: RecordingSpeechOutput = RecordingSpeechOutput(),
        errors: MutableList<Throwable> = mutableListOf(),
    ) = CaptionPipeline(
        frameSource = EmptyFrameSource(),
        textRecognizer = recognizer,
        translator = translator,
        sinks = listOf(sink),
        speechOutput = speech,
        clock = { 0L },
        onTranslationError = { errors += it },
    )

    @Test
    fun `a translation failure does not kill the pipeline and is retried on the next stable frames`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", "Hello there", "Hello there", "Hello there"))
        val translator = FlakyTranslator(failuresBeforeSuccess = 1)
        val sink = RecordingSubtitleSink()
        val errors = mutableListOf<Throwable>()
        val p = pipeline(recognizer, translator, sink, errors = errors)

        p.onFrame(testFrame(frameId = 1))
        p.onFrame(testFrame(frameId = 2)) // stabil → çeviri DENENİR ve başarısız olur (istisna dışarı sızmamalı)
        assertTrue("Başarısız çeviri ekrana boş/eksik bir cue basmamalı", sink.shown.isEmpty())
        assertEquals(1, errors.size)

        p.onFrame(testFrame(frameId = 3))
        p.onFrame(testFrame(frameId = 4)) // tekrar stabil → bu sefer başarılı olmalı (hata cache'lenmedi)

        assertEquals(1, sink.shown.size)
        assertEquals("TR:Hello there", sink.shown[0].translatedText)
        assertEquals(2, translator.calls.size)
    }

    @Test
    fun `a growing typewriter line updates the translation instead of freezing the first partial one`() = runTest {
        val partial = "Watch out, the bridge is unstable"
        val full = "Watch out, the bridge is unstable now"
        val recognizer = FakeTextRecognizer(mutableListOf(partial, partial, full, full))
        val translator = FakeTranslator()
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val p = pipeline(recognizer, translator, sink, speech)

        repeat(4) { p.onFrame(testFrame(frameId = it.toLong() + 1)) }

        assertEquals(2, sink.shown.size)
        val first = sink.shown[0]
        val updated = sink.shown[1]
        assertEquals("TR:$partial", first.translatedText)
        assertEquals("Ekranda kalan cue'nun çevirisi yeni metne göre güncellenmeli", "TR:$full", updated.translatedText)
        assertEquals("Aynı altyazı satırı aynı cueId'yi korumalı", first.cueId, updated.cueId)
        assertEquals(first.revision + 1, updated.revision)
        assertEquals("Aynı satırın güncellemesi yeniden seslendirilmemeli", 1, speech.spoken.size)
    }

    @Test
    fun `identical text on screen only refreshes the cue and is translated once`() = runTest {
        val recognizer = FakeTextRecognizer(MutableList(6) { "Hello there" })
        val translator = FakeTranslator()
        val sink = RecordingSubtitleSink()
        val p = pipeline(recognizer, translator, sink)

        repeat(6) { p.onFrame(testFrame(frameId = it.toLong() + 1)) }

        assertEquals(listOf("Hello there"), translator.calls)
        assertTrue(sink.shown.all { it.cueId == sink.shown.first().cueId && it.revision == 0 })
    }

    @Test
    fun `a single unreadable frame between two good reads does not prevent stabilisation`() = runTest {
        // Ortadaki `null`: bulanık/okunamayan tek kare. Önceden bu, bekleyen
        // adayı silip kararlılığı sıfırlıyordu.
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", null, "Hello there"))
        val sink = RecordingSubtitleSink()
        val p = pipeline(recognizer, FakeTranslator(), sink)

        repeat(3) { p.onFrame(testFrame(frameId = it.toLong() + 1)) }

        assertEquals(1, sink.shown.size)
        assertEquals("TR:Hello there", sink.shown[0].translatedText)
    }

    @Test
    fun `a long run of empty frames still discards the pending candidate`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", null, null, null, "Hello there"))
        val sink = RecordingSubtitleSink()
        val p = pipeline(recognizer, FakeTranslator(), sink)

        repeat(5) { p.onFrame(testFrame(frameId = it.toLong() + 1)) }

        assertTrue("Uzun bir boşluktan sonra tek bir okuma henüz kararlı sayılmamalı", sink.shown.isEmpty())
    }
}
