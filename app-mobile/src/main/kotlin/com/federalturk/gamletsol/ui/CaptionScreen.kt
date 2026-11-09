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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.camera.view.PreviewView

/**
 * NOT (geri bildirim, 2026-11-09): İlk beta sürümünde izin verildikten
 * sonra ekranda kamera önizlemesi dışında HİÇBİR şey yoktu — ne başlık, ne
 * durum bilgisi, ne de "nasıl kullanılır" açıklaması vardı; kullanıcı
 * "kamera açılıyor, başka hiçbir şey yok" diye bildirdi. Bu dosyaya
 * eklenen [TopBar] (başlık + bilgi düğmesi) ve [StatusChip] (model
 * indiriliyor/taranıyor/hata durumu) bunu gidermek için eklendi.
 */
@Composable
fun CaptionScreen(viewModel: CaptionViewModel, onRequestCameraPermission: () -> Unit) {
    val granted by viewModel.cameraPermissionGranted.collectAsState()
    val cue by viewModel.subtitleSink.activeCue.collectAsState()
    val status by viewModel.pipelineStatus.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (granted) {
            CameraPreview(viewModel = viewModel)
        } else {
            PermissionRequestContent(onRequestCameraPermission = onRequestCameraPermission)
        }

        TopBar(onInfoClick = { showInfoDialog = true })

        val translated = cue?.translatedText
        if (granted && !translated.isNullOrBlank()) {
            SubtitleOverlay(original = cue?.originalText, translated = translated)
        } else if (granted) {
            StatusChip(status = status)
        }
    }

    if (showInfoDialog) {
        InfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun BoxScope.TopBar(onInfoClick: () -> Unit) {
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
        IconButton(onClick = onInfoClick) {
            Icon(Icons.Filled.Info, contentDescription = "Nasıl çalışır / bilgi", tint = Color.White)
        }
    }
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
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GAMLETSOL nasıl çalışır?") },
        text = {
            Text(
                "Kamerayı, İngilizce altyazının göründüğü ekrana (TV/monitör) " +
                    "doğrultun. Uygulama metni cihaz üzerinde okur (OCR) ve " +
                    "Türkçeye çevirip altta gösterir, ayrıca sesli okur.\n\n" +
                    "Gizlilik: kamera görüntüsü hiçbir zaman bir sunucuya " +
                    "yüklenmez — çeviri tamamen cihaz üzerinde (ML Kit) yapılır.\n\n" +
                    "Bu bir BETA sürümdür: ayar menüsü, oyun profilleri ve " +
                    "kalite iyileştirmeleri henüz eklenmedi (bkz. proje yol " +
                    "haritası).",
            )
        },
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
