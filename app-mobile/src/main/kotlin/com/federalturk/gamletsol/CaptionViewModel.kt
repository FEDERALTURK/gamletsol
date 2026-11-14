package com.federalturk.gamletsol

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.federalturk.gamletsol.core.model.GameProfile
import com.federalturk.gamletsol.core.model.SampleGameProfiles
import com.federalturk.gamletsol.core.model.TranslationLinePack
import com.federalturk.gamletsol.core.pipeline.CaptionPipeline
import com.federalturk.gamletsol.core.pipeline.LinePackIndex
import com.federalturk.gamletsol.core.pipeline.LinePackTranslator
import com.federalturk.gamletsol.core.pipeline.TermPreservingTranslator
import com.federalturk.gamletsol.feature.capture.CameraXFrameSource
import com.federalturk.gamletsol.ocr.MlKitTextRecognizer
import com.federalturk.gamletsol.speech.AndroidTtsSpeechOutput
import com.federalturk.gamletsol.speech.TurkishVoiceStatus
import com.federalturk.gamletsol.translate.MlKitTranslator
import com.federalturk.gamletsol.translate.TranslationLibraryStore
import com.federalturk.gamletsol.tv.TvLinkState
import com.federalturk.gamletsol.tv.TvRelaySubtitleSink
import com.federalturk.gamletsol.ui.PhoneScreenSubtitleSink
import android.util.Log
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /** Bağlantı koptu; TV'nin verdiği jetonla kendiliğinden yeniden bağlanılıyor (QR gerekmez). */
    RECONNECTING,
    FAILED,
}

class CaptionViewModel(application: Application) : AndroidViewModel(application) {

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    private val _pipelineStatus = MutableStateFlow(PipelineStatus.STARTING)
    val pipelineStatus: StateFlow<PipelineStatus> = _pipelineStatus.asStateFlow()

    val subtitleSink = PhoneScreenSubtitleSink()

