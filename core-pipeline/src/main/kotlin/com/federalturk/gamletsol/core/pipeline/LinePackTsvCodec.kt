package com.federalturk.gamletsol.core.pipeline

import com.federalturk.gamletsol.core.model.TranslationLine
import com.federalturk.gamletsol.core.model.TranslationLinePack

/**
 * [TranslationLinePack]'in diskteki/uygulama içindeki metin biçimi
 * (2026-11-13, çeviri kütüphanesi).
 *
 * Neden TSV: elle düzenlemesi kolay (bir metin editörü veya tablo
 * programıyla açılabilir), satır satır `git diff` okunabilir ve core
 * modüllerine yeni bir serileştirme bağımlılığı eklemez.
 *
 * Biçim:
 * ```
 * #gamletsol-linepack 1
 * #packId=genel-oyun-ifadeleri
 * #gameProfileId=genel
 * #version=3
 * # boş satırlar ve '# ' ile başlayan satırlar açıklamadır
 * Loading	Yükleniyor
 * Press {0} to continue	Devam etmek için {0} tuşuna basın
 * ```
 * Her veri satırı: `kaynak<TAB>çeviri[<TAB>konuşmacı[<TAB>stil]]`. Metin
 * içindeki sekme, satır sonu ve ters eğik çizgi `\t`, `\n`, `\\` olarak
 * kaçışlanır. Bozuk satırlar atlanır (tek hatalı satır tüm kütüphaneyi
 * kullanılamaz yapmaz) ve [DecodeResult.skippedLines] ile bildirilir.
 */
object LinePackTsvCodec {

    private const val MAGIC = "#gamletsol-linepack"
    private const val FORMAT_VERSION = 1

    data class DecodeResult(val pack: TranslationLinePack, val skippedLines: Int)

    fun encode(pack: TranslationLinePack): String = buildString {
        append(MAGIC).append(' ').append(FORMAT_VERSION).append('\n')
        append("#packId=").append(escape(pack.packId)).append('\n')
        append("#gameProfileId=").append(escape(pack.gameProfileId)).append('\n')
        append("#version=").append(pack.version).append('\n')
        for (line in pack.lines) {
            append(escape(line.sourceText)).append('\t').append(escape(line.translatedText))
            if (line.speaker != null || line.style != null) {
                append('\t').append(escape(line.speaker.orEmpty()))
            }
            line.style?.let { style -> append('\t').append(escape(style)) }
            append('\n')
        }
    }

    /**
     * [text]'i çözer. Başlıkta eksik alan varsa [defaultPackId] /
     * [defaultGameProfileId] / sürüm 1 kullanılır.
     */
    fun decode(
        text: String,
        defaultPackId: String = "kutuphane",
        defaultGameProfileId: String = "genel",
    ): DecodeResult {
        var packId = defaultPackId
        var gameProfileId = defaultGameProfileId
        var version = 1
        val lines = mutableListOf<TranslationLine>()
        var skipped = 0

        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                when {
                    line.startsWith("#packId=") -> packId = unescape(line.removePrefix("#packId=")).ifBlank { packId }
                    line.startsWith("#gameProfileId=") ->
                        gameProfileId = unescape(line.removePrefix("#gameProfileId=")).ifBlank { gameProfileId }
                    line.startsWith("#version=") -> version = line.removePrefix("#version=").trim().toIntOrNull()?.coerceAtLeast(1) ?: version
                }
                continue
            }
            val fields = line.split('\t').map(::unescape)
            val source = fields.getOrNull(0)?.trim().orEmpty()
            val translated = fields.getOrNull(1)?.trim().orEmpty()
            if (source.isEmpty() || translated.isEmpty()) {
                skipped++
                continue
            }
            lines += TranslationLine(
                sourceText = source,
                translatedText = translated,
                speaker = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
                style = fields.getOrNull(3)?.takeIf { it.isNotBlank() },
            )
        }

        return DecodeResult(
            pack = TranslationLinePack(packId = packId, gameProfileId = gameProfileId, version = version, lines = lines),
            skippedLines = skipped,
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(c).append(value[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
