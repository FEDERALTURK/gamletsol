package com.federalturk.gamletsol.ui

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.CaptionViewModel
import com.federalturk.gamletsol.PipelineStatus
import com.federalturk.gamletsol.TvConnectionState
import androidx.camera.view.PreviewView

/**
 * NOT (geri bildirim, 2026-11-09): İlk beta sürümünde izin verildikten
 * sonra ekranda kamera önizlemesi dışında HİÇBİR şey yoktu — ne başlık, ne
 * durum bilgisi, ne de bir menü vardı. Sonra bir bilgi (i) düğmesi
 * eklendi; bu sürümde kullanıcı "ilk açılışta menüler, kurulum talimatları
 * ve diğer bilgiler olsun" diye tekrar istedi, bu yüzden:
 * - İlk açılışta ayrı bir [OnboardingScreen] akışı gösteriliyor
 *   (MainActivity'de yönlendiriliyor, bkz. OnboardingPrefs).
 * - Ana ekrandaki tek "bilgi" düğmesi, birden çok öğeli gerçek bir
 *   AÇILIR MENÜYE (overflow) dönüştürüldü: Kurulum Talimatları, Gizlilik,
 *   Tanıtımı Yeniden Göster.
 *
 * NOT (2026-11-09, kullanıcı netleştirmesi): Kullanıcının asıl istediği
 * özellik telefon↔TV bağlantısı kurup çeviriyi TV'ye yansıtmaktı — bu
 * yüzden menüye bir "TV'ye Yansıt" öğesi eklendi (bkz. [TvConnectDialog]),
 * P3/G022-G030'un ilk/MVP sürümü (core-transport modülü).
 */
@Composable
fun CaptionScreen(
    viewModel: CaptionViewModel,
    onRequestCameraPermission: () -> Unit,
    onShowOnboardingAgain: () -> Unit,
) {
    val granted by viewModel.cameraPermissionGranted.collectAsState()
    val cue by viewModel.subtitleSink.activeCue.collectAsState()
    val status by viewModel.pipelineStatus.collectAsState()
    val tvConnectionState by viewModel.tvConnectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (granted) {
            CameraPreview(viewModel = viewModel)
        } else {
            PermissionRequestContent(onRequestCameraPermission = onRequestCameraPermission)
        }

        TopBar(
            onShowOnboardingAgain = onShowOnboardingAgain,
            tvConnectionState = tvConnectionState,
            onConnectToTv = viewModel::connectToTv,
            onDisconnectFromTv = viewModel::disconnectFromTv,
        )

        val translated = cue?.translatedText
        if (granted && !translated.isNullOrBlank()) {
            SubtitleOverlay(original = cue?.originalText, translated = translated)
        } else if (granted) {
            StatusChip(status = status)
        }
    }
}

