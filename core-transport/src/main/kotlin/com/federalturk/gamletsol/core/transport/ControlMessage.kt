package com.federalturk.gamletsol.core.transport

/**
 * Protokol v2 (2026-11-13) kontrol mesajları — altyazı içermeyen, telefon →
 * TV yönlü kısa satırlar:
 *
 * - [ControlType.CLEAR]: "ekrandaki altyazıyı kaldır". Telefondaki altyazı
 *   süresi dolduğunda (ekranda artık yazı yok) gönderilir. Önceden TV son
 *   altyazıyı, yenisi gelene kadar sonsuza dek gösteriyordu.
 * - [ControlType.PING]: "hâlâ buradayım" nabzı. TV bir süre hiçbir satır
 *   almazsa telefonun gittiğine karar verir (bkz. TvEventServer).
 *
 * Biçim: `CTL:<TÜR>|<sıra>|<epoch>`. Sıra ve epoch, altyazı satırlarıyla
 * ([CueEventCodec]) AYNI sayaçtan gelir; böylece [CueSequenceGuard] bir
 * "temizle"nin kendisinden sonra gönderilmiş bir altyazıyı silmesini önler.
 * Eski (v1) bir TV bu satırları zaten çözemez ve sessizce atlar
 * ([CueEventCodec.decode] `null` döner); yine de telefon bu mesajları
 * yalnız [TvCapabilities] destekliyorsa gönderir.
 */
enum class ControlType { CLEAR, PING }

data class DecodedControlMessage(val type: ControlType, val sequence: Long, val epochId: String)

object ControlMessageCodec {
    const val PREFIX = "CTL:"

    fun encode(type: ControlType, sequence: Long, epochId: String): String = "$PREFIX${type.name}|$sequence|$epochId"

    fun isControl(line: String): Boolean = line.startsWith(PREFIX)

    fun decode(line: String): DecodedControlMessage? {
        if (!isControl(line) || line.length > MAX_LINE_LENGTH) return null
        val parts = line.removePrefix(PREFIX).split("|")
        if (parts.size != 3) return null
        val type = ControlType.entries.firstOrNull { it.name == parts[0] } ?: return null
        val sequence = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val epochId = parts[2]
        if (epochId.isBlank() || epochId.length > CueEventCodec.MAX_EPOCH_ID_LENGTH) return null
        return DecodedControlMessage(type, sequence, epochId)
    }

    private const val MAX_LINE_LENGTH = 256
}
