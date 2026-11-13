package com.federalturk.gamletsol.translate

import android.content.Context
import android.util.Log
import com.federalturk.gamletsol.core.model.TranslationLinePack
import com.federalturk.gamletsol.core.pipeline.LinePackTsvCodec
import com.federalturk.gamletsol.core.pipeline.UserTranslationLibrary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Çeviri kütüphanesinin (2026-11-13) cihazdaki yükleme/kaydetme katmanı.
 *
 * İki katman:
 * - **Gömülü genel ifadeler** (`assets/library/genel-oyun-ifadeleri.tsv`):
 *   uygulamayla gelir, salt okunur. Oyunlarda sık görülen menü/sistem
 *   iletileri ve kısa diyalog ifadeleri için GAMLETSOL'a özgü çeviriler.
 * - **Kişisel kütüphane** (`filesDir/library/kisisel-kutuphane.tsv`):
 *   kullanıcının ekranda düzelttiği çeviriler. Uygulama kapanıp açılınca
 *   korunur.
 *
 * Gizlilik: Her iki dosya da yalnız cihazda tutulur, hiçbir yere
 * gönderilmez (bkz. AGENTS.md madde 2).
 *
 * NOT (dürüstlük ilkesi): Gerçek cihazda denenmedi; ayrıştırma ve eşleştirme
 * mantığı core-pipeline birim testleriyle doğrulandı.
 */
class TranslationLibraryStore(private val context: Context) {

    @Volatile
    var builtinPack: TranslationLinePack? = null
        private set

    val personal = UserTranslationLibrary()

    private val _personalSnapshot = MutableStateFlow(personal.snapshot())

    /** Arayüz için kişisel kütüphanenin güncel hali. */
    val personalSnapshot: StateFlow<TranslationLinePack> = _personalSnapshot.asStateFlow()

    private val fileMutex = Mutex()

    private val personalFile: File
        get() = File(File(context.filesDir, "library"), PERSONAL_FILE_NAME)

    /**
     * Çeviri önbelleği için birleşik sürüm etiketi: iki katmandan biri
     * değişince (yükleme tamamlandı, kullanıcı düzeltme ekledi/sildi) değişir.
     */
    fun versionTag(): String = "${builtinPack?.versionTag ?: "yok"}|${personal.snapshot().versionTag}"

    /** Her iki katmanı arka planda yükler. Hata olursa ilgili katman boş kalır, uygulama çalışmaya devam eder. */
    suspend fun load() = withContext(Dispatchers.IO) {
        builtinPack = runCatching {
            context.assets.open(BUILTIN_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.mapCatching { LinePackTsvCodec.decode(it, defaultPackId = "genel-oyun-ifadeleri").pack }
            .onFailure { Log.w(TAG, "Gömülü çeviri kütüphanesi okunamadı", it) }
            .getOrNull()

        fileMutex.withLock {
            val file = personalFile
            if (file.exists()) {
                runCatching {
                    val decoded = LinePackTsvCodec.decode(
                        file.readText(Charsets.UTF_8),
                        defaultPackId = UserTranslationLibrary.PACK_ID,
                    ).pack
                    // Yüklenen satırları kütüphaneye aktar (sürüm yine artar —
                    // önemli değil, yalnız önbellek anahtarı için kullanılıyor).
                    decoded.lines.forEach { personal.put(it.sourceText, it.translatedText) }
                }.onFailure { Log.w(TAG, "Kişisel çeviri kütüphanesi okunamadı", it) }
            }
        }
        _personalSnapshot.value = personal.snapshot()
    }

    suspend fun saveCorrection(sourceText: String, translatedText: String) {
        personal.put(sourceText, translatedText)
        persist()
    }

    suspend fun removeCorrection(sourceText: String) {
        if (personal.remove(sourceText)) persist()
    }

    suspend fun clearPersonal() {
        personal.clear()
        persist()
    }

    private suspend fun persist() {
        val snapshot = personal.snapshot()
        _personalSnapshot.value = snapshot
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                runCatching {
                    val file = personalFile
                    file.parentFile?.mkdirs()
                    // Önce geçici dosyaya yaz, sonra yerine taşı: yazma yarıda
                    // kalırsa (uygulama kapandı, disk doldu) eski kütüphane bozulmaz.
                    val temp = File(file.parentFile, "$PERSONAL_FILE_NAME.tmp")
                    temp.writeText(LinePackTsvCodec.encode(snapshot), Charsets.UTF_8)
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                }.onFailure { Log.w(TAG, "Kişisel çeviri kütüphanesi kaydedilemedi", it) }
            }
        }
    }

    private companion object {
        const val TAG = "GamletsolLibrary"
        const val BUILTIN_ASSET_PATH = "library/genel-oyun-ifadeleri.tsv"
        const val PERSONAL_FILE_NAME = "kisisel-kutuphane.tsv"
    }
}
