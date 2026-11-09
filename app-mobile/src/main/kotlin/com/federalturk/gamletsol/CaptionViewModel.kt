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
import com.federalturk.gamletsol.ui.PhoneScreenSubtitleSink
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Yakalama → OCR → çeviri → altyazı hattını (bkz. core-pipeline
 * `CaptionPipeline`) telefon üzerinde somutlaştıran ViewModel. Kamera
 * bağlama kararı (hangi use-case'ler, hangi lifecycle) Composable tarafında
 * alınır; bu sınıf yalnızca [bindCamera] ile elindeki parçaları birleştirir.
 */
class CaptionViewModel(application: Application) : AndroidViewModel(application) {

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    val subtitleSink = PhoneScreenSubtitleSink()

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
            // NOT (dürüstlük ilkesi): Model indirmesi başarısız olursa (ör.
            // Wi-Fi yok) burada sessizce yutulur ve pipeline yine de
            // başlatılır — ML Kit modeli hazır olana kadar `translate()`
            // çağrıları askıda kalır/hata verebilir. Gerçek bir "model
            // indiriliyor" durumu kullanıcıya gösterilmiyor; bu P2/P4
            // kapsamında (kalite/hata durumları) ele alınacak.
            runCatching { translator.ensureModelDownloaded() }

            val pipeline = CaptionPipeline(
                frameSource = frameSource,
                textRecognizer = MlKitTextRecognizer(),
                translator = translator,
                sinks = listOf(subtitleSink),
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
    }
}
