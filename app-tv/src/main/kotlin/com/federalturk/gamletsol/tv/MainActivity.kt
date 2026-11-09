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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.size
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

    // G024: TLS kimliği — uygulama bu ekran canlı olduğu sürece SABİTTİR
    // (yeniden başlatılana kadar aynı sertifika/fingerprint kullanılır).
    // NOT (dürüstlük ilkesi/MVP sınırı): KALICI DEĞİL — bkz. TvTlsIdentity
    // dokümantasyonu, G029 ("bu TV'yi hatırla") henüz bunu beklemiyor.
    val tlsIdentity = remember { TvTlsIdentity.generate() }

    // P3/G022-G030 (MVP): telefondan gelen cue'ları dinleyen TCP sunucusu +
    // G023'teki eşleştirme kodu doğrulaması + G024'teki TLS. Activity canlı
    // olduğu sürece çalışır, DisposableEffect ile kapatılır.
    // G025: NSD/mDNS reklamcısı — kod her değiştiğinde (bkz. aşağıdaki
    // LaunchedEffect(currentToken, serverPort)) yeniden kaydedilir.
    val nsdAdvertiser = remember { TvNsdAdvertiser(context) }
    var nsdServiceName by remember { mutableStateOf<String?>(null) }

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

        // Overlay — gerçek amaç bu kutunun TvView'ın gösterdiği görüntünün
        // ÜSTÜNDE görünüp görünmediğini gözle doğrulamak (G003).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(24.dp),
        ) {
            val liveCue = receivedCue
            if (liveCue != null) {
                // Telefondan gerçek bir cue geldiyse, sabit test yazısı
                // yerine onu göster — gerçek P3 davranışı.
                Text(text = liveCue.translatedText, color = Color.White)
                if (liveCue.originalText.isNotBlank()) {
                    Text(text = liveCue.originalText, color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                Text(
                    text = stringResource(R.string.tv_test_overlay_text),
                    color = Color.White,
                )
            }
            Text(text = "Canlı saat (donmuş değil): $nowText", color = Color(0xFFFF6D10))
            Text(
                text = "Telefondan bağlan: ${localIp ?: "IP bulunamadı"}:${serverPort ?: "…"}",
                color = Color(0xFF4DD0E1),
            )
            Text(
                text = "Otomatik keşif (NSD): " +
                    (nsdServiceName?.let { "\"$it\" olarak yayında" }
                        ?: "yayında değil (devre dışı/engelli olabilir — QR/manuel giriş hâlâ çalışır)"),
                color = Color.Gray,
            )

            val token = currentToken
            if (token != null) {
                val remainingSeconds = ((token.issuedAtMillis + token.ttlMillis - nowMillis) / 1000)
                    .coerceAtLeast(0)
                if (remainingSeconds > 0) {
                    Text(
                        text = "Eşleştirme kodu: ${token.value}  (${remainingSeconds}s)",
                        color = Color(0xFFFFD54F),
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
                            QrCodeView(matrix = matrix, modifier = Modifier.size(160.dp))
                        }
                    }
                } else {
                    Text(text = "Eşleştirme kodunun süresi doldu — yeni kod oluşturun", color = Color(0xFFE5484D))
                }
            }
            Button(onClick = { currentToken = pairingRegistry.issue() }) {
                Text("Yeni eşleştirme kodu oluştur")
            }
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
