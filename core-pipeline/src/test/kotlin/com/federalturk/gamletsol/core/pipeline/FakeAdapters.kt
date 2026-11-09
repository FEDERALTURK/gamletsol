package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.Cue
import com.federalturk.gamletsol.core.model.FrameRef
import com.federalturk.gamletsol.core.model.TextRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Testlerde gerçek piksel veri taşımaya gerek olmadığını belirten boş gövde. */
object FakeImage : RawImage

class FakeTextRecognizer(private val scriptedResults: MutableList<String?>) : TextRecognizer {
    override suspend fun recognize(image: RawImage, region: TextRegion): RecognizedText? {
        val next = if (scriptedResults.isNotEmpty()) scriptedResults.removeAt(0) else null
        return next?.let { RecognizedText(text = it) }
    }
}

class FakeTranslator(private val prefix: String = "TR:") : Translator {
    val calls = mutableListOf<String>()

    override suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
        calls += text
        return "$prefix$text"
    }
}

class RecordingSubtitleSink : SubtitleSink {
    val shown = mutableListOf<Cue>()
    val cleared = mutableListOf<String>()

    override fun show(cue: Cue) {
        shown += cue
    }

    override fun clear(cueId: String) {
        cleared += cueId
    }
}

class RecordingSpeechOutput : SpeechOutput {
    val spoken = mutableListOf<Cue>()
    var stopped = false

    override fun speak(cue: Cue) {
        spoken += cue
    }

    override fun stop() {
        stopped = true
    }
}

class EmptyFrameSource(override val sourceEpoch: Long = 1L) : FrameSource {
    override fun frames(): Flow<CapturedFrame> = emptyFlow()
}

fun testFrame(frameId: Long, sourceEpoch: Long = 1L, region: TextRegion = TextRegion.DEFAULT_SUBTITLE_BAND) =
    CapturedFrame(
        ref = FrameRef(sourceEpoch = sourceEpoch, frameId = frameId, capturedAtMillis = frameId),
        image = FakeImage,
        region = region,
    )
