package com.federalturk.gamletsol.core.pipeline

/**
 * Cihaz-üstü çeviri soyutlaması. Android tarafında ML Kit Translation ile
 * (önceden indirilmiş en↔tr modeli) uygulanır. Yol haritasının P6'da
 * tanımladığı isteğe bağlı bulut çeviri de aynı arayüzün başka bir
 * uygulaması olarak eklenecek — pipeline hangi çeviri kaynağının
 * kullanıldığını bilmek zorunda değildir.
 */
interface Translator {
    suspend fun translate(text: String, sourceLanguageCode: String, targetLanguageCode: String): String
}
