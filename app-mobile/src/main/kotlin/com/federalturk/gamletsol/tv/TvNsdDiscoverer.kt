package com.federalturk.gamletsol.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.federalturk.gamletsol.core.transport.DiscoveredTv
import com.federalturk.gamletsol.core.transport.TvNsdAttributes

/**
 * G025 — gerçek Android `NsdManager` (mDNS/DNS-SD) ile yerel ağdaki
 * GAMLETSOL TV'lerini TARAR. `app-tv`'deki [TvNsdAdvertiser]'ın karşılığı
 * (bkz. o sınıfın dokümantasyonu — aynı "platform koduyla saf-Kotlin
 * çekirdeği ayırma" deseni, G022).
 *
 * NOT (dürüstlük ilkesi/MVP sınırı): bu sınıf bu kum havuzunda yerel
 * olarak DERLENMEDİ (Android SDK yok) ve gerçek bir mDNS/NSD ortamında
 * HİÇ denenmedi. NSD başarısız olursa/engelliyse (bazı router'larda mDNS
 * kapalı) kullanıcıya hiçbir TV listelenmez — bu bir hata DEĞİLDİR, QR
 * (G023) ve manuel giriş hâlâ çalışır, bu yüzden [onDiscovered] hiç
 * çağrılmamış olması kullanıcıyı engellemez.
 */
class TvNsdDiscoverer(private val context: Context) {
    private val nsdManager: NsdManager? =
        runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Taramayı başlatır. [onDiscovered], her çözümlenen (resolve edilmiş)
     * TV için ana iş parçacığında çağrılır — aynı TV birden fazla kez
     * bulunabilir (örn. ağ koşulları nedeniyle yeniden reklam edilirse),
     * çağıran kod (bkz. `CaptionScreen`) aynı `serviceName`'i güncelleyerek
     * tekilleştirmeli. [onLost], bir TV ağdan kaybolduğunda (kapandı,
     * reklamı durdu) `serviceName`'iyle çağrılır.
     */
    fun startDiscovery(onDiscovered: (DiscoveredTv) -> Unit, onLost: (String) -> Unit = {}) {
        val manager = nsdManager ?: return
        stopDiscovery()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "NSD taraması başladı: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // onServiceFound'daki NsdServiceInfo HENÜZ çözümlenmemiş
                // olabilir (host/port/TXT eksik) — resolveService ile ayrıca
                // çözümlemek gerekir.
                runCatching {
                    manager.resolveService(
                        service,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.w(TAG, "NSD çözümlemesi başarısız (${serviceInfo.serviceName}, kod=$errorCode)")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val attrs = TvNsdAttributes.fromTxtRecordMap(serviceInfo.attributes)
                                val host = serviceInfo.host?.hostAddress
                                if (attrs == null || host == null) {
                                    // Bu bir GAMLETSOL TV'si değil (ör. ağdaki başka bir
                                    // _gamletsol._tcp cihazı) ya da eksik/bozuk bir kayıt —
                                    // sessizce atla.
                                    return
                                }
                                val discovered = DiscoveredTv(
                                    serviceName = serviceInfo.serviceName,
                                    host = host,
                                    port = serviceInfo.port,
                                    pairingCode = attrs.pairingCode,
                                    fingerprintSha256 = attrs.fingerprintSha256,
                                    protocolVersion = attrs.protocolVersion,
                                )
                                mainHandler.post { onDiscovered(discovered) }
                            }
                        },
                    )
                }.onFailure { Log.w(TAG, "NSD resolveService çağrısı istisna fırlattı", it) }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                mainHandler.post { onLost(service.serviceName) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "NSD taraması durdu: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD taraması başlatılamadı (kod=$errorCode) — QR/manuel giriş hâlâ kullanılabilir")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD taramasının durdurulması başarısız (kod=$errorCode)")
            }
        }
        discoveryListener = listener
        runCatching { manager.discoverServices(TvNsdAttributes.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "NSD discoverServices çağrısı istisna fırlattı", it) }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { manager.stopServiceDiscovery(listener) }
    }

    companion object {
        private const val TAG = "TvNsdDiscoverer"
    }
}
