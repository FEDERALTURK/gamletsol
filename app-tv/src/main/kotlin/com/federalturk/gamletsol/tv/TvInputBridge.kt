package com.federalturk.gamletsol.tv

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log
import com.federalturk.gamletsol.core.model.tv.TvInputSummary

private const val TAG = "GamletsolTvProbe"

/**
 * `android.media.tv.TvInputManager`ın ince Android sarmalayıcısı. Gerçek
 * seçim/karar mantığı `core-model`deki [TvInputSummary] ve
 * `selectPreferredHdmiPassthroughInput` içindedir (Android SDK'sız test
 * edilebilir); bu dosya yalnızca platform API'sinden saf veri modeline
 * eşleme yapar.
 *
 * NOT (dürüstlük ilkesi — G002'nin asıl konusu): `TvInputInfo.getType()`
 * değerinin [TvInputInfo.TYPE_HDMI] olup olmadığı ve `isPassthroughInput()`
 * dönüşü, standart AOSP davranışına göre yazıldı ama **hiçbir gerçek
 * Android TV / Google TV cihazında denenmedi**. Bazı OEM'lerin (burada
 * hiçbiri hedeflenmedi, genel API kullanılıyor) `getTvInputList()`'i boş
 * döndürmesi veya `SecurityException` fırlatması ihtimaline karşı bu kod
 * hatayı yutmaz, [TvInputProbeResult.error] alanına yazar.
 */
data class TvInputProbeResult(
    val inputs: List<TvInputSummary>,
    val error: String? = null,
)

object TvInputBridge {

    fun discoverInputs(context: Context): TvInputProbeResult {
        return try {
            val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
                ?: return TvInputProbeResult(
                    inputs = emptyList(),
                    error = "TvInputManager sistem servisi yok — bu cihaz standart bir " +
                        "Android TV / Google TV framework'ü sunmuyor olabilir.",
                )
            val summaries = manager.tvInputList.map { it.toSummary(context, manager) }
            Log.i(TAG, "Algılanan ${summaries.size} TV girişi: $summaries")
            TvInputProbeResult(inputs = summaries)
        } catch (e: SecurityException) {
            // NOT: Bazı cihazlarda TV girişi listesine erişim için ek izin
            // gerekebilir; bu kum havuzunda hangi cihazların bunu
            // gerektirdiği doğrulanamadı.
            TvInputProbeResult(inputs = emptyList(), error = "SecurityException: ${e.message}")
        } catch (e: Exception) {
            TvInputProbeResult(inputs = emptyList(), error = "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun TvInputInfo.toSummary(context: Context, manager: TvInputManager): TvInputSummary {
        val label = try {
            loadLabel(context)?.toString()
        } catch (e: Exception) {
            null
        }
        return TvInputSummary(
            inputId = id,
            isHdmi = type == TvInputInfo.TYPE_HDMI,
            isPassthrough = isPassthroughInput,
            label = label,
        )
    }
}
