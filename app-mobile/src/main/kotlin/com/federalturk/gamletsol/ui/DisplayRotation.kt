package com.federalturk.gamletsol.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Ekranın dönüşü (`Surface.ROTATION_*`) her değiştiğinde [onRotationChanged]'i
 * çağırır; ilk kompozisyonda da mevcut değerle bir kez çağırır.
 *
 * Neden gerekli (2026-11-13, yatay mod desteği): `MainActivity` döndürmede
 * yeniden oluşturulmaz (manifest'te `configChanges`), bu yüzden CameraX
 * use-case'lerinin hedef dönüşü kendiliğinden güncellenmez. Güncellenmezse
 * yatayda kamera karesi OCR'a yan yatmış gider ve hiçbir yazı okunmaz.
 * Ayrıca yataydan ters-yataya (180°) geçiş ekran boyutunu değiştirmediği için
 * bir yapılandırma değişikliği bile üretmez — onu ancak bir
 * [DisplayManager.DisplayListener] yakalar.
 */
@Composable
fun DisplayRotationEffect(onRotationChanged: (Int) -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val callback by rememberUpdatedState(onRotationChanged)

    DisposableEffect(view) {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        fun currentRotation(): Int = view.display?.rotation ?: Surface.ROTATION_0

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (view.display?.displayId == displayId) callback(currentRotation())
            }
        }
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        callback(currentRotation())

        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }
}
