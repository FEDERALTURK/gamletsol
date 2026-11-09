package com.federalturk.gamletsol.core.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [PairingQrPayload]'ı (veya herhangi bir metni) bir [QrMatrix]'e kodlar —
 * zxing-core'un ürettiği `BitMatrix`'i bu modülün Android'siz [QrMatrix]
 * türüne çevirir. `app-tv`, bunu Compose `Canvas` ile piksellere çizer (bkz.
 * app-tv `QrCodeView.kt`) — bu sınıf hiçbir çizim/Bitmap işi YAPMAZ.
 *
 * NOT: `moduleCount` (zxing'in ürettiği karakter ızgarasının boyutu, veri
 * uzunluğuna göre DEĞİŞİR — sabit değildir) [QrMatrix.size] olarak
 * döndürülür; `requestedSize`, zxing'e "en az bu kadar piksel büyüklüğünde
 * bir görsel üret" der ama zxing kendi iç ızgarasını (quiet zone dahil)
 * kullanır — bu yüzden dönen matrisin gerçek [QrMatrix.size]'ı
 * `requestedSize`'tan farklı olabilir; çağıran kod matrisin GERÇEK
 * `size`'ına göre ölçekleyip çizmelidir.
 */
object QrMatrixEncoder {
    /**
     * @throws IllegalArgumentException [text] boşsa veya zxing onu bir QR
     * koduna sığdıramıyorsa (aşırı uzun metin) — çağıran kod bunu
     * yakalamalıdır.
     */
    fun encode(text: String, requestedSize: Int = 240): QrMatrix {
        require(text.isNotBlank()) { "QR için boş metin kodlanamaz" }
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, requestedSize, requestedSize, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        require(width == height) { "Beklenmeyen durum: zxing kare olmayan bir matris üretti ($width x $height)" }
        val dark = BooleanArray(width * height) { index ->
            val x = index % width
            val y = index / width
            bitMatrix.get(x, y)
        }
        return QrMatrix(size = width, dark = dark)
    }
}
