package com.federalturk.gamletsol.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.federalturk.gamletsol.core.transport.TvNsdAttributes

/**
 * G025 ("otomatik keşif") — gerçek Android `NsdManager` (mDNS/DNS-SD) ile
 * bu TV'yi yerel ağda reklam eder. `core-transport`'taki
 * [TvNsdAttributes] (saf Kotlin/JVM) burada TXT kayıtlarına dönüştürülür;
 * bu sınıfın kendisi Android'e bağımlı olduğu için `app-tv`'de yaşıyor
 * (bkz. `TvReceiverAdapter`/`SocketTvReceiverAdapter` ile aynı "platform
 * koduyla saf-Kotlin çekirdeği ayırma" deseni, G022).
 *
 * NOT (dürüstlük ilkesi/MVP sınırı): bu sınıf bu kum havuzunda yerel
 * olarak DERLENMEDİ (Android SDK yok) ve gerçek bir mDNS/NSD ortamında
 * (gerçek yönlendirici/router, gerçek telefon) HİÇ denenmedi — bazı ev
 * router'ları/misafir ağları mDNS trafiğini engeller; bu durumda kullanıcı
 * hâlâ QR'a (G023) veya manuel girişe geri dönebilir, bu yüzden NSD
 * BAŞARISIZ olursa hata sessizce yutulur, kullanıcıya engelleyici bir
 * hata gösterilmez (ekranda zaten QR/kod görünür durumda).
 *
 * Eşleştirme kodu her değiştiğinde (yeni kod üretildiğinde/süresi
 * dolduğunda) [restart] çağrılmalı — Android'in NSD API'si mevcut bir
 * kaydın TXT kayıtlarını YERİNDE güncellemeyi desteklemez, bu yüzden
 * [restart] önce [stop] edip sonra yeniden [start] eder (kısa bir pencere
 * boyunca TV ağda görünmez olabilir — bilinen, kabul edilebilir MVP
 * sınırı).
 */
class TvNsdAdvertiser(private val context: Context) {
    private val nsdManager: NsdManager? =
        runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var registeredServiceName: String? = null
        private set

    /**
     * [onServiceNameChanged] her değiştiğinde (kayıt/kaldırma/hata) ÇAĞRILIR,
     * ana iş parçacığında (NsdManager'ın geri çağrıları farklı bir iş
     * parçacığında gelebilir — Compose state'i ana iş parçacığı dışında
     * güncellemek güvenli değil). Compose tarafı bunu bir `mutableStateOf`'a
     * yansıtabilir.
     */
    fun start(port: Int, attributes: TvNsdAttributes, onServiceNameChanged: (String?) -> Unit = {}) {
        val manager = nsdManager ?: return // NSD bu cihazda yok/erişilemez — sessizce atla (QR/manuel hâlâ çalışır).
        stop()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME_HINT
            serviceType = TvNsdAttributes.SERVICE_TYPE
            this.port = port
            // NOT (gerçek Android API sınırı, CI'da bulundu): `setAttribute(String, byte[])`
            // yalnızca API 34+'te var; bu uygulamanın minSdk'si (26) daha
            // düşük olduğu için her API seviyesinde var olan
            // `setAttribute(String, String)` kullanılıyor — bkz.
            // TvNsdAttributes.toTxtRecordStringMap dokümantasyonu.
            attributes.toTxtRecordStringMap().forEach { (key, value) -> setAttribute(key, value) }
        }

        fun update(name: String?) {
            registeredServiceName = name
            mainHandler.post { onServiceNameChanged(name) }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                // Android, aynı ağda ad çakışması varsa gerçek adı değiştirebilir
                // (ör. "GAMLETSOL-TV (2)") — gerçek adı kaydet, log'la.
                Log.i(TAG, "NSD servisi kaydedildi: ${info.serviceName}")
                update(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                // Dürüstlük ilkesi: bu bir kullanıcı-engelleyici hata DEĞİL —
                // QR/manuel giriş her zaman çalışır, NSD yalnızca bir kısayol.
                Log.w(TAG, "NSD kaydı başarısız (kod=$errorCode) — QR/manuel giriş hâlâ kullanılabilir")
                update(null)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                update(null)
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD kaydının kaldırılması başarısız (kod=$errorCode)")
            }
        }
        registrationListener = listener
        runCatching { manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure {
                Log.w(TAG, "NSD registerService çağrısı istisna fırlattı", it)
                update(null)
            }
    }

    fun restart(port: Int, attributes: TvNsdAttributes, onServiceNameChanged: (String?) -> Unit = {}) =
        start(port, attributes, onServiceNameChanged)

    fun stop() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { manager.unregisterService(listener) }
        registeredServiceName = null
    }

    companion object {
        private const val TAG = "TvNsdAdvertiser"

        /**
         * İstenen servis adı — Android, ağda aynı adla başka bir kayıt varsa
         * bunu otomatik olarak benzersizleştirir (bkz. [onServiceRegistered]).
         */
        const val SERVICE_NAME_HINT = "GAMLETSOL-TV"
    }
}
