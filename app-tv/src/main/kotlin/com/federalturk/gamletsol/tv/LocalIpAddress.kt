package com.federalturk.gamletsol.tv

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Cihazın yerel ağdaki (Wi-Fi) IPv4 adresini bulmaya çalışır — kullanıcının
 * telefon tarafında elle girmesi için ekranda gösterilir (bkz. roadmap P3:
 * NSD/otomatik keşif henüz yok, bu MVP'de kullanıcı IP'yi elle girer).
 *
 * Özel bir izin (ör. ACCESS_WIFI_STATE) gerektirmez — yalnızca standart
 * `NetworkInterface` API'si kullanılır. Birden çok arayüz varsa (Ethernet +
 * Wi-Fi gibi) ilk uygun olanı döner; bu kesin/güvenilir bir seçim
 * ALGORİTMASI değildir — gerçek bir TV'de birden fazla ağ arayüzü varsa
 * yanlış adresi gösterebilir (denenmedi).
 */
object LocalIpAddress {
    fun find(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
