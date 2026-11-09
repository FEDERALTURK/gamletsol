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
import com.federalturk.gamletsol.core.model.tv.TvInputSummary
import com.federalturk.gamletsol.core.model.tv.selectPreferredHdmiPassthroughInput
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * G001-G003 kanıt ekranı — bu uygulamanın TEK amacı budur:
 *
 * 1. Cihazdaki TV girişlerini listele (`TvInputBridge`, G002).
 * 2. Bulunan ilk HDMI passthrough girişine (varsa) `TvView` ile tune ol.
 * 3. Üstüne sabit, saati gösteren bir Türkçe test yazısı bindir (G003) —
 *    saat canlı güncellendiği için yazının "donmuş bir ekran görüntüsü"
 *    olmadığı, gerçekten üst üste render edildiği anlaşılır.
 *
 * Bu ekranın "çalıştığını" iddia etmiyoruz — kullanıcının gerçek
 * Profilo TV + Xbox donanımında deneyip `docs/compatibility/hardware.md`
 * G002/G003 bölümlerini doldurması gerekiyor.
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
            Text(
                text = stringResource(R.string.tv_test_overlay_text),
                color = Color.White,
            )
            Text(text = "Canlı saat (donmuş değil): $nowText", color = Color(0xFFFF6D10))
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
