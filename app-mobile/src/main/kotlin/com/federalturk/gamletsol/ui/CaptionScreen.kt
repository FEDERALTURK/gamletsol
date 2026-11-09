package com.federalturk.gamletsol.ui

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.CaptionViewModel
import androidx.camera.view.PreviewView

@Composable
fun CaptionScreen(viewModel: CaptionViewModel, onRequestCameraPermission: () -> Unit) {
    val granted by viewModel.cameraPermissionGranted.collectAsState()
    val cue by viewModel.subtitleSink.activeCue.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (granted) {
            CameraPreview(viewModel = viewModel)
        } else {
            PermissionRequestContent(onRequestCameraPermission = onRequestCameraPermission)
        }

        val translated = cue?.translatedText
        if (granted && !translated.isNullOrBlank()) {
            SubtitleOverlay(original = cue?.originalText, translated = translated)
        }
    }
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
