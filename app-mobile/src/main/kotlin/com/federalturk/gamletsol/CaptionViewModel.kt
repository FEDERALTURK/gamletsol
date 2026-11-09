package com.federalturk.gamletsol

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.federalturk.gamletsol.core.pipeline.CaptionPipeline
import com.federalturk.gamletsol.feature.capture.CameraXFrameSource
import com.federalturk.gamletsol.ocr.MlKitTextRecognizer
import com.federalturk.gamletsol.speech.AndroidTtsSpeechOutput
import com.federalturk.gamletsol.translate.MlKitTranslator
import com.federalturk.gamletsol.tv.TvRelaySubtitleSink
import com.federalturk.gamletsol.ui.PhoneScreenSubtitleSink
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Yakalama → OCR → çeviri → altyazı hattını (bkz. core-pipeline
 * `CaptionPipeline`) telefon üzerinde somutlaştıran ViewModel. Kamera
 * bağlama kararı (hangi use-case'ler, hangi lifecycle) Composable tarafında
 * alınır; bu sınıf yalnızca [bindCamera] ile elindeki parçaları birleştirir.
 */
/**
 * Kullanıcıya gösterilecek durum — eklenme nedeni: ilk beta sürümünde
 * ekranda kamera önizlemesi dışında HİÇBİR geri bildirim yoktu (izin
 * verildikten sonra herhangi bir metin/menü/başlık görünmüyordu); bu alan
 * [com.federalturk.gamletsol.ui.CaptionScreen]'deki üst bar/durum satırının
 * neyi göstereceğine karar vermek için eklendi.
 */
enum class PipelineStatus {
    STARTING,
    DOWNLOADING_TRANSLATION_MODEL,
    MODEL_DOWNLOAD_FAILED,
    SCANNING,
}

/**
 * "TV'ye Yansıt" bağlantısının durumu (yol haritası P3/G022-G030, MVP).
 * Eklenme nedeni: kullanıcının asıl istediği özellik telefon↔TV çeviri
 * yansıtmasıydı — bu alan, CaptionScreen'deki bağlantı diyaloğunun hangi
 * mesajı/butonu göstereceğine karar vermesi için var.
 */
enum class TvConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

class CaptionViewModel(application: Application) : AndroidViewModel(application) {

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    private val _pipelineStatus = MutableStateFlow(PipelineStatus.STARTING)
    val pipelineStatus: StateFlow<PipelineStatus> = _pipelineStatus.asStateFlow()

    val subtitleSink = PhoneScreenSubtitleSink()
    private val tvRelaySink = TvRelaySubtitleSink()

    private val _tvConnectionState = MutableStateFlow(TvConnectionState.DISCONNECTED)
    val tvConnectionState: StateFlow<TvConnectionState> = _tvConnectionState.asStateFlow()

    /**
     * TV'ye bağlanmayı dener (bloklayan soket çağrısı `Dispatchers.IO`'da
     * çalışır). [pairingCode], TV ekranında gösterilen, tek kullanımlık
     * eşleştirme kodudur (G023) — yanlış/süresi geçmiş bir kod da
     * [TvConnectionState.FAILED] ile sonuçlanır (bağlanamama ile
     * eşleştirme reddi ayrıştırılmaz, MVP sınırı). Başarılı olursa,
     * pipeline'ın ürettiği her yeni cue otomatik olarak [tvRelaySink]
     * üzerinden TV'ye gönderilmeye başlar.
     */
    fun connectToTv(host: String, port: Int, pairingCode: String) {
        viewModelScope.launch {
            _tvConnectionState.value = TvConnectionState.CONNECTING
            val connected = withContext(Dispatchers.IO) {
                tvRelaySink.connectBlocking(host, port, pairingCode)
            }
            _tvConnectionState.value = if (connected) {
                TvConnectionState.CONNECTED
            } else {
                TvConnectionState.FAILED
            }
        }
    }

    fun disconnectFromTv() {
        tvRelaySink.disconnect()
        _tvConnectionState.value = TvConnectionState.DISCONNECTED
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val translator = MlKitTranslator()
    private val speechOutput = AndroidTtsSpeechOutput(application)

    // sourceEpoch = ViewModel/uygulama oturumu başına sabit; kamera her
    // yeniden bağlandığında (konfigürasyon değişikliği vb.) ViewModel hayatta
    // kaldığı sürece değişmez. Gerçek kaynak değişimi (yeniden bağlanma,
    // farklı bir yakalama kaynağına geçiş) burada henüz ele alınmıyor — bkz.
    // CameraXFrameSource dokümantasyonu.
    private val frameSource = CameraXFrameSource(sourceEpoch = System.currentTimeMillis())

    private var pipelineStarted = false

    fun onCameraPermissionResult(granted: Boolean) {
        _cameraPermissionGranted.value = granted
    }

    /** Kamerayı [lifecycleOwner]'a bağlar ve (ilk çağrıda) hattı başlatır. */
    fun bindCamera(cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner, preview: Preview) {
        val analysis = frameSource.buildAnalysisUseCase(cameraExecutor)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )

        startPipelineIfNeeded()
    }

    private fun startPipelineIfNeeded() {
        if (pipelineStarted) return
        pipelineStarted = true

        viewModelScope.launch {
            _pipelineStatus.value = PipelineStatus.DOWNLOADING_TRANSLATION_MODEL
            val modelResult = runCatching { translator.ensureModelDownloaded() }
            // NOT (dürüstlük ilkesi): Model indirme başarısız olsa bile
            // (ör. Wi-Fi yok) pipeline yine başlatılıyor — ML Kit modeli
            // hazır olana kadar sonraki `translate()` çağrıları askıda
            // kalabilir/hata verebilir. `MODEL_DOWNLOAD_FAILED` durumu
            // kullanıcıya gösteriliyor (bkz. CaptionScreen) ama otomatik
            // yeniden deneme YOK — bu hâlâ P2/P4 kapsamında ele alınacak.
            _pipelineStatus.value = if (modelResult.isSuccess) {
                PipelineStatus.SCANNING
            } else {
                PipelineStatus.MODEL_DOWNLOAD_FAILED
            }

            val pipeline = CaptionPipeline(
                frameSource = frameSource,
                textRecognizer = MlKitTextRecognizer(),
                translator = translator,
                sinks = listOf(subtitleSink, tvRelaySink),
                speechOutput = speechOutput,
            )
            pipeline.run()
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        translator.close()
        speechOutput.shutdown()
        tvRelaySink.disconnect()
    }
}
