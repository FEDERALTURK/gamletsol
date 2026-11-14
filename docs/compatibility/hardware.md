# Donanım ve deney kaydı (G001)

> Bu dosya `docs/roadmap/tasks/todo.md` içindeki **G001 — Hedef donanım ve
> deney kaydı** görevinin kabul kanıtı şablonudur. Dürüstlük ilkesi:
> bilinmeyen alan boş/`?` bırakılır, tahmini değer yazılmaz.
>
> **2026-11-14 güncellemesi:** TV alanlarının bir kısmı artık GERÇEK cihazdan,
> ağ üzerinden `adb` ile okunarak doldurulmuştur (kaynak: `getprop`
> beyaz listesi + uygulamanın kendi `TvInputBridge` keşif kaydı). Hangi
> değerin ölçüldüğü satır satır belirtilmiştir. Konsol, telefon ve ağ
> alanları hâlâ kullanıcı tarafından doldurulmalıdır — bunlar adb ile
> okunamaz.

## TV

| Alan | Değer | Kaynak |
|---|---|---|
| Marka / seri | `Profilo 4K Android TV` (ürün: `Profilo_4K_ATV2`, cihaz kodu: `guandu`, üretici: `Expressluck`) | adb `getprop`, 2026-11-14 |
| Ekran boyu | 75" | kullanıcı beyanı (ölçülmedi) |
| Android TV sürümü / API seviyesi | **Android 11 / API 30** | adb `getprop`, 2026-11-14 |
| Google TV mi, klasik Android TV mi | **Klasik Android TV** (leanback) — çalışan launcher `com.google.android.tvlauncher`, Google TV'nin `launcherx`'i yok | adb `ps -A`, 2026-11-14 |
| Firmware/yazılım sürümü | `RTM7.230903.080` (fingerprint: `Profilo/Profilo_4K_ATV2/guandu:11/RTM7.230903.080/11092646:user/release-keys`) | adb `getprop`, 2026-11-14 |
| Güvenlik yaması | `2023-11-01` | adb `getprop`, 2026-11-14 |
| CPU mimarisi | **`armeabi-v7a,armeabi` — yalnız 32-bit** (arm64 YOK) | adb `getprop`, 2026-11-14 |
| HDMI giriş sayısı | **4** (HDMI 1–4, hepsi passthrough) | uygulamanın giriş keşfi, 2026-11-14 |
| HDMI-CEC açık mı | ? | kullanıcı doldurmalı |
| TV'nin yerel IP'si | `10.0.0.8` (adb ağ hata ayıklama portu 5555 açık) | adb, 2026-11-14 |

## Konsol

| Alan | Değer |
|---|---|
| Model | Xbox Series X veya S? |
| Bağlı olduğu HDMI girişi (TV üzerinde) | ? |
| Çıkış çözünürlüğü/Hz (TV ayarlarında görünen) | ? |
| HDR/Dolby Vision açık mı | ? |
| VRR/ALLM açık mı | ? |

## Telefon (yakalama/eşleştirme tarafı)

| Alan | Değer |
|---|---|
| Model | ? |
| Android sürümü | ? |
| Kamera çözünürlüğü (arka kamera, ana sensör) | ? |

## Ağ

| Alan | Değer |
|---|---|
| TV bağlantı türü | Wi-Fi mi, kablolu mu? |
| Telefon bağlantı türü | Wi-Fi mi, mobil veri mi? |
| Aynı ağda mı (TV ve telefon) | ? |
| Router/AP isolation var mı (biliniyorsa) | ? |

## G002 — Profilo HDMI girişlerini keşfet (sonuçlar)

**Durum: BAŞARILI** (2026-11-14, gerçek cihaz, adb + uygulama kaydı).

Uygulama 9 TV girişi buldu; 4'ü HDMI ve hepsi passthrough:

| Etiket | `inputId` | HDMI | Passthrough |
|---|---|---|---|
| HDMI 1 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` | ✓ | ✓ |
| HDMI 2 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW6` | ✓ | ✓ |
| HDMI 3 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW7` | ✓ | ✓ |
| HDMI 4 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW8` | ✓ | ✓ |
| Composite | `com.mediatek.tvinput/.composite.CompositeInputService/HW2` | ✗ | ✓ |
| Tuner(DTV) | `com.mediatek.tvinput/.tuner.TunerInputService/HW0` | ✗ | ✗ |
| Tuner(ATV) | `com.mediatek.tvinput/.tuner.TunerInputService/HW1` | ✗ | ✗ |
| Google Play Filmler (2 giriş) | `com.google.android.videos/...` | ✗ | ✗ |

- Xbox'ın fiziksel olarak takılı olduğu HDMI girişinin `inputId`'si: **?**
  (henüz bilinmiyor — uygulama varsayılan olarak HDMI 4'ü denedi ve orada
  **sinyal yoktu**, yani konsol başka bir girişte ya da kapalıydı.)
