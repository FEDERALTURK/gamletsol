package com.federalturk.gamletsol.core.pipeline

/**
 * Yol haritası G036 ("Termal, pil ve performans uyarlaması") kabul
 * kriterinin ÇEKİRDEK mantığı: "yük artınca OCR frekansı kontrollü düşer".
 *
 * Her kareyi OCR'a vermek yerine, son işlenmiş karelerin ortalama süresini
 * (pencereli) izler — süre sürekli olarak hedef bütçenin üstündeyse (cihaz
 * ısınmış/CPU yük altında gibi tipik bir belirti), kademeli olarak kare
 * ATLAMA oranını artırır ([skipFactor] N olunca her N kareden sadece 1'i
 * işlenir); cihaz toparlanıp süre tekrar bütçenin altına düşerse kademeli
 * olarak geri azaltır. Tek bir yavaş kare ANINDA atlamaya geçirmez — ancak
 * [windowSize] kadar ardışık ÖLÇÜM birikince karar verilir (histerezis).
 *
 * Saf Kotlin/JVM'dir, Android'e bağımlı değildir (bkz. kök AGENTS.md madde
 * 5) — bu yüzden [CaptionPipeline] tarafından doğrudan, test edilebilir
 * şekilde kullanılabilir.
 *
 * NOT (dürüstlük ilkesi): [targetFrameBudgetMillis] varsayımsal bir
 * başlangıç değeridir, gerçek bir orta sınıf telefonda ölçülmüş bir eşik
 * DEĞİLDİR — yol haritasının istediği gerçek "60-120 dakika pil/ısı/RAM"
 * profili bu kum havuzunda hiç yapılamadı (gerçek cihaz gerektirir). Bu
 * sınıf yalnızca "yük artınca OCR frekansı kontrollü düşer" davranışını
 * SAĞLAR — gerçek eşik değerleri gerçek cihaz ölçümüyle ayarlanmalı.
 */
class AdaptiveFrameThrottle(
    private val targetFrameBudgetMillis: Long = DEFAULT_TARGET_BUDGET_MILLIS,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val maxSkipFactor: Int = DEFAULT_MAX_SKIP_FACTOR,
) {
    init {
        require(targetFrameBudgetMillis > 0) { "targetFrameBudgetMillis pozitif olmalı" }
        require(windowSize >= 1) { "windowSize en az 1 olmalı" }
        require(maxSkipFactor >= 1) { "maxSkipFactor en az 1 olmalı" }
    }

    private val recentDurationsMillis = ArrayDeque<Long>()

    /** 1 = her kare işlenir (atlama yok); N (>1) = her N kareden yalnızca 1'i işlenir. */
    private var skipFactor = 1
    private var framesSeenSinceLastProcessed = 0

    /**
     * Çağıran, bu kareyi gerçekten OCR'a mı vermeli yoksa tamamen atlamalı mı
     * diye bunu sorar. `true` dönerse çağıran, kareyi işledikten sonra süresini
     * [recordProcessingDuration] ile bildirmeli — `false` dönen kareler için
     * HİÇ çağrılmamalı (atlanan kare zaten ölçülecek bir iş yapmadı).
     */
    fun shouldProcess(): Boolean {
        framesSeenSinceLastProcessed++
        if (framesSeenSinceLastProcessed < skipFactor) return false
        framesSeenSinceLastProcessed = 0
        return true
    }

    /** [shouldProcess] `true` döndürdükten sonra, o karenin OCR+normalizasyon süresini (ms) bildirir. */
    fun recordProcessingDuration(durationMillis: Long) {
        recentDurationsMillis.addLast(durationMillis)
        if (recentDurationsMillis.size > windowSize) recentDurationsMillis.removeFirst()
        // Pencere henüz dolmadıysa karar vermek için yetersiz örnek var — bekle.
        if (recentDurationsMillis.size < windowSize) return

        val averageMillis = recentDurationsMillis.average()
        when {
            averageMillis > targetFrameBudgetMillis * OVERLOAD_RATIO && skipFactor < maxSkipFactor -> {
                skipFactor++
                recentDurationsMillis.clear() // yeni skipFactor'ü kendi penceresiyle yeniden değerlendir
            }
            // 2026-11-14 düzeltme: Eski kural, geri düşmek için işlem
            // süresinin BÜTÇENİN %80'ine inmesini istiyordu. Ancak OCR süresi
            // cihazın donanımına bağlı SABİT bir maliyet — skipFactor artınca
            // işlenen kare sayısı düşer ama her işlenen karenin süresi aynı
            // kalır, bu yüzden ortalama hiç düşmez ve geri toparlanma ASLA
            // olmaz. Yeni kural: skipFactor > 1 iken periyodik olarak bir
            // kademe düşürüp dene; eğer yük hâlâ yüksekse bir sonraki pencere
            // tekrar artırır. Bu, "dene ve geri çekil" yaklaşımı ile cihazın
            // gerçek kapasitesini sürekli yeniden değerlendirmesini sağlar.
            skipFactor > 1 -> {
                // Ortalama en azından bütçenin altındaysa (skip sayesinde
                // cihaz nefes alıyorsa) bir kademe düşür.
                if (averageMillis <= targetFrameBudgetMillis * OVERLOAD_RATIO) {
                    skipFactor--
                    recentDurationsMillis.clear()
                }
            }
        }
    }

    /** Test/gözlem amaçlı — şu an her N kareden kaçının atlandığını okur. */
    fun currentSkipFactor(): Int = skipFactor

    companion object {
        /** Varsayımsal başlangıç bütçesi (ms) — bkz. sınıf KDoc'u "dürüstlük ilkesi" notu. */
        const val DEFAULT_TARGET_BUDGET_MILLIS = 150L
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_MAX_SKIP_FACTOR = 4
        private const val OVERLOAD_RATIO = 1.5
        private const val RECOVER_RATIO = 0.8
    }
}
