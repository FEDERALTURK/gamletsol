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
import com.federalturk.gamletsol.core.model.tv.TvInputSummary
import com.federalturk.gamletsol.core.model.tv.selectPreferredHdmiPassthroughInput
import com.federalturk.gamletsol.core.transport.CueEvent
import com.federalturk.gamletsol.core.transport.TvEventServer
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
 *
 * NOT (dürüstlük ilkesi): #1-3 gerçek Profilo TV + Xbox donanımında HENÜZ
 * denenmedi (bkz. docs/compatibility/hardware.md). #4'teki TCP sunucusu
 * gerçek loopback soket testleriyle doğrulandı (core-transport modülü)
 * ama gerçek bir telefon ile gerçek bir TV arasında, gerçek bir Wi-Fi
 * ağında HENÜZ denenmedi — ve şifreleme/eşleştirme doğrulaması YOK.
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
    val localIp = remember { LocalIpAddress.find() }

    // P3/G022-G030 (MVP): telefondan gelen cue'ları dinleyen TCP sunucusu.
    // Activity canlı olduğu sürece çalışır, DisposableEffect ile kapatılır.
    DisposableEffect(Unit) {
        val server = TvEventServer { cue -> receivedCue = cue }
        server.start()
        serverPort = server.boundPort
        onDispose { server.stop() }
    }

    // Saat her saniye güncellenir — overlay'in canlı render edildiğini,
    // dondurulmuş bir ekran görüntüsü olmadığını kanıtlamak için.
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            nowText = fmt.format(Date())
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
