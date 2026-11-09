package com.federalturk.gamletsol.tv

import android.media.tv.TvContract
import android.media.tv.TvView
import android.util.Log

private const val TAG = "GamletsolTvProbe"

/** Tune sonucunun kullanıcıya gösterilecek durumları. */
enum class TuneStatus {
    IDLE,
    TUNING,
    VIDEO_AVAILABLE,
    VIDEO_UNAVAILABLE,
    CONNECTION_FAILED,
    DISCONNECTED,
}

/**
 * Bir `TvView`'ı verilen passthrough girişine (örn. Xbox'ın bağlı olduğu
 * HDMI portu) tune eden ince sarmalayıcı.
 *
 * NOT (dürüstlük ilkesi — G003'ün asıl konusu): `TvContract
 * .buildChannelUriForPassthroughInput` + `TvView.tune` çağrısının gerçek
 * bir HDMI passthrough girişinde görüntü döndürüp döndürmediği, ve
 * `TvView`'ın üstüne eklenen bir Compose/View overlay'in gerçekten video
 * katmanının ÜSTÜNDE göründüğü (bazı eski `SurfaceView` tabanlı
 * bindirmelerde görünmeyebilir) **hiç test edilmedi**. Bu sınıf yalnızca
 * resmi `android.media.tv` dokümantasyonundaki kullanım şekline göre
 * yazıldı.
 */
class PassthroughTuner(
    private val tvView: TvView,
    private val onStatusChanged: (TuneStatus) -> Unit,
) {
    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(inputId: String) {
                Log.w(TAG, "onConnectionFailed: $inputId")
                onStatusChanged(TuneStatus.CONNECTION_FAILED)
            }

            override fun onDisconnected(inputId: String) {
                Log.w(TAG, "onDisconnected: $inputId")
                onStatusChanged(TuneStatus.DISCONNECTED)
            }

            override fun onVideoAvailable(inputId: String) {
                Log.i(TAG, "onVideoAvailable: $inputId")
                onStatusChanged(TuneStatus.VIDEO_AVAILABLE)
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                Log.w(TAG, "onVideoUnavailable: $inputId reason=$reason")
                onStatusChanged(TuneStatus.VIDEO_UNAVAILABLE)
            }
        })
    }

    fun tuneTo(inputId: String) {
        onStatusChanged(TuneStatus.TUNING)
        val channelUri = TvContract.buildChannelUriForPassthroughInput(inputId)
        tvView.tune(inputId, channelUri)
    }

    fun release() {
        tvView.reset()
    }
}
