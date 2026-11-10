package com.federalturk.gamletsol.tv

import android.media.tv.TvView
import android.os.Bundle
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
import kotlinx.coroutines.delay
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
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    var discoveredInputs by remember { mutableStateOf<List<TvInputSummary>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var selectedInput by remember { mutableStateOf<TvInputSummary?>(null) }
    var tuneStatus by remember { mutableStateOf(TuneStatus.IDLE) }
    var tuner by remember { mutableStateOf<PassthroughTuner?>(null) }
    var nowText by remember { mutableStateOf("") }
    var receivedCue by remember { mutableStateOf<CueEvent?>(null) }
    var serverPort by remember { mutableStateOf<Int?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val localIp = remember { LocalIpAddress.find() }

    // G023: eşleştirme kodu kaydı — tek bir TvEventServer örneğiyle birlikte
    // yaşar (bkz. aşağıdaki DisposableEffect).
    val pairingRegistry = remember { PairingRegistry() }
    var currentToken by remember { mutableStateOf<PairingToken?>(null) }

    // G024/G029: TLS kimliği — artık AndroidKeyStore'da KALICI olarak
    // saklanıyor (bkz. PersistentTvIdentity dokümantasyonu). Uygulama
    // yeniden başlatılsa BİLE aynı sertifika/fingerprint kullanılır; bu
    // sayede G029'daki "bu TV'yi hatırla" özelliği GERÇEK güvenlik değeri
    // taşır. Herhangi bir AndroidKeyStore hatasında (bu kum havuzunda
    // doğrulanamayan bir durum) PersistentTvIdentity sessizce geçici
    // (ephemeral) bir kimliğe düşer — TV hiçbir zaman bu yüzden çökmez.
    val tlsIdentity = remember { PersistentTvIdentity.loadOrCreate() }

    // P3/G022-G030 (MVP): telefondan gelen cue'ları dinleyen TCP sunucusu +
    // G023'teki eşleştirme kodu doğrulaması + G024'teki TLS. Activity canlı
    // olduğu sürece çalışır, DisposableEffect ile kapatılır.
    // G025: NSD/mDNS reklamcısı — kod her değiştiğinde (bkz. aşağıdaki
    // LaunchedEffect(currentToken, serverPort)) yeniden kaydedilir.
    val nsdAdvertiser = remember { TvNsdAdvertiser(context) }
    var nsdServiceName by remember { mutableStateOf<String?>(null) }

    // G027: altyazı görüntü ayarları — ilk açılışta SharedPreferences'tan
    // yüklenir, her değişiklikte hemen kalıcı olarak kaydedilir (bkz.
    // [CaptionDisplayPrefs]).
    var displaySettings by remember { mutableStateOf(CaptionDisplayPrefs.load(context)) }
    var showDisplaySettingsPanel by remember { mutableStateOf(false) }
    fun updateDisplaySettings(transform: (CaptionDisplaySettings) -> CaptionDisplaySettings) {
        val updated = transform(displaySettings)
        displaySettings = updated
        CaptionDisplayPrefs.save(context, updated)
    }

    // (2026-11-10, kullanıcı geri bildirimi) Varsayılan ekran artık SADE —
    // TvView/HDMI-tuning ve tüm debug/teknik satırlar yalnızca bu anahtar
    // açıkken görünür. Bu, G002/G003/G030 donanım testlerini yapan kişi
    // için bir "Test modu"dur, normal kullanım için değildir.
    var testModeEnabled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        currentToken = pairingRegistry.issue()
        val server = TvEventServer(
            tlsIdentity = tlsIdentity,
            validatePairingCode = { code -> pairingRegistry.validateAndConsume(code) == PairingResult.VALID },
        ) { cue -> receivedCue = cue }
        server.start()
        serverPort = server.boundPort
        onDispose {
            server.stop()
            nsdAdvertiser.stop()
        }
    }

    // G025: eşleştirme kodu her değiştiğinde (ilk üretim veya "Yeni
    // eşleştirme kodu oluştur" düğmesi) NSD reklamını, yeni kod +
    // fingerprint'i taşıyacak şekilde yeniden başlat.
    LaunchedEffect(currentToken, serverPort) {
        val port = serverPort
        val token = currentToken
        if (port != null && token != null) {
            nsdAdvertiser.restart(
                port = port,
                attributes = TvNsdAttributes(
                    pairingCode = token.value,
                    fingerprintSha256 = tlsIdentity.fingerprintSha256Hex,
                    protocolVersion = TvCapabilities.CURRENT.protocolVersion,
                ),
                onServiceNameChanged = { name -> nsdServiceName = name },
            )
        }
    }

    // Saat her saniye güncellenir — overlay'in canlı render edildiğini,
    // dondurulmuş bir ekran görüntüsü olmadığını kanıtlamak için; aynı
    // döngü, eşleştirme kodunun geri sayımını da güncel tutar.
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            nowMillis = System.currentTimeMillis()
            nowText = fmt.format(Date(nowMillis))
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        val result = TvInputBridge.discoverInputs(context)
        discoveredInputs = result.inputs
        discoveryError = result.error
        selectedInput = selectPreferredHdmiPassthroughInput(result.inputs)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // (2026-11-10) TvView/HDMI-tuning artık YALNIZCA Test modu
        // açıkken kurulur/tune edilir — bkz. sınıf dokümantasyonundaki
        // kullanıcı geri bildirimi notu. Normal (sade) kullanımda bu
        // AndroidView hiç oluşturulmaz; ekran arka planı düz siyah kalır.
        if (testModeEnabled) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TvView(ctx)
                },
                update = { tvView ->
                    if (tuner == null) {
                        tuner = PassthroughTuner(tvView) { status -> tuneStatus = status }
                    }
                    val input = selectedInput
                    if (input != null && tuneStatus == TuneStatus.IDLE) {
                        tuner?.tuneTo(input.inputId)
                    }
                },
            )
        }

        // Sağ üst köşe: her zaman erişilebilir, göze batmayan küçük
        // kontroller (Test modu anahtarı + Yazı ayarları). Bunlar debug
        // satırlarının KENDİSİ değil, onları açıp kapatan düğmelerdir.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
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

        // G027: konum (üst/alt), arka plan opaklığı ve kenar (safe-area)
        // boşluğu artık kullanıcının [displaySettings] seçimine göre.
        val overlayAlignment = when (displaySettings.position) {
            CaptionVerticalPosition.BOTTOM -> Alignment.BottomCenter
            CaptionVerticalPosition.TOP -> Alignment.TopCenter
        }
        Column(
            modifier = Modifier
                .align(overlayAlignment)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = displaySettings.backgroundOpacity))
                .padding(displaySettings.safeAreaMarginDp.dp),
        ) {
            val liveCue = receivedCue
            val captionFontSize = (20f * displaySettings.fontScale).sp
            val token = currentToken

            if (liveCue != null) {
                // BAĞLI/SADE mod: telefondan gerçek bir cue geldi — yalnızca
                // çeviriyi göster, hiçbir debug/eşleştirme bilgisi yok.
                Text(text = liveCue.translatedText, color = Color.White, fontSize = captionFontSize)
                if (liveCue.originalText.isNotBlank()) {
                    Text(
                        text = liveCue.originalText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = captionFontSize,
                    )
                }
            } else if (!testModeEnabled) {
                // BAĞLANMA/SADE mod: henüz hiçbir cue gelmedi ve Test modu
                // kapalı — Game Caption'daki gibi tek bir basit talimat +
                // QR/kod, başka hiçbir teknik satır yok.
                Text(
                    text = "GAMLETSOL'a bağlanın",
                    color = Color.White,
                    fontSize = (28f * displaySettings.fontScale).sp,
                )
                Text(
                    text = "Telefonunuzda GAMLETSOL'u açın → menü (⋮) → " +
                        "\"TV'ye Yansıt\" → \"QR ile tara\"",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = captionFontSize,
                )
            } else {
                // Test modu, henüz cue gelmedi: eski sabit test yazısı
                // (G003'ün "donmuş ekran görüntüsü değil" kanıtı için).
                Text(
                    text = stringResource(R.string.tv_test_overlay_text),
                    color = Color.White,
                )
            }

            if (token != null && liveCue == null) {
                // Eşleştirme kodu/QR — bağlı değilken, Test modundan
                // BAĞIMSIZ olarak her zaman görünür (bu bir debug bilgisi
                // değil, ürünün ana işlevidir).
                val remainingSeconds = ((token.issuedAtMillis + token.ttlMillis - nowMillis) / 1000)
                    .coerceAtLeast(0)
                if (remainingSeconds > 0) {
                    Text(
                        text = "Eşleştirme kodu: ${token.value}  (${remainingSeconds}s)",
                        color = Color(0xFFFFD54F),
                        fontSize = (captionFontSize.value * 1.3f).sp,
                    )
                    val ip = localIp
                    val port = serverPort
                    if (ip != null && port != null) {
                        // G023 (QR kısmı): IP/port/kodu tek bir QR'a kodla —
                        // kullanıcı telefonundaki kamerayla tarayabilir, üç
                        // alanı elle yazmak zorunda kalmaz. Hata durumunda
                        // (örn. zxing bir kodlama istisnası fırlatırsa) QR
                        // sessizce gösterilmez — metin tabanlı alan/kod
                        // zaten yukarıda görünür durumda, kullanıcı elle
                        // girebilir (dürüstlük ilkesi: sahte/boş bir QR
                        // göstermek yerine hiç göstermemek).
                        val payload = PairingQrPayload(
                            host = ip,
                            port = port,
                            code = token.value,
                            fingerprintSha256 = tlsIdentity.fingerprintSha256Hex,
                        )
                        val matrix = runCatching { QrMatrixEncoder.encode(payload.encode()) }.getOrNull()
                        if (matrix != null) {
                            QrCodeView(matrix = matrix, modifier = Modifier.size(220.dp))
                        }
                    }
                } else {
                    Text(text = "Eşleştirme kodunun süresi doldu — yeni kod oluşturun", color = Color(0xFFE5484D))
                }
                Button(onClick = { currentToken = pairingRegistry.issue() }) {
                    Text("Yeni eşleştirme kodu oluştur")
                }
            }

            // G027: yazı yerleşimi/okunabilirlik ayar paneli — kumandayla
            // (D-pad yukarı/aşağı ile düğmeler arasında gezinip "seç" ile
            // tıklanır; Compose'un varsayılan fokus gezinmesi, bkz. sınıf
            // dokümantasyonundaki dürüstlük notu — gerçek kumandada HENÜZ
            // denenmedi).
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

            // (2026-11-10) Aşağıdaki TÜM satırlar yalnızca Test modu
            // açıkken görünür — bunlar G001-G003/G030 donanım testleri
            // için gereken teknik/debug bilgilerdir (bkz.
            // docs/testing/P0-DONANIM-TEST-REHBERI.md), normal kullanıcı
            // deneyiminin bir parçası DEĞİLDİR. Hiçbir satır silinmedi,
            // yalnızca bu koşulun arkasına taşındı.
            if (testModeEnabled) {
                Text(text = "Canlı saat (donmuş değil): $nowText", color = Color(0xFFFF6D10))
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
                if (discoveredInputs.count { it.isHdmi && it.isPassthrough } > 1) {
                    Button(onClick = {
                        val hdmiInputs = discoveredInputs.filter { it.isHdmi && it.isPassthrough }
                        val currentIndex = hdmiInputs.indexOf(selectedInput)
                        val next = hdmiInputs[(currentIndex + 1) % hdmiInputs.size]
                        selectedInput = next
                        tuneStatus = TuneStatus.IDLE
                    }) {
                        Text("Sonraki HDMI girişine geç")
                    }
                }
            }
        }
    }
}