@Composable
private fun BoxScope.TopBar(
    onShowOnboardingAgain: () -> Unit,
    tvConnectionState: TvConnectionState,
    onConnectToTv: (String, Int, String) -> Unit,
    onDisconnectFromTv: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }

    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GAMLETSOL",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menü", tint = Color.White)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (tvConnectionState == TvConnectionState.CONNECTED) {
                                "TV bağlantısı (bağlı ✓)"
                            } else {
                                "TV'ye Yansıt"
                            },
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.TV_CONNECT
                    },
                )
                DropdownMenuItem(
                    text = { Text("Kurulum talimatları") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.SETUP
                    },
                )
                DropdownMenuItem(
                    text = { Text("Gizlilik ve nasıl çalışır") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.PRIVACY
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tanıtımı yeniden göster") },
                    onClick = {
                        menuExpanded = false
                        onShowOnboardingAgain()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Hakkında / yol haritası durumu") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.ABOUT
                    },
                )
            }
        }
    }

    when (dialog) {
        MenuDialog.TV_CONNECT -> TvConnectDialog(
            connectionState = tvConnectionState,
            onConnect = onConnectToTv,
            onDisconnect = onDisconnectFromTv,
            onDismiss = { dialog = null },
        )
        MenuDialog.SETUP -> InfoDialog(
            title = "Kurulum talimatları",
            body = "1. Oyun/cihaz ayarlarından altyazıları (Subtitles/Captions) " +
                "İNGİLİZCE olarak açın.\n" +
                "2. Telefonu, altyazının göründüğü ekranı net ve sabit görecek " +
                "şekilde tutun — mümkünse bir stand/tripod kullanın.\n" +
                "3. Altyazı net göründüğünde çeviri birkaç kare içinde ekranın " +
                "altında belirir ve sesli okunur.\n" +
                "4. İlk açılışta çeviri modeli indirilir — buna Wi-Fi gerekir.",
            onDismiss = { dialog = null },
        )
        MenuDialog.PRIVACY -> InfoDialog(
            title = "Gizlilik ve nasıl çalışır",
            body = "Metin tanıma (OCR) ve çeviri tamamen CİHAZ ÜZERİNDE yapılır " +
                "(Google ML Kit). Kamera görüntüsü hiçbir zaman bir sunucuya " +
                "yüklenmez — yalnızca ekrandaki yazı okunur, ses dinlenmez.",
            onDismiss = { dialog = null },
        )
        MenuDialog.ABOUT -> InfoDialog(
            title = "Hakkında",
            body = "GAMLETSOL — BETA sürüm. Bu, temel kamera → OCR → çeviri → " +
                "altyazı/TTS akışının çalıştığı ilk sürümdür. Ayar menüsü, " +
                "oyun profilleri, TV'ye yansıtma ve konsol bağlantısı yol " +
                "haritasındaki sonraki fazlarda (bkz. proje deposu " +
                "docs/roadmap/) planlanmıştır; henüz eklenmedi.",
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class MenuDialog { TV_CONNECT, SETUP, PRIVACY, ABOUT }

/**
 * "TV'ye Yansıt" diyaloğu — kullanıcının app-tv ekranında gördüğü IP:port'u
 * ve eşleştirme kodunu (G023) elle girip bağlanmasını sağlar (bkz. roadmap
 * P3, MVP: NSD/otomatik keşif ve KAMERA İLE QR TARAMA HENÜZ yok — kod elle
 * girilir). Bağlı olduğunda bağlantıyı kesme seçeneği de burada gösterilir.
 */
@Composable
private fun TvConnectDialog(
    connectionState: TvConnectionState,
    onConnect: (String, Int, String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("48631") }
    var pairingCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TV'ye Yansıt (beta)") },
        text = {
            Column {
                Text(
                    "TV uygulamasının (app-tv) ekranında gösterilen IP adresini, " +
                        "port'u ve eşleştirme kodunu girin. Telefon ve TV AYNI " +
                        "Wi-Fi ağında olmalı. Eşleştirme kodu 60 saniye geçerlidir " +
                        "ve tek kullanımlıktır — süresi geçerse TV'de \"Yeni " +
                        "eşleştirme kodu oluştur\" düğmesine basın.\n\n" +
                        "NOT: Bu bağlantının KENDİSİ şifrelenmemiştir (beta) — " +
                        "kod yalnızca kimlik doğrulama sağlar; yalnızca ev " +
                        "içi/güvendiğiniz bir ağda kullanın.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("TV IP adresi (ör. 192.168.1.23)") },
                    enabled = connectionState != TvConnectionState.CONNECTING,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    enabled = connectionState != TvConnectionState.CONNECTING,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("Eşleştirme kodu (TV'de gösterilen)") },
                    enabled = connectionState != TvConnectionState.CONNECTING,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val statusText = when (connectionState) {
                    TvConnectionState.DISCONNECTED -> "Bağlı değil."
                    TvConnectionState.CONNECTING -> "Bağlanılıyor…"
                    TvConnectionState.CONNECTED -> "Bağlandı ✓ — çeviriler artık TV'ye de gönderiliyor."
                    TvConnectionState.FAILED -> "Bağlanılamadı — IP/port/eşleştirme kodunu ve aynı ağda olduğunuzu kontrol edin."
                }
                Text(statusText)
            }
        },
        confirmButton = {
            if (connectionState == TvConnectionState.CONNECTED) {
                Button(onClick = { onDisconnect() }) { Text("Bağlantıyı kes") }
            } else {
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (host.isNotBlank() && port != null && pairingCode.isNotBlank()) {
                            onConnect(host.trim(), port, pairingCode.trim())
                        }
                    },
                    enabled = connectionState != TvConnectionState.CONNECTING,
                ) { Text("Bağlan") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        },
    )
}

@Composable
private fun BoxScope.StatusChip(status: PipelineStatus) {
    val text = when (status) {
        PipelineStatus.STARTING -> "Başlatılıyor…"
        PipelineStatus.DOWNLOADING_TRANSLATION_MODEL -> "Çeviri modeli indiriliyor (yalnızca ilk sefer, Wi-Fi gerekir)…"
        PipelineStatus.MODEL_DOWNLOAD_FAILED -> "Çeviri modeli indirilemedi — Wi-Fi'ye bağlanıp uygulamayı yeniden açın"
        PipelineStatus.SCANNING -> "Kamerayı İngilizce altyazının göründüğü ekrana doğrultun — altyazı aranıyor…"
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Tamam") }
        },
    )
}

@Composable
private fun PermissionRequestContent(onRequestCameraPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "GAMLETSOL, ekrandaki İngilizce altyazıyı okuyabilmek için " +
                "kameranı kullanır. Görüntü hiçbir zaman sunucuya yüklenmez.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRequestCameraPermission) {
            Text("Kamera iznini ver")
        }
    }
}

@Composable
private fun CameraPreview(viewModel: CaptionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    viewModel.bindCamera(
                        cameraProvider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        preview = preview,
                    )
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}

@Composable
private fun BoxScope.SubtitleOverlay(original: String?, translated: String) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(12.dp),
    ) {
        Text(
            text = translated,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (!original.isNullOrBlank()) {
            Text(
                text = original,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
