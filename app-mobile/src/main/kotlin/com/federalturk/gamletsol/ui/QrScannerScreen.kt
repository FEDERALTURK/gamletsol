package com.federalturk.gamletsol.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import android.view.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.core.pairing.PairingQrPayload
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * TV ekranındaki eşleştirme QR'ını (G023, `PairingQrPayload`) kamerayla
 * tarayan tam ekran Composable. [CaptionScreen] bunu, ana OCR
 * kamerası ([CameraPreview]) yerine geçici olarak gösterir — AYNI ANDA
 * iki CameraX bağlaması yapılmaz: bu ekran kendi `ProcessCameraProvider`
 * oturumunu `unbindAll()` ile sıfırlayıp YALNIZCA tarayıcı için bağlar; bu
 * ekrandan çıkıldığında [CaptionScreen] `CameraPreview`'ı YENİDEN
 * oluşturduğu için (composition'dan tamamen kaldırılıp eklendiği için)
 * OCR kamerası otomatik olarak yeniden bağlanır (bkz. `viewModel.bindCamera`
 * zaten her çağrıldığında `unbindAll()` + taze bağlama yapıyor).
 *
 * NOT (dürüstlük ilkesi): Bu ekranın gerçek bir TV ekranındaki QR'ı gerçek
 * bir telefon kamerasıyla okuyabildiği bu kum havuzunda HİÇ denenmedi
 * (kamera/gerçek donanım yok) — yalnızca [PairingQrPayload]'ın kodlama/
 * kod çözmesi birim testlerle doğrulandı (core-pairing). Düşük
 * ışık/parlaklık/açı gibi gerçek dünya koşulları bilinmiyor; bu yüzden
 * manuel giriş alanı KALDIRILMADI (bkz. [TvConnectDialog]).
 */
@Composable
fun QrScannerScreen(
    onResult: (PairingQrPayload) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorText by remember { mutableStateOf<String?>(null) }
    // İlk başarılı/geçerli taramadan SONRA analiz callback'inin devam
    // edip onResult'ı tekrar tekrar çağırmasını önlemek için — kamera her
    // kareyi analiz etmeye devam eder, ama ikinci bir eşleştirme isteği
    // göndermeyiz.
    var resolved by remember { mutableStateOf(false) }

    val view = LocalView.current
    // Yatay mod (2026-11-13): döndürmede use-case'lerin hedef dönüşünü
    // güncellemek için referanslar; ayrıca tarayıcı ve iş parçacığı artık
    // ekran kapanınca serbest bırakılıyor (önceden hiç kapatılmıyordu).
    val scannerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    var boundAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }

    DisplayRotationEffect { rotation ->
        boundPreview?.targetRotation = rotation
        boundAnalysis?.targetRotation = rotation
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val initialRotation = view.display?.rotation ?: Surface.ROTATION_0
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetRotation(initialRotation)
                            .build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(initialRotation)
                            .build()
                        boundPreview = preview
                        boundAnalysis = analysis
                        analysis.setAnalyzer(scannerExecutor) { imageProxy ->
                            @Suppress("UnsafeOptInUsageError")
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || resolved) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (!resolved) {
                                        val payload = barcodes
                                            .mapNotNull { it.rawValue }
                                            .firstNotNullOfOrNull { PairingQrPayload.decode(it) }
                                        if (payload != null) {
                                            resolved = true
                                            onResult(payload)
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // Tek bir kare analizinin başarısız olması normaldir
                                    // (bulanıklık, odak) — kullanıcıya hata GÖSTERMİYORUZ,
                                    // sonraki karede tekrar denenir.
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) {
                            errorText = "Kamera başlatılamadı: ${e.message}"
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )

        DisposableEffect(Unit) {
            onDispose {
                // Önce analizörü kaldır: aksi halde CameraX kapatılmış
                // iş parçacığına kare göndermeye çalışabilir.
                boundAnalysis?.clearAnalyzer()
                scanner.close()
                scannerExecutor.shutdown()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TV ekranındaki QR kodunu kameraya gösterin",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (errorText != null) {
                Text(text = errorText!!, color = Color(0xFFE5484D))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "QR görünmüyorsa veya taranamıyorsa, önceki ekrana dönüp " +
                    "IP/port/kodu elle girebilirsiniz.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onCancel) { Text("İptal / elle gir") }
        }
    }
}
