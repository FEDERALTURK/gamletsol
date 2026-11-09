package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TextRegion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPipelineTest {
    private fun pipeline(
        recognizer: TextRecognizer,
        translator: FakeTranslator = FakeTranslator(),
        sink: RecordingSubtitleSink = RecordingSubtitleSink(),
        speech: RecordingSpeechOutput = RecordingSpeechOutput(),
        clockMillis: MutableList<Long> = mutableListOf(0L),
        cueTtlMillis: Long = 4_000L,
    ) = CaptionPipeline(
        frameSource = EmptyFrameSource(),
        textRecognizer = recognizer,
        translator = translator,
        sinks = listOf(sink),
        speechOutput = speech,
        cueTtlMillis = cueTtlMillis,
        clock = { clockMillis.first() },
    )

    @Test
    fun `single frame does not emit a cue (needs 2-frame stability)`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there"))
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val p = pipeline(recognizer, sink = sink, speech = speech)

        p.onFrame(testFrame(frameId = 1))

        assertTrue("Tek kareden sonra henüz cue yayınlanmamalı", sink.shown.isEmpty())
        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun `two consecutive matching frames emit exactly one cue and speak once`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", "Hello there"))
        val translator = FakeTranslator()
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val p = pipeline(recognizer, translator = translator, sink = sink, speech = speech)

        p.onFrame(testFrame(frameId = 1))
        p.onFrame(testFrame(frameId = 2))

        assertEquals(1, sink.shown.size)
        assertEquals("Hello there", sink.shown[0].originalText)
        assertEquals("TR:Hello there", sink.shown[0].translatedText)
        assertEquals(1, speech.spoken.size)
        assertEquals(listOf("Hello there"), translator.calls)
    }

    @Test
    fun `slightly noisy OCR reading still counts as stable via fuzzy match`() = runTest {
        // İkinci kare tek karakter farklı (OCR gürültüsü) — yine de kararlı kabul edilmeli.
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", "Hello there1"))
        val sink = RecordingSubtitleSink()
        val p = pipeline(recognizer, sink = sink)

        p.onFrame(testFrame(frameId = 1))
        p.onFrame(testFrame(frameId = 2))

        assertEquals(1, sink.shown.size)
    }

    @Test
    fun `same stable text across more frames does not re-trigger speech or new cueId`() = runTest {
        val recognizer = FakeTextRecognizer(
            mutableListOf("Hello there", "Hello there", "Hello there", "Hello there"),
        )
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val p = pipeline(recognizer, sink = sink, speech = speech)

        p.onFrame(testFrame(frameId = 1))
        p.onFrame(testFrame(frameId = 2)) // cue #1 yayınlanır, TTS tetiklenir
        p.onFrame(testFrame(frameId = 3)) // aynı metin: yalnız tazeleme
        p.onFrame(testFrame(frameId = 4)) // aynı metin: yalnız tazeleme

        assertEquals(1, speech.spoken.size)
        val cueIds = sink.shown.map { it.cueId }.toSet()
        assertEquals("Aynı metin boyunca cueId değişmemeli", 1, cueIds.size)
    }

    @Test
    fun `different text after a stable cue creates a new cue and speaks again`() = runTest {
        val recognizer = FakeTextRecognizer(
            mutableListOf("Hello there", "Hello there", "Goodbye now", "Goodbye now"),
        )
        val sink = RecordingSubtitleSink()
        val speech = RecordingSpeechOutput()
        val p = pipeline(recognizer, sink = sink, speech = speech)

        repeat(4) { i -> p.onFrame(testFrame(frameId = i.toLong() + 1)) }

        assertEquals(2, speech.spoken.size)
        assertEquals(2, sink.shown.map { it.cueId }.toSet().size)
    }

    @Test
    fun `empty OCR result resets the pending candidate`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", null, "Hello there", "Hello there"))
        val sink = RecordingSubtitleSink()
        val p = pipeline(recognizer, sink = sink)

        p.onFrame(testFrame(frameId = 1)) // aday: "Hello there"
        p.onFrame(testFrame(frameId = 2)) // boş kare -> aday sıfırlanır
        p.onFrame(testFrame(frameId = 3)) // yeni aday: "Hello there"
        p.onFrame(testFrame(frameId = 4)) // eşleşir -> yayın

        assertEquals(1, sink.shown.size)
    }

    @Test
    fun `active cue is cleared from sinks after its ttl expires`() = runTest {
        val recognizer = FakeTextRecognizer(mutableListOf("Hello there", "Hello there"))
        val sink = RecordingSubtitleSink()
        val clockMillis = mutableListOf(0L)
        val p = pipeline(recognizer, sink = sink, clockMillis = clockMillis, cueTtlMillis = 1_000L)

        p.onFrame(testFrame(frameId = 1))
        clockMillis[0] = 100L
        p.onFrame(testFrame(frameId = 2)) // cue burada 100ms'de oluşturulur, TTL 1000ms

        assertEquals(1, sink.shown.size)
        assertTrue(sink.cleared.isEmpty())

        clockMillis[0] = 1_200L // TTL doldu
        // Not: recognizer listesi tükendiğinde null döner, bu da expire
        // kontrolünün sonraki `onFrame` çağrısında tetiklenmesine yeter.
        p.onFrame(testFrame(frameId = 3))

        assertEquals(listOf(sink.shown[0].cueId), sink.cleared)
    }

    @Test
    fun `G036 - sustained slow OCR causes the pipeline to start skipping frames`() = runTest {
        var recognizeCallCount = 0
        val recognizer = object : TextRecognizer {
            override suspend fun recognize(image: RawImage, region: TextRegion): RecognizedText {
                recognizeCallCount++
                return RecognizedText(text = "Hello there")
            }
        }
        // Her clock() çağrısında 50ms ileri giden bir saat: onFrame içinde
        // işlenen her kare için (atlanmayan) başlangıç/bitiş clock() okuması
        // arasında tam olarak bir "tık" (50ms) geçer — bu, OCR'ın her
        // işlenen karede 50ms sürdüğünü simüle eder.
        var simulatedMillis = 0L
        val clock = { val v = simulatedMillis; simulatedMillis += 50L; v }

        val throttle = AdaptiveFrameThrottle(targetFrameBudgetMillis = 10L, windowSize = 3, maxSkipFactor = 4)
        val sink = RecordingSubtitleSink()
        val p = CaptionPipeline(
            frameSource = EmptyFrameSource(),
            textRecognizer = recognizer,
            translator = FakeTranslator(),
            sinks = listOf(sink),
            clock = clock,
            frameThrottle = throttle,
        )

        val totalFrames = 12
        repeat(totalFrames) { i -> p.onFrame(testFrame(frameId = i.toLong())) }

        // Sürekli "yavaş" (50ms >> 10ms hedef) kareler sonunda atlama oranı
        // artmış olmalı ve bu yüzden recognizer, her kare için değil, daha
        // azı için çağrılmış olmalı (G036 kabul kriteri: "yük artınca OCR
        // frekansı kontrollü düşer").
        assertTrue("Yük altında atlama oranı artmalı", throttle.currentSkipFactor() > 1)
        assertTrue(
            "Recognizer her kare için değil, yalnızca atlanmayanlar için çağrılmalı",
            recognizeCallCount < totalFrames,
        )
    }
}
