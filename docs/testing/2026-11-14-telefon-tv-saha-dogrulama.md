# Saha doğrulama — telefon ↔ TV uçtan uca (2026-11-14)

Gerçek cihazlarda, adb üzerinden otomatik yapılan doğrulama kaydı. Yöntem,
kanıt ve sonuçlar ayrı ayrı verilmiştir (dürüstlük ilkesi: her satır gözlenen
veriye dayanır; çıkarım olanlar "çıkarım" diye işaretlidir).

## Kurulum

| | Telefon | TV |
|---|---|---|
| Cihaz | HONOR MTN-NX1M, Android 16 (SDK 36), arm64-v8a | Profilo 4K Android TV, Android 11 (SDK 30), armeabi-v7a |
| Bağlantı | USB (`AGCVVB5C01002525`) | Wi-Fi adb (`10.0.0.8:5555`) |
| Uygulama | app-mobile build 49 | app-tv build 47 (**eski** — güncellenmeli) |
| IP | 10.0.0.6 | 10.0.0.8 |

## Gözlemler

### Telefon
- Logcat (5000 satır, PID filtresiz): `CamX` 2672, `PipelineManager` 281
  satır — kamera HAL'ı ve ML Kit OCR motoru bizim süreçte ~30 fps çalışıyor.
- `GamletsolPipeline` tag'inden **sıfır** satır.
- Ekran görüntüsü (`screencap`): uygulama yakalama ekranında, "altyazı
  aranıyor…" banner'ı ve ROI çerçevesi görünüyor; kamera önizlemesi
  **tamamen siyah**.
- `/proc/net/tcp6`: `10.0.0.8:48631`'e hiçbir bağlantı yok (ne ESTABLISHED
  ne SYN_SENT). Tek telefon→TV bağlantısı `8009` (Chromecast, sistem).

### TV
- `/proc/net/tcp6`: `0.0.0.0:48631 LISTEN` — TLS altyazı sunucusu ayakta.
- Logcat: NSD "GAMLETSOL-TV" kaydı başarılı, 9 TV girişi (4 HDMI) algılandı,
  AndroidKeyStore destekli kimlik yüklendi. Sunucu başlangıcına dair log
  **yok** (kod o ana kadar hiç log yazmıyordu).
- `screencap` TV'de "unreadable output" ile başarısız (iki deneme, farklı
  dizin). Çıkarım: HDMI/korumalı katman; doğrulanmadı.
- Açılışta `Invalid ID 0x00000000` (1 kez) ve 106 kare atlama (Davey!
  2175 ms) — başlangıç performans notu, işlevi engellemiyor.

## Sonuçlar

1. **Telefon pipeline'ı çalışıyor; "sessizlik" bir hata değil.** Kamera hiç
   metin görmediği (siyah kare) için OCR her karede boş dönüyor;
   `CaptionPipeline` "OCR boş kare" logunu yalnız ilk boş karede yazdığı
   için dakikalarca tek satır üretmiyordu ve o satır ML Kit'in 30 fps iç
   loglarının arasında tampondan düşmüştü. → **Düzeltme:** her 300 karede bir
   tek satırlık durum özeti (`Durum: N karede X metinli, Y boş, Z atlandı;
   aday=… aktifCue=…`).
2. **TV sunucusu çalışıyor; log eksikliği yanıltıcıydı.** `TvEventServer`
   başlangıcı loglanmıyordu; doğrulama ancak `/proc/net/tcp6` ile yapılabildi.
   → **Düzeltme:** `GamletsolTvServer` tag'iyle başlat/durdur logu.
3. **Telefon TV'ye bağlı değil.** 48631'e hiçbir bağlantı denemesi yok;
   bu oturumda QR eşleştirme yapılmamış (çıkarım: uygulama QR okutulmadan
   TV'ye bağlanmayı denemez — tasarım gereği). Bağlanmadan çeviri yalnız
   telefon ekranında görünür.
4. **TV uygulaması eski (47 < 49).** Protokol v2 testleri için TV'ye güncel
   APK kurulmalı.

## Kullanıcıya düşen (otomatikleştirilemeyen) adımlar

1. Telefonu masadan kaldırıp arka kamerayı **İngilizce altyazı görünen
   ekrana** doğrultun; altyazı ROI çerçevesinin içine gelsin. Önizleme siyah
   kaldığı sürece OCR'a okunacak hiçbir şey gitmiyor.
2. Çevirinin TV'de görünmesi için telefonda "TV'ye bağlan" → TV'deki QR'ı
   okutun. Bağlantı kurulunca TV tarafında 48631 portunda ESTABLISHED
   bağlantı ve (yeni build ile) telefonda `Durum:` satırlarında `metinli > 0`
   görülmelidir.

## Doğrulanmayanlar

- Kamera gerçekten altyazıya doğrultulduğunda OCR → çeviri → TV zinciri bu
  oturumda **çalıştırılamadı** (fiziksel kamera gerektirir).
- TV ekran görüntüsünün neden alınamadığı araştırılmadı.