    // Gönderim başarısız olursa (TV kapandı, Wi-Fi koptu) arayüz artık
    // "Bağlı" demeye devam etmiyor — önceden sink sessizce bağlantıyı
    // bırakıyor, ekranda ise "TV'ye bağlı" yazmaya devam ediyordu.
    private val tvRelaySink = TvRelaySubtitleSink(
        onLinkStateChanged = { state ->
            _tvConnectionState.value = when (state) {
                TvLinkState.CONNECTED -> TvConnectionState.CONNECTED
                TvLinkState.RECONNECTING -> TvConnectionState.RECONNECTING
                TvLinkState.LOST -> TvConnectionState.FAILED
            }
        },
    )

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
     *
     * [fingerprintSha256] (G024): QR ile tarandıysa TV'nin TLS sertifika
     * fingerprint'i ([PairingQrPayload.fingerprintSha256]) — verilirse
     * gerçek MITM korumalı pinning uygulanır. Manuel giriş yolunda `null`
     * geçilir (bkz. [TvRelaySubtitleSink.connectBlocking] dokümantasyonu).
     */
    fun connectToTv(host: String, port: Int, pairingCode: String, fingerprintSha256: String? = null) {
        viewModelScope.launch {
            _tvConnectionState.value = TvConnectionState.CONNECTING
            val connected = withContext(Dispatchers.IO) {
                tvRelaySink.connectBlocking(host, port, pairingCode, expectedFingerprintSha256 = fingerprintSha256)
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

    // G031: etkin oyun profili (terim sözlüğü/ROI) — kullanıcı henüz hiçbir
    // profil seçmemişse `null` (hiçbir terim korunmaz, yalnız sayılar — bkz.
    // TermPreservingTranslator). Kalıcı depolama YOK; her uygulama açılışında
    // sıfırlanır (bkz. GameProfileStore KDoc'u, henüz bir kalıcılık adaptörü
    // eklenmedi).
    private val _activeGameProfile = MutableStateFlow<GameProfile?>(null)
    val activeGameProfile: StateFlow<GameProfile?> = _activeGameProfile.asStateFlow()

    /** UI'nın (örn. bir ayarlar menüsü) seçilebilir örnek profilleri listelemesi için. */
    val availableGameProfiles: List<GameProfile> = SampleGameProfiles.ALL

    fun selectGameProfile(profile: GameProfile?) {
        _activeGameProfile.value = profile
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val rawTranslator = MlKitTranslator()

    // G031: TermPreservingTranslator, sayıları HER ZAMAN ve (bir profil
    // seçiliyse) profildeki buton/istatistik/isim terimlerini ML Kit'e
    // vermeden önce maskeler, çeviri sonrası geri yükler (bkz. core-pipeline
    // TermPreservingTranslator KDoc'u).
    private val liveTranslator = TermPreservingTranslator(rawTranslator) { _activeGameProfile.value }

    // Çeviri kütüphanesi (2026-11-13, yol haritası G073'ün uygulamaya
    // bağlanması). Bir satır şu sırayla aranır:
    //   1) kullanıcının kişisel düzeltmeleri,
    //   2) gömülü genel oyun ifadeleri (menü/sistem iletileri, tuş istemleri,
    //      kısa diyaloglar),
    //   3) bulunamazsa ML Kit ile canlı (cihaz-üstü) çeviri.
    // Kütüphanede bulunan satır canlı çeviriye hiç gitmez (daha hızlı ve
    // tutarlı). Önek eşleşmesi bu genel kütüphanelerde kapalı — bkz.
    // LinePackTranslator KDoc'u.
    private val libraryStore = TranslationLibraryStore(application)
    private val translator = LinePackTranslator(
        delegate = LinePackTranslator(
            delegate = liveTranslator,
            minPrefixLength = LinePackIndex.PREFIX_MATCH_DISABLED,
        ) { libraryStore.builtinPack },
        minPrefixLength = LinePackIndex.PREFIX_MATCH_DISABLED,
    ) { libraryStore.personal.snapshot() }

    /** Kişisel çeviri kütüphanesi (arayüzdeki "Çeviri kütüphanem" listesi için). */
    val personalLibrary: StateFlow<TranslationLinePack> = libraryStore.personalSnapshot

    /** Gömülü genel ifadeler kütüphanesindeki satır sayısı (yüklenmediyse 0). */
    private val _builtinLibrarySize = MutableStateFlow(0)
    val builtinLibrarySize: StateFlow<Int> = _builtinLibrarySize.asStateFlow()

    /**
     * Kullanıcının ekrandaki bir çeviriyi düzeltmesi: [sourceText] bir daha
     * görüldüğünde [translatedText] gösterilir. Ekranda duran satır da
     * hemen (sonraki kararlı karede) güncellenir.
     */
    fun saveTranslationCorrection(sourceText: String, translatedText: String) {
        if (sourceText.isBlank() || translatedText.isBlank()) return
        viewModelScope.launch { libraryStore.saveCorrection(sourceText, translatedText) }
    }

    fun removeTranslationCorrection(sourceText: String) {
        viewModelScope.launch { libraryStore.removeCorrection(sourceText) }
    }

    fun clearTranslationLibrary() {
        viewModelScope.launch { libraryStore.clearPersonal() }
    }
    private val speechOutput = AndroidTtsSpeechOutput(application)

    private val _turkishVoiceStatusMessage = MutableStateFlow<String?>(null)
    val turkishVoiceStatusMessage: StateFlow<String?> = _turkishVoiceStatusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            libraryStore.load()
            _builtinLibrarySize.value = libraryStore.builtinPack?.lines?.size ?: 0
        }

        // G032: TTS motoru asenkron başlatılıyor (TextToSpeech constructor'ı
        // hazır olana kadar `ready=false`) — bu yüzden durum birkaç kez
        // (kısa aralıklarla, en fazla ~2 sn) yoklanır; ilk "NotYetInitialized"
        // durumunu geçtikten sonra durur. Gerçek bir Flow/callback yerine
        // yoklama kullanıldı çünkü AndroidTtsSpeechOutput üçüncü taraf
        // TextToSpeech API'sinin kendi callback'ine bağlı, buraya ayrı bir
        // dinleyici eklemek gereksiz karmaşıklık katardı.
        viewModelScope.launch {
            repeat(10) {
                val status = speechOutput.turkishVoiceStatus()
                _turkishVoiceStatusMessage.value = describeVoiceStatus(status)
                if (status !is TurkishVoiceStatus.NotYetInitialized) return@launch
                delay(200)
            }
        }
    }

    private fun describeVoiceStatus(status: TurkishVoiceStatus): String? = when (status) {
        is TurkishVoiceStatus.NotYetInitialized -> null
        is TurkishVoiceStatus.Unavailable ->
            "Bu cihazda kurulu bir Türkçe ses bulunamadı — altyazı yine de çalışır, sesli okuma yapılamaz."
        is TurkishVoiceStatus.Available -> if (status.requiresNetwork) {
            "Türkçe ses kurulu ama ağ bağlantısı gerektiriyor — çevrimdışıyken sesli okuma çalışmayabilir."
        } else {
            null // Normal/çevrimdışı çalışan ses kurulu — kullanıcıya ekstra bir uyarı göstermeye gerek yok.
        }
    }

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

    // Yatay mod desteği (2026-11-13): döndürmede Activity yeniden
    // oluşturulmadığı için bağlı use-case'lerin hedef dönüşü elle
    // güncellenir (bkz. [onDisplayRotationChanged], ui/DisplayRotation.kt).
    private var boundPreview: Preview? = null
    private var boundAnalysis: ImageAnalysis? = null

    /**
     * Kamerayı [lifecycleOwner]'a bağlar ve (ilk çağrıda) hattı başlatır.
     * [targetRotation]: ekranın o anki dönüşü (`Surface.ROTATION_*`).
     */
    fun bindCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        targetRotation: Int,
    ) {
        val analysis = frameSource.buildAnalysisUseCase(cameraExecutor)
        analysis.targetRotation = targetRotation
        preview.targetRotation = targetRotation

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
        boundPreview = preview
        boundAnalysis = analysis

        startPipelineIfNeeded()
    }

    /**
     * Ekran döndüğünde çağrılır. Analiz use-case'inin hedef dönüşü
     * güncellenmezse kare OCR'a yan yatmış gider (ML Kit dönüşü
     * `rotationDegrees`'ten okur) ve yatayda hiçbir yazı tanınmaz.
     */
    fun onDisplayRotationChanged(rotation: Int) {
        boundPreview?.targetRotation = rotation
        boundAnalysis?.targetRotation = rotation
    }

    private fun startPipelineIfNeeded() {
        if (pipelineStarted) return
        pipelineStarted = true

        // 2026-11-13 hata düzeltmeleri:
        // 1) Hat artık model indirmesini BEKLEMEDEN hemen kareleri dinlemeye
        //    başlar (eskiden bekleme sırasında gelen ilk kare kamera analizini
        //    donduruyordu — bkz. CameraXFrameSource KDoc'u). Model hazır
        //    değilken çeviri hatası hattı durdurmaz, o kare atlanır.
        // 2) Hat ana (UI) iş parçacığında değil Dispatchers.Default'ta çalışır:
        //    Levenshtein/OCR sonrası işler arayüzü yavaşlatmaz, TV'ye gönderim
        //    ana iş parçacığında ağ erişimi yasağına (NetworkOnMainThread)
        //    takılmaz.
        // 3) Model indirmesi başarısız olursa (Wi-Fi yok vb.) artık bir kez
        //    deneyip vazgeçilmez; arka planda periyodik olarak yeniden denenir.
        viewModelScope.launch {
            ensureTranslationModelWithRetry()
        }

        viewModelScope.launch(Dispatchers.Default) {
            val pipeline = CaptionPipeline(
                frameSource = frameSource,
                textRecognizer = MlKitTextRecognizer(),
                translator = translator,
                sinks = listOf(subtitleSink, tvRelaySink),
                speechOutput = speechOutput,
                // Kütüphane değişince (yüklendi / düzeltme eklendi) önbellek
                // eski çeviriyi göstermesin, ekrandaki satır yeniden çevrilsin.
                cacheVersionTag = libraryStore::versionTag,
                onTranslationError = {
                    // Model henüz inmediyse kullanıcıya bunu göster; aksi halde
                    // tek seferlik geçici hata — sonraki karede yeniden denenir.
                    if (_pipelineStatus.value == PipelineStatus.SCANNING && !modelReady) {
                        _pipelineStatus.value = PipelineStatus.DOWNLOADING_TRANSLATION_MODEL
                    }
                },
                logger = { msg -> Log.d("GamletsolPipeline", msg) },
            )
            pipeline.run()
        }
    }

    @Volatile
    private var modelReady = false

    private suspend fun ensureTranslationModelWithRetry() {
        _pipelineStatus.value = PipelineStatus.DOWNLOADING_TRANSLATION_MODEL
        while (true) {
            val result = runCatching { rawTranslator.ensureModelDownloaded() }
            if (result.isSuccess) {
                modelReady = true
                _pipelineStatus.value = PipelineStatus.SCANNING
                return
            }
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) return
            _pipelineStatus.value = PipelineStatus.MODEL_DOWNLOAD_FAILED
            delay(MODEL_DOWNLOAD_RETRY_MILLIS)
        }
    }

    private companion object {
        /** Model indirme yeniden deneme aralığı (varsayım, ölçüm değil). */
        const val MODEL_DOWNLOAD_RETRY_MILLIS = 15_000L
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        rawTranslator.close()
        speechOutput.shutdown()
        tvRelaySink.shutdown()
    }
}
