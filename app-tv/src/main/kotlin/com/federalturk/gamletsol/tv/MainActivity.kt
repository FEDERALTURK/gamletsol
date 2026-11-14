package com.federalturk.gamletsol.tv

import android.media.tv.TvView
import android.os.Bundle
import com.federalturk.gamletsol.core.pairing.ResumeTokenRegistry
import android.view.WindowManager
import android.os.Looper
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.federalturk.gamletsol.core.model.tv.CaptionDisplaySettings
import com.federalturk.gamletsol.core.model.tv.CaptionVerticalPosition
import com.federalturk.gamletsol.core.model.tv.TvInputSummary
import com.federalturk.gamletsol.core.model.tv.selectPreferredHdmiPassthroughInput
import com.federalturk.gamletsol.core.pairing.PairingQrPayload
import com.federalturk.gamletsol.core.pairing.PairingRegistry
import com.federalturk.gamletsol.core.pairing.PairingResult
import com.federalturk.gamletsol.core.pairing.PairingToken
import com.federalturk.gamletsol.core.pairing.QrMatrixEncoder
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.TvCapabilities
import com.federalturk.gamletsol.core.transport.TvEventServer
import com.federalturk.gamletsol.core.transport.TvNsdAttributes
import com.federalturk.gamletsol.core.transport.TvTlsIdentity
import com.federalturk.gamletsol.core.transport.TvTlsIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * G001-G003 kanıt ekranı + P3/G022-G030'un ilk sürümü (telefondan gelen
 * cue'ları gösterme). Bu uygulamanın yaptıkları:
 *
 * 1. Cihazdaki TV girişlerini listele (`TvInputBridge`, G002).
 * 2. Bulunan ilk HDMI passthrough girişine (varsa) `TvView` ile tune ol.
 * 3. Üstüne, G001-G003 testi için sabit+canlı-saatli bir Türkçe test yazısı
 *    bindir — saat canlı güncellendiği için yazının "donmuş bir ekran
 *    görüntüsü" olmadığı, gerçekten üst üste render edildiği anlaşılır.
 * 4. Bir `TvEventServer` (core-transport) başlat ve telefondan gelen
 *    gerçek [CueEvent]'leri aynı overlay alanında göster — kullanıcının
 *    telefonda gireceği IP/port, ekranda gösterilir.
 * 5. (G023) Bir `PairingRegistry` (core-pairing) ile 60 saniye geçerli,
 *    tek kullanımlık bir eşleştirme kodu üretir ve geri sayımla gösterir;
 *    `TvEventServer`'ın `validatePairingCode` doğrulayıcısı bu kayda
 *    bağlanır — yanlış/süresi geçmiş/tekrar kullanılan bir kod artık
 *    bağlantıyı reddeder.
 * 6. (G023, 2026-11-09) Aynı IP/port/kod'u bir [QrCodeView] ile QR kodu
 *    olarak da gösterir — `app-mobile`'daki kamera tarayıcısı bunu
 *    okuyabilir, kullanıcı artık üç alanı elle yazmak ZORUNDA değildir
 *    (manuel giriş hâlâ var, bu yalnızca bir kısayol).
 * 7. (G024, 2026-11-09) Bağlantı artık TLS ile şifreli; eşleştirme kodu/
 *    QR, ek olarak TV'nin TLS sertifika fingerprint'ini de taşır
 *    ([TvTlsIdentity]) — QR ile bağlanan telefonlar gerçek MITM korumalı
 *    pinning kullanır.
 * 8. (G025, 2026-11-09) [TvNsdAdvertiser] ile bu TV, gerçek Android
 *    `NsdManager`'ı (mDNS/DNS-SD) üzerinden `app-mobile`'a otomatik olarak
 *    reklam edilir — kod/fingerprint TXT kayıtlarında taşınır
 *    ([TvNsdAttributes]). QR ve manuel giriş hâlâ çalışır; NSD yalnızca
 *    bir kısayol, engellenmişse (bazı router'larda mDNS kapalı) sessizce
 *    atlanır.
 * 9. (G027, 2026-11-09) Kullanıcı, "Yazı ayarları" düğmesiyle açılan bir
 *    panelden altyazının font boyutunu, ekrandaki konumunu (üst/alt), arka
 *    plan opaklığını ve kenar (safe-area) boşluğunu kumandayla (D-pad +
 *    "seç") adım adım ayarlayabilir ([CaptionDisplaySettings], saf Kotlin —
 *    core-model); seçimler [CaptionDisplayPrefs] ile `SharedPreferences`'a
 *    kalıcı olarak kaydedilir.
 * 10. (G029 kimlik kalıcılığı, 2026-11-09) TLS kimliği artık
 *    [TvTlsIdentity.generate] (her başlatmada YENİ) ile değil,
 *    [PersistentTvIdentity.loadOrCreate] ile elde edilir — bu, Android'in
 *    `AndroidKeyStore`'unda KALICI bir anahtar çifti/sertifika kullanır,
 *    böylece TV'nin fingerprint'i UYGULAMA YENİDEN BAŞLATILSA BİLE DEĞİŞMEZ
 *    ve "bu TV'yi hatırla" (G029, `app-mobile`'daki [TrustedTvStore])
 *    GERÇEK güvenlik değeri taşır (bkz. [PersistentTvIdentity] dokümantasyonu
 *    — bu kum havuzunda gerçek bir AndroidKeyStore çağrısı DENENEMEDİ, bu
 *    yüzden [PersistentTvIdentity] herhangi bir hatada sessizce geçici
 *    kimliğe düşer).
 *
 * NOT (2026-11-10, kullanıcı geri bildirimi — "çalışma mantığı Game
 * Caption'a benzemiyor, daha yabancı/anlaşılmaz"): #1-3'teki TvView/
 * TvInputManager HDMI-tuning denemesi, her zaman #4-#9'daki ağ-üzerinden
 * çeviri-overlay'i ile AYNI ANDA ve VARSAYILAN olarak çalışıyordu — ekran
 * hem canlı Xbox görüntüsünü kendi üstüne tune etmeye çalışıyor hem de
 * üstüne bir sürü teknik/debug satırı (Durum, Algılanan N giriş, NSD,
 * canlı saat) basıyordu. Gerçek Game Caption'da kullanıcı yalnızca iki şey
 * görür: (a) henüz bağlı değilken basit bir eşleştirme ekranı (QR+kod),
 * (b) bağlıyken sade bir altyazı. HDMI-tuning (#1-3) zaten yalnızca
 * G002/G003'ün donanım-kanıt denemesidir, nihai ürün deneyiminin bir
 * parçası değildir — TV'nin kendi native HDMI girişi zaten harici olarak
 * Xbox'a geçirilir, bu uygulamanın görüntüyü kendi üstüne çekmesine hiç
 * gerek yoktur. Bu yüzden artık:
 * - Varsayılan ekran SADE: bağlı değilken büyük QR+kod+kısa talimat;
 *   bağlıyken yalnızca çevrilen altyazı. TvView/PassthroughTuner
 *   VARSAYILAN olarak hiç başlatılmaz/tune edilmez.
 * - Sağ üstteki "Gelişmiş / Test modu" anahtarı açıldığında (yalnızca
 *   G002/G003 donanım testi yapan kişi için) TvView devreye girer VE tüm
 *   eski debug satırları (Durum/Giriş, Algılanan girişler, NSD, canlı
 *   saat, "Sonraki HDMI girişine geç") tekrar görünür. Bu, G030/P0 test
 *   rehberinde (docs/testing/P0-DONANIM-TEST-REHBERI.md) tarif edilen
 *   adımların hâlâ yapılabilmesi için gereklidir — hiçbir işlevsellik
 *   KALDIRILMADI, yalnızca varsayılan görünümden çıkarıldı.
 *
 * NOT (dürüstlük ilkesi): #1-3 gerçek Profilo TV + Xbox donanımında HENÜZ
 * denenmedi (bkz. docs/compatibility/hardware.md). #4-#5'teki TCP sunucusu
 * ve eşleştirme mantığı gerçek loopback soket testleriyle doğrulandı
 * (core-transport + core-pairing modülleri) ama gerçek bir telefon ile
 * gerçek bir TV arasında, gerçek bir Wi-Fi ağında HENÜZ denenmedi. #6'daki
 * QR'ın gerçek bir telefon kamerasıyla, gerçek bir TV ekranından
 * (parlaklık, açı, mesafe) okunabilirliği de HENÜZ denenmedi — yalnızca
 * QR üretiminin zxing ile birim test edilmiş doğruluğu kanıtlandı
 * (core-pairing). #8'deki gerçek NSD/mDNS reklamı/keşfi de gerçek bir ağda
 * HİÇ denenmedi — yalnızca TXT kayıt kodlama/kod çözme mantığı birim test
 * edildi (core-transport), NsdManager'ın kendisi burada hiç çalıştırılmadı.
 * #9'daki ayar paneli de gerçek bir TV kumandasıyla (D-pad fokus/gezinme)
 * HİÇ denenmedi — yalnızca [CaptionDisplaySettings]'in kelepçeleme (clamp)
 * mantığı core-model'de birim test edildi. #10'daki [PersistentTvIdentity]
 * — gerçek bir Android cihazda/`AndroidKeyStore`'da HİÇ çalıştırılmadı/
 * denenmedi (bu kum havuzunda Android SDK/cihaz yok); yalnızca Android'in
 * resmi, belgeli `KeyGenParameterSpec` davranışına dayanır ve herhangi bir
 * hatada geçici kimliğe güvenle düşer.
 *
 * 2026-11-13 — eksikler ve telefon↔TV senkronizasyonu (protokol v2):
 * - TV artık telefonun bağlı olup olmadığını biliyor (TvEventServer
 *   `onPrimaryClientChanged`). Önceden ilk altyazı geldikten sonra QR bir
 *   daha hiç görünmüyordu; telefon gidince TV'de eski altyazı kalıyor ve
 *   yeniden eşleştirmenin yolu kalmıyordu. Artık telefon kopunca altyazı
 *   kaldırılır ve QR yeni bir kodla geri gelir.
 * - Eşleştirme kodu kullanılınca/süresi dolunca kendiliğinden yenilenir
 *   (önceden ekranda kullanılmış/süresi geçmiş kod kalıyordu).
 * - Telefonda altyazı ekrandan kalkınca TV'de de kalkar (`CTL:CLEAR`).
 * - Telefon nabız gönderir; TV ~8 sn hiçbir şey alamazsa bağlantıyı ölü
 *   sayar. Telefon kopunca TV'nin verdiği yeniden bağlanma jetonuyla
 *   kendiliğinden geri bağlanır (yeni QR gerekmez) — bkz.
 *   ResumeTokenRegistry.
 * - Ekran açık tutulur (FLAG_KEEP_SCREEN_ON): kumandaya dokunulmadan
 *   uzun süre altyazı gösterirken TV ekran koruyucuya geçmesin.
 * - OYUN GÖRÜNTÜSÜ: 2026-11-10'da HDMI görüntüsü yalnız Test moduna
 *   alınmıştı ve "TV'nin kendi girişi zaten Xbox'a geçirilir, uygulamanın
 *   görüntüyü çekmesine gerek yok" diye gerekçelendirilmişti. Bu gerekçe
 *   YANLIŞTI: TV kendi HDMI girişine geçince bu uygulama arka plana düşer
 *   ve altyazı görünmez. Artık telefon bağlıyken, TV'de bir HDMI geçiş
 *   girişi varsa oyun görüntüsü bu uygulamanın içinde altyazının ARKASINDA
 *   gösterilir (kullanıcı kapatabilir, hangi HDMI girişi olduğunu
 *   seçebilir). Test modundaki teknik satırlar yine yalnız Test modunda.
 *
 * NOT (dürüstlük ilkesi): Bu maddelerin hiçbiri gerçek bir TV'de denenmedi.
 * Protokol kısmı (temizle/nabız/jeton) gerçek loopback TLS soketleriyle
 * test edildi (core-transport TvSyncProtocolTest); HDMI görüntüsünün
 * gerçekten açılıp açılmadığı TV üreticisine bağlıdır ve hâlâ G002/G003
 * donanım testini bekler.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Uzun süre kumandaya dokunulmadan altyazı izlenirken TV ekran
        // koruyucuya/uykuya geçmesin.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                TvProbeScreen()
            }
        }
    }
}

