# Saha doğrulama — telefon ↔ TV uçtan uca (2026-11-14)

Gerçek cihazlarda, adb üzerinden otomatik yapılan doğrulama kaydı. Yöntem,
kanıt ve sonuçlar ayrı ayrı verilmiştir (dürüstlük ilkesi: her satır gözlenen
veriye dayanır; çıkarım olanlar "çıkarım" diye işaretlidir).

## Kurulum

| | Telefon | TV |
|---|---|---|
| Cihaz | HONOR MTN-NX1M, Android 16 (SDK 36), arm64-v8a | Profilo 4K Android TV, Android 11 (SDK 30), armeabi-v7a |
| Bağlantı | USB (`AGCVVB5C01002525`) | Wi-Fi adb (`10.0.0.8:5555`) |
| Uygulama | app-mobile build 50 | app-tv build 50 |
| IP | 10.0.0.6 | 10.0.0.8 |

## Gözlemler

### Telefon (build 50 — güncellenmiş)

**İlk kontrol (PID filtresiz logcat, 5000 satır):**
- `CamX` 2672, `PipelineManager` 281 satır — kamera HAL'ı ve ML Kit OCR
  motoru bizim süreçte ~30 fps çalışıyor.
- `GamletsolPipeline` tag'inden **sıfır** satır (ML Kit'in ~30fps iç
  logları tamponu doldurup bizim 10 sn'de 1 satırımızı düşürüyor).

**İkinci kontrol (PID-filtreli logcat, 5000 satır, PID 31455):**
- ✅ **Heartbeat BULUNDU:**
  `11-14 15:28:49.163 31455 8154 D GamletsolPipeline: Durum: 300 karede 0 metinli, 300 boş, 0 atlandı; aday=false aktifCue=false`
- Bu, commit 04e2de6'daki heartbeat teşhis sisteminin gerçek cihazda
  çalıştığını kanıtlar.
- `0 metinli, 300 boş` → kamera masaya/tavana bakıyor, altyazı yok — beklenen
  davranış.

**Ekran görüntüsü (`screencap`):**
- Uygulama yakalama ekranında (onboarding geçilmiş), "altyazı aranıyor…"
  banner'ı ve ROI çerçevesi görünüyor.
- "Türkçe ses kurulu ama ağ bağlantısı gerektiriyor — çevrimdışıyken sesli
  okuma çalışmayabilir."
- Kamera önizlemesi **tamamen siyah** (telefon masada).

**Ağ:**
- `/proc/net/tcp6`: `10.0.0.8:48631`'e hiçbir bağlantı yok (ne ESTABLISHED
  ne SYN_SENT). QR eşleştirme yapılmamış — tasarım gereği.

### TV (build 50 — güncellenmiş)

**Logcat (güncellenmiş build ile):**
- ✅ `GamletsolTvServer: TLS altyazı sunucusu başladı, port=48631` — sunucu
  başlangıç logu artık VAR (commit 04e2de6 düzeltmesi çalışıyor).
- ✅ NSD "GAMLETSOL-TV" kaydı başarılı.
- ✅ AndroidKeyStore destekli TV kimliği yüklendi.
- 9 TV girişi (4 HDMI) algılandı.

**Ağ — bağımsız doğrulama:**
- ✅ `/proc/net/tcp6`: port 48631 (0xBDF7) **LISTEN** durumunda
  (`00000000:BDF7 ... 0A`).
- Port 5555 (0x15B3) LISTEN + 10.0.0.10'dan ESTABLISHED (PC'nin adb
  bağlantısı).
- 48631'de ESTABLISHED bağlantı **yok** (beklenen — QR eşleştirme yapılmadı).

**Diğer:**
- `screencap` TV'de "unreadable output" ile başarısız (iki deneme, farklı
  dizin). Çıkarım: HDMI/korumalı katman; doğrulanmadı.
- Açılışta `Invalid ID 0x00000000` (1 kez) ve 106 kare atlama (Davey!
  2175 ms) — başlangıç performans notu, işlevi engellemiyor.

## Sonuçlar

1. **Telefon pipeline'ı çalışıyor — heartbeat ile KANITLANDI.** ✅
   `GamletsolPipeline: Durum: 300 karede 0 metinli, 300 boş, 0 atlandı`
   satırı gerçek cihazda görüldü. Commit 04e2de6'daki heartbeat teşhis
   sistemi üretimde çalışıyor. Pipeline her 300 karede (~10 sn) bir durum
   raporluyor. `0 metinli` beklenen: kamera altyazıya doğrultulmamış.

2. **TV TLS sunucusu çalışıyor — ÇİFT doğrulama.** ✅
   Hem logcat (`GamletsolTvServer: TLS altyazı sunucusu başladı, port=48631`)
   hem de `/proc/net/tcp6` (0xBDF7 LISTEN) ile bağımsız olarak doğrulandı.
   Commit 04e2de6'daki sunucu başlangıç logu da üretimde çalışıyor.

3. **Telefon TV'ye bağlı değil — tasarım gereği.** ℹ️
   48631'e hiçbir bağlantı denemesi yok; QR eşleştirme yapılmamış. Uygulama
   QR okutulmadan TV'ye bağlanmayı denemiyor (doğru davranış).

4. **Her iki cihaz da build 50'de.** ✅
   Önceki oturumda TV build 47'deydi; artık her ikisi de güncel.

## Kullanıcıya düşen (otomatikleştirilemeyen) adımlar

1. Telefonu masadan kaldırıp arka kamerayı **İngilizce altyazı görünen
   ekrana** doğrultun; altyazı ROI çerçevesinin içine gelsin. Önizleme siyah
   kaldığı sürece OCR'a okunacak hiçbir şey gitmiyor. → Heartbeat'te
   `metinli > 0` görülmeli.
2. Çevirinin TV'de görünmesi için telefonda "TV'ye bağlan" → TV'deki QR'ı
   okutun. Bağlantı kurulunca:
   - TV'de: 48631 portunda ESTABLISHED bağlantı
   - Telefonda: `Durum:` satırlarında `metinli > 0` + `aktifCue=true`
   - TV ekranında: Türkçe çevrilmiş altyazı

## Doğrulanmayanlar

- Kamera gerçekten altyazıya doğrultulduğunda OCR → çeviri → TV zinciri bu
  oturumda **çalıştırılamadı** (fiziksel kamera yönlendirmesi gerektirir).
- TV ekran görüntüsünün neden alınamadığı araştırılmadı.
- QR eşleştirme akışı bu oturumda test edilmedi.