- İzin/erişim hatası oluştu mu: **Hayır** — `TvInputManager` erişimi
  sorunsuz, ek izin gerekmedi.

## G002/G003 sırasında bulunan ve düzeltilen hatalar (2026-11-14)

İlk gerçek cihaz denemesi, kum havuzunda görülemeyen dört hatayı ortaya
çıkardı (ayrıntı: ilgili kaynak dosyaların KDoc'ları):

1. **TLS el sıkışması bu TV'de çalışmıyordu (ölümcül).** AndroidKeyStore
   anahtarı üretiliyordu ama ilk el sıkışmada cihazın keymaster'ı ham RSA
   işlemini reddediyordu (`Incompatible padding mode` /
   `RSA/ECB/NoPadding`), yani **telefon TV'ye hiç bağlanamıyordu** ve bu
   hiçbir yerde hata olarak görünmüyordu. Anahtar artık `DIGEST_NONE` +
   `ENCRYPTION_PADDING_NONE` ile üretiliyor ve kimlik kullanılmadan önce
   gerçek bir loopback TLS el sıkışmasıyla sınanıyor. Düzeltme sonrası
   aynı TV'de doğrulandı: `PersistentTvIdentity: Donanım destekli
   (AndroidKeyStore) TV kimliği kullanılıyor`.
2. **NSD/mDNS kaydı dakikada bir yenileniyordu** (eşleştirme kodu her 60 sn
   yenilendiği için). Kod TTL'i 5 dk yapıldı, aynı içerikli tekrar kayıtlar
   atlanıyor. Düzeltme sonrası açılışta tek kayıt gözlendi.
3. **Sinyalsiz HDMI girişi seçiliyordu** (HDMI 4, `onVideoUnavailable
   reason=2`) ve kullanıcı siyah ekran görüyordu. Artık sinyal gelmezse
   sıradaki HDMI girişine kendiliğinden geçiliyor.
4. **Açılışta ana iş parçacığı bloke oluyordu** (263 kare atlandı, ~4,4 sn).
   RSA anahtar üretimi ve giriş keşfi arka plana alındı; kalan takılma
   debug derlemesinin Compose JIT maliyetidir (bkz. G003 notu).

## G003 — Canlı HDMI üstüne sabit Türkçe yazı (sonuçlar)

**Durum: KISMİ.** API seviyesinde tune çalışıyor, ancak canlı oyun görüntüsü
üstünde yazı KANITLANMADI.

- `app-tv` Xbox'ın bağlı olduğu girişe tune olabildi mi: **?** — `TvView.tune()`
  çağrısı HDMI 4 için hatasız kabul edildi ve geri çağrı geldi, ama o girişte
  sinyal yoktu (`onVideoUnavailable reason=2`). Konsolun takılı olduğu giriş
  açıkken tekrar denenmeli.
- Türkçe test yazısı 30 dakika boyunca görüntü üzerinde sürekli görünür
  kaldı mı: **? (henüz denenmedi)**
- Geri tuşu / giriş değişimi / kaynak kaybı davranışı: **? (henüz denenmedi)**
- Desteklenmiyorsa karşılaşılan engel: **henüz bilinmiyor.** Not: TV'nin
  kendi uygulaması `com.mediatek.wwtv.tvcenter`, 12 Kasım'da `TvView.reset()`
  → `InputSourceManager.stopSession` yolunda üç kez `NullPointerException`
  ile çöktü (TV'nin kendi kodunda, bizim sürecimizde değil). Bunun bizim
  HDMI tune/release akışımızla ilişkili olup olmadığı **doğrulanmadı**;
  oyun görüntüsü testinde izlenmeli.

## G003 — Canlı HDMI üstüne sabit Türkçe yazı (sonuçlar buraya)

- `app-tv` probe APK'sı Xbox'ın bağlı olduğu girişe tune olabildi mi: ?
- Türkçe test yazısı (ı/İ/ş/ğ/ü/ö/ç içeren) 30 dakika boyunca görüntü
  üzerinde sürekli görünür kaldı mı: ?
- Geri tuşu / giriş değişimi / kaynak kaybı (Xbox'ı kapatma) sırasında
  davranış: ?
- Desteklenmiyorsa, karşılaşılan tam engel (örn. "TvView passthrough
  girişine tune olmadı", "yazı video arkasında kaldı" vb.): ?

**Not:** Bu alanlar doldurulmadan G001-G003 "tamamlandı" sayılmaz
(`docs/roadmap/tasks/todo.md` ortak tamamlanma ölçütü). 2026-11-14
itibarıyla G002 kanıtlandı; G001'in TV bölümü büyük ölçüde doldu (konsol/
telefon/ağ alanları eksik); G003 hâlâ konsol açıkken gerçek bir denemeyi
bekliyor.