@Composable
private fun TvProbeScreen() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var discoveredInputs by remember { mutableStateOf<List<TvInputSummary>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var selectedInput by remember { mutableStateOf<TvInputSummary?>(null) }
    var tuneStatus by remember { mutableStateOf(TuneStatus.IDLE) }
    var tuner by remember { mutableStateOf<PassthroughTuner?>(null) }
    var tunedInputId by remember { mutableStateOf<String?>(null) }
    var nowText by remember { mutableStateOf("") }
    var receivedCue by remember { mutableStateOf<CueEvent?>(null) }
    var serverPort by remember { mutableStateOf<Int?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val localIp = remember { LocalIpAddress.find() }

    // v2 senkronizasyon: telefon şu an bağlı mı, az önce mi koptu.
    var phoneConnected by remember { mutableStateOf(false) }
    var phoneConnectionLost by remember { mutableStateOf(false) }

    // G023: eşleştirme kodu kaydı — tek bir TvEventServer örneğiyle birlikte
    // yaşar (bkz. aşağıdaki DisposableEffect).
    val pairingRegistry = remember { PairingRegistry() }
    var currentToken by remember { mutableStateOf<PairingToken?>(null) }

    // v2: kopan telefonun yeni QR okutmadan geri bağlanabilmesi için.
    val resumeTokens = remember { ResumeTokenRegistry() }

    // G024/G029: TLS kimliği — kalıcı (bkz. PersistentTvIdentity).
    // 2026-11-14: Artık ARKA PLANDA yükleniyor. Gerekçe: gerçek bir TV'de
    // açılışta 263 kare atlandığı (~4,4 sn donma) ölçüldü; RSA anahtar
    // üretimi + TLS öz-testi ana iş parçacığında yapılıyordu. Kimlik hazır
    // olana kadar sunucu başlatılmaz ve ekranda "Hazırlanıyor" yazar.
    var tlsIdentity by remember { mutableStateOf<TvTlsIdentity?>(null) }
    LaunchedEffect(Unit) {
        tlsIdentity = withContext(Dispatchers.IO) { PersistentTvIdentity.loadOrCreate(context) }
    }
    val identityFingerprint = tlsIdentity?.fingerprintSha256Hex

    // G025: NSD/mDNS reklamcısı — kod her değiştiğinde yeniden kaydedilir.
    val nsdAdvertiser = remember { TvNsdAdvertiser(context) }
    var nsdServiceName by remember { mutableStateOf<String?>(null) }

    // G027: altyazı görüntü ayarları (kalıcı).
    var displaySettings by remember { mutableStateOf(CaptionDisplayPrefs.load(context)) }
    var showDisplaySettingsPanel by remember { mutableStateOf(false) }
    fun updateDisplaySettings(transform: (CaptionDisplaySettings) -> CaptionDisplaySettings) {
        val updated = transform(displaySettings)
        displaySettings = updated
        CaptionDisplayPrefs.save(context, updated)
    }

    // Oyun görüntüsü (HDMI, telefon bağlıyken altyazının arkasında).
    var gameViewEnabled by remember { mutableStateOf(GameViewPrefs.isEnabled(context)) }

    // (2026-11-10) Teknik/debug satırları yalnız Test modunda.
    var testModeEnabled by remember { mutableStateOf(false) }

    DisposableEffect(tlsIdentity) {
        val identity = tlsIdentity ?: return@DisposableEffect onDispose { }
        currentToken = pairingRegistry.issue()
        val server = TvEventServer(
            tlsIdentity = identity,
            validatePairingCode = { code -> pairingRegistry.validateAndConsume(code) == PairingResult.VALID },
            validateResumeToken = resumeTokens::validateAndConsume,
            issueResumeToken = resumeTokens::issue,
            // Sunucu kendi iş parçacıklarından çağırır; Compose durumu ana
            // iş parçacığında güncellenir.
            onClear = { mainHandler.post { receivedCue = null } },
            onPrimaryClientChanged = { connected ->
                mainHandler.post {
                    phoneConnected = connected
                    if (connected) {
                        phoneConnectionLost = false
                    } else {
                        // Telefon gitti: eski altyazıyı kaldır, kullanılmış
                        // kodun yerine yenisini üret ve QR'ı geri getir.
                        receivedCue = null
                        phoneConnectionLost = true
                        currentToken = pairingRegistry.issue()
                    }
                }
            },
        ) { cue -> mainHandler.post { receivedCue = cue } }
        server.start()
        serverPort = server.boundPort
        onDispose {
            server.stop()
            nsdAdvertiser.stop()
        }
    }

    // G025: eşleştirme kodu her değiştiğinde NSD reklamını yenile.
    LaunchedEffect(currentToken, serverPort, identityFingerprint) {
        val port = serverPort
        val token = currentToken
        val fingerprint = identityFingerprint
        if (port != null && token != null && fingerprint != null) {
            nsdAdvertiser.restart(
                port = port,
                attributes = TvNsdAttributes(
                    pairingCode = token.value,
                    fingerprintSha256 = fingerprint,
                    protocolVersion = TvCapabilities.CURRENT.protocolVersion,
                ),
                onServiceNameChanged = { name -> nsdServiceName = name },
            )
        }
    }

    // Saniyelik döngü: saat + kod geri sayımı + süresi dolan kodun
    // kendiliğinden yenilenmesi (telefon bağlı değilken).
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            nowMillis = System.currentTimeMillis()
            nowText = fmt.format(Date(nowMillis))
            val token = currentToken
            if (!phoneConnected && token != null && token.isExpiredAt(nowMillis)) {
                currentToken = pairingRegistry.issue()
            }
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        // Arka planda: gerçek TV'de 9 giriş tarandı ve bu ana iş parçacığını
        // bloke ediyordu (bkz. yukarıdaki donma notu).
        val result = withContext(Dispatchers.IO) { TvInputBridge.discoverInputs(context) }
        discoveredInputs = result.inputs
        discoveryError = result.error
        val savedInputId = GameViewPrefs.inputId(context)
        selectedInput = result.inputs.firstOrNull { it.inputId == savedInputId && it.isHdmi && it.isPassthrough }
            ?: selectPreferredHdmiPassthroughInput(result.inputs)
    }

    val hdmiInputs = discoveredInputs.filter { it.isHdmi && it.isPassthrough }

    // Sinyalsiz HDMI girişini otomatik atla (2026-11-14, gerçek cihaz):
    // TV'de 4 HDMI girişi bulundu, uygulama HDMI 4'ü seçti ve orada sinyal
    // yoktu (onVideoUnavailable reason=2) — kullanıcı elle "Giriş" düğmesine
    // basana kadar siyah ekran görüyordu. Artık sinyal gelmeyen girişten
    // sıradakine kendiliğinden geçilir; liste bir kez dolaşılır, sonsuz
    // döngüye girilmez. Kullanıcı elle bir giriş seçerse otomatik geçiş
    // durur (seçimine saygı).
    var autoScanTriedInputIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var userPickedInput by remember { mutableStateOf(false) }

    LaunchedEffect(tuneStatus, selectedInput, userPickedInput) {
        val current = selectedInput ?: return@LaunchedEffect
        if (userPickedInput || hdmiInputs.size < 2) return@LaunchedEffect
        if (tuneStatus != TuneStatus.VIDEO_UNAVAILABLE && tuneStatus != TuneStatus.CONNECTION_FAILED) {
            return@LaunchedEffect
        }
        // Sinyal gelmesi için kısa bir şans ver (HDMI el sıkışması zaman alır).
        delay(AUTO_SCAN_SETTLE_MILLIS)
        if (tuneStatus != TuneStatus.VIDEO_UNAVAILABLE && tuneStatus != TuneStatus.CONNECTION_FAILED) {
            return@LaunchedEffect
        }
        val tried = autoScanTriedInputIds + current.inputId
        val next = hdmiInputs.firstOrNull { it.inputId !in tried } ?: return@LaunchedEffect
        autoScanTriedInputIds = tried
        selectedInput = next
        GameViewPrefs.setInputId(context, next.inputId)
    }
    val showGameView = selectedInput != null && (testModeEnabled || (phoneConnected && gameViewEnabled))

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (showGameView) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TvView(ctx).also { tvView ->
                        tuner = PassthroughTuner(tvView) { status -> mainHandler.post { tuneStatus = status } }
                        tunedInputId = null
                        tuneStatus = TuneStatus.IDLE
                    }
                },
                update = {
                    // Seçili giriş değiştiyse (kullanıcı "Giriş" düğmesine bastı) yeniden tune et.
                    val input = selectedInput
                    if (input != null && input.inputId != tunedInputId) {
                        tunedInputId = input.inputId
                        tuner?.tuneTo(input.inputId)
                    }
                },
                onRelease = {
                    tuner?.release()
                    tuner = null
                    tunedInputId = null
                    tuneStatus = TuneStatus.IDLE
                },
            )
        }

        // Sol üst: telefon bağlıyken küçük, sade bir durum göstergesi.
        if (phoneConnected) {
            Text(
                text = "● Telefon bağlı",
                color = Color(0xFF7CD992),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Sağ üst: göze batmayan kontroller.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            if (hdmiInputs.isNotEmpty()) {
                TextButton(onClick = {
                    gameViewEnabled = !gameViewEnabled
                    GameViewPrefs.setEnabled(context, gameViewEnabled)
                }) {
                    Text(if (gameViewEnabled) "Oyun görüntüsü: Açık" else "Oyun görüntüsü: Kapalı")
                }
                if (hdmiInputs.size > 1) {
                    TextButton(onClick = {
                        val currentIndex = hdmiInputs.indexOf(selectedInput)
                        val next = hdmiInputs[(currentIndex + 1) % hdmiInputs.size]
                        selectedInput = next
                        userPickedInput = true
                        GameViewPrefs.setInputId(context, next.inputId)
                    }) {
                        Text("Giriş: ${selectedInput?.label ?: selectedInput?.inputId ?: "?"}")
                    }
                }
            }
            TextButton(onClick = { showDisplaySettingsPanel = !showDisplaySettingsPanel }) {
                Text(if (showDisplaySettingsPanel) "Yazı ayarlarını gizle" else "Yazı ayarları")
            }
            TextButton(onClick = { testModeEnabled = !testModeEnabled }) {
                Text(
                    if (testModeEnabled) "Test modu: AÇIK" else "Gelişmiş / Test modu",
                    color = if (testModeEnabled) Color(0xFFFFD54F) else Color.Gray,
                )
            }
        }

        // G027: konum (üst/alt), arka plan opaklığı ve kenar boşluğu kullanıcı seçimine göre.
        val overlayAlignment = when (displaySettings.position) {
            CaptionVerticalPosition.BOTTOM -> Alignment.BottomCenter
            CaptionVerticalPosition.TOP -> Alignment.TopCenter
        }
        val liveCue = receivedCue
        val showPairing = !phoneConnected
        val hasOverlayContent = liveCue != null || showPairing || showDisplaySettingsPanel || testModeEnabled ||
            (phoneConnected && showGameView && tuneStatus.isFailure())
        if (hasOverlayContent) {
            Column(
                modifier = Modifier
                    .align(overlayAlignment)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = if (showPairing) 1f else displaySettings.backgroundOpacity))
                    .padding(displaySettings.safeAreaMarginDp.dp),
            ) {
                val captionFontSize = (20f * displaySettings.fontScale).sp
                val token = currentToken

                if (liveCue != null) {
                    // BAĞLI mod: yalnızca çeviri (+ küçük orijinal).
                    Text(text = liveCue.translatedText, color = Color.White, fontSize = captionFontSize)
                    if (liveCue.originalText.isNotBlank()) {
                        Text(
                            text = liveCue.originalText,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = captionFontSize * 0.75f,
                        )
                    }
                } else if (showPairing && !testModeEnabled) {
                    Text(
                        text = "GAMLETSOL'a bağlanın",
                        color = Color.White,
                        fontSize = (28f * displaySettings.fontScale).sp,
                    )
                    if (phoneConnectionLost) {
                        Text(
                            text = "Telefon bağlantısı koptu. Telefon birkaç saniye içinde kendiliğinden " +
                                "yeniden bağlanmayı dener; olmazsa aşağıdaki QR'ı yeniden okutun.",
                            color = Color(0xFFFFB74D),
                            fontSize = captionFontSize,
                        )
                    }
                    if (tlsIdentity == null) {
                        Text(
                            text = "Hazırlanıyor… (güvenli bağlantı kimliği kuruluyor)",
                            color = Color(0xFFFFD54F),
                            fontSize = captionFontSize,
                        )
                    }
                    Text(
                        text = "Telefonunuzda GAMLETSOL'u açın → menü (⋮) → " +
                            "\"TV'ye Yansıt\" → \"QR ile tara\"",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = captionFontSize,
                    )
                } else if (testModeEnabled && liveCue == null) {
                    // Test modu, altyazı yok: sabit test yazısı (G003 kanıtı).
                    Text(
                        text = stringResource(R.string.tv_test_overlay_text),
                        color = Color.White,
                    )
                }

                if (phoneConnected && showGameView && tuneStatus.isFailure() && !testModeEnabled) {
                    Text(
                        text = "Oyun görüntüsü bu TV'de uygulama içinde açılamadı. Doğru HDMI girişini seçin " +
                            "ya da \"Oyun görüntüsü\"nü kapatın.",
                        color = Color(0xFFFFB74D),
                        fontSize = captionFontSize * 0.8f,
                    )
                }

                if (token != null && showPairing) {
                    // Eşleştirme kodu/QR — telefon bağlı değilken her zaman görünür.
                    val remainingSeconds = ((token.issuedAtMillis + token.ttlMillis - nowMillis) / 1000)
                        .coerceAtLeast(0)
                    Text(
                        text = "Eşleştirme kodu: ${token.value}  (${remainingSeconds}s — süresi dolunca kendiliğinden yenilenir)",
                        color = Color(0xFFFFD54F),
                        fontSize = (captionFontSize.value * 1.3f).sp,
                    )
                    val ip = localIp
                    val port = serverPort
                    val fingerprint = identityFingerprint
                    if (ip != null && port != null && fingerprint != null) {
                        // QR, IP/port/kod + TLS fingerprint'i taşır (G023/G024).
                        // Kodlama hatasında QR gösterilmez; kod metni zaten yukarıda.
                        val payload = PairingQrPayload(
                            host = ip,
                            port = port,
                            code = token.value,
                            fingerprintSha256 = fingerprint,
                        )
                        val matrix = runCatching { QrMatrixEncoder.encode(payload.encode()) }.getOrNull()
                        if (matrix != null) {
                            QrCodeView(matrix = matrix, modifier = Modifier.size(220.dp))
                        }
                    }
                    Button(onClick = { currentToken = pairingRegistry.issue() }) {
                        Text("Yeni eşleştirme kodu oluştur")
                    }
                }

                // G027: yazı yerleşimi/okunabilirlik ayar paneli (kumandayla).
                if (showDisplaySettingsPanel) {
                    Text(
                        text = "Font: %.1fx · Konum: %s · Arka plan: %%%d · Kenar: %ddp".format(
                            displaySettings.fontScale,
                            if (displaySettings.position == CaptionVerticalPosition.BOTTOM) "Alt" else "Üst",
                            (displaySettings.backgroundOpacity * 100).toInt(),
                            displaySettings.safeAreaMarginDp,
                        ),
                        color = Color(0xFFFFD54F),
                    )
                    Row {
                        Button(onClick = { updateDisplaySettings { it.withFontScaleDelta(-1) } }) { Text("Font -") }
                        Button(onClick = { updateDisplaySettings { it.withFontScaleDelta(1) } }) { Text("Font +") }
                    }
                    Row {
                        Button(onClick = { updateDisplaySettings { it.withTogglePosition() } }) { Text("Konum değiştir") }
                    }
                    Row {
                        Button(onClick = { updateDisplaySettings { it.withBackgroundOpacityDelta(-1) } }) { Text("Arka plan -") }
                        Button(onClick = { updateDisplaySettings { it.withBackgroundOpacityDelta(1) } }) { Text("Arka plan +") }
                    }
                    Row {
                        Button(onClick = { updateDisplaySettings { it.withSafeAreaMarginDelta(-1) } }) { Text("Kenar -") }
                        Button(onClick = { updateDisplaySettings { it.withSafeAreaMarginDelta(1) } }) { Text("Kenar +") }
                    }
                    Button(onClick = { updateDisplaySettings { it.reset() } }) {
                        Text("Varsayılanlara dön")
                    }
                }

                // Teknik satırlar — yalnız Test modunda (bkz.
                // docs/testing/P0-DONANIM-TEST-REHBERI.md).
                if (testModeEnabled) {
                    Text(text = "Canlı saat (donmuş değil): $nowText", color = Color(0xFFFF6D10))
                    Text(
                        text = "Telefon: " + if (phoneConnected) "bağlı" else "bağlı değil",
                        color = Color.White,
                    )
                    Text(
                        text = "Telefondan bağlan (manuel): ${localIp ?: "IP bulunamadı"}:${serverPort ?: "…"}",
                        color = Color(0xFF4DD0E1),
                    )
                    Text(
                        text = "Otomatik keşif (NSD): " +
                            (nsdServiceName?.let { "\"$it\" olarak yayında" }
                                ?: "yayında değil (devre dışı/engelli olabilir — QR/manuel giriş hâlâ çalışır)"),
                        color = Color.Gray,
                    )
                    Text(
                        text = "Durum: ${tuneStatus.name} · Giriş: ${selectedInput?.inputId ?: "bulunamadı"}",
                        color = Color.White,
                    )
                    if (discoveryError != null) {
                        Text(text = "Hata: $discoveryError", color = Color(0xFFE5484D))
                    }
                    Text(
                        text = "Algılanan ${discoveredInputs.size} giriş: " +
                            discoveredInputs.joinToString { "${it.inputId}(hdmi=${it.isHdmi},pt=${it.isPassthrough})" },
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

/** Sinyalsiz girişten sıradakine geçmeden önce beklenen süre. */
private const val AUTO_SCAN_SETTLE_MILLIS = 2_500L

private fun TuneStatus.isFailure(): Boolean =
    this == TuneStatus.CONNECTION_FAILED || this == TuneStatus.VIDEO_UNAVAILABLE || this == TuneStatus.DISCONNECTED
