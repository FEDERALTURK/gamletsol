package com.federalturk.gamletsol.tv

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.federalturk.gamletsol.core.pairing.QrMatrix

/**
 * [QrMatrix]'i (core-pairing, platformdan bağımsız) bir Compose [Canvas]
 * üzerine siyah/beyaz kareler olarak çizer. Bu dosya BİLEREK çok basit —
 * zxing-android-embedded gibi ek bir Android kütüphanesine gerek kalmadan,
 * yalnızca temel çizim API'leriyle QR'ı render eder.
 *
 * NOT (dürüstlük ilkesi): Bu Composable'ın gerçek bir QR tarayıcı
 * uygulamasıyla (veya app-mobile'daki [com.federalturk.gamletsol.ui]
 * paketindeki tarayıcıyla) GERÇEKTEN taranabildiği bu kum havuzunda
 * doğrulanamadı (kamera/gerçek ekran yok) — yalnızca `QrMatrixEncoder`'ın
 * ürettiği matrisin zxing'in encode ettiği hücrelerle bire bir eşleştiği
 * birim testle doğrulandı (core-pairing). Gerçek bir TV ekranından gerçek
 * bir telefon kamerasıyla okunup okunamadığı kullanıcının kendi
 * donanımında denenmelidir.
 */
@Composable
fun QrCodeView(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellSize = size.minDimension / matrix.size
        // Beyaz zemin — kameranın kontrastı net görmesi için QR'ın
        // etrafında/arasında her zaman beyaz bırakılır (quiet zone zaten
        // zxing'in matrisine dahil, ama tuval tam kare olmayabilir).
        drawRect(color = Color.White, size = size)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}
