package com.federalturk.gamletsol.core.pairing

/**
 * Bir QR karesinin platformdan bağımsız temsili — her hücre karanlık (`true`)
 * veya açık (`false`). [QrMatrixEncoder] bunu üretir; `app-tv` bunu
 * Bitmap/Canvas'a çevirip çizer — bu sınıfın kendisi hiçbir Android türü
 * BİLMEZ, bu yüzden bu modül (`core-pairing`) saf Kotlin/JVM kalır ve bu
 * sınıf Android'siz test edilebilir.
 */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) {
            "dark.size (${dark.size}) size*size ($size*$size=${size * size}) ile eşleşmeli"
        }
    }

    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size) { "($x,$y) sınır dışı (size=$size)" }
        return dark[y * size + x]
    }
}
