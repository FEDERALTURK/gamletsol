# Donanım ve deney kaydı (G001)

> Bu dosya `docs/roadmap/tasks/todo.md` içindeki **G001 — Hedef donanım ve
> deney kaydı** görevinin kabul kanıtı şablonudur. Buradaki alanlar bu kum
> havuzundan **doldurulamaz** — gerçek cihaza dokunan kişi (kullanıcı)
> doldurmalıdır. Dürüstlük ilkesi: bilinmeyen alan boş/`?` bırakılır, tahmini
> değer yazılmaz.

## TV

| Alan | Değer |
|---|---|
| Marka / seri | Profilo (tam model numarası?) |
| Ekran boyu | 75" (kullanıcı beyanı) |
| Android TV sürümü / API seviyesi | ? |
| Google TV mi, klasik Android TV mi | ? |
| Firmware/yazılım sürümü | ? |
| HDMI giriş sayısı | ? |
| HDMI-CEC açık mı | ? |

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

## G002 — Profilo HDMI girişlerini keşfet (sonuçlar buraya)

- `app-tv` probe APK'sı TV'ye kurulup açıldığında listelenen girişler
  (uygulama ekranındaki "Algılanan TV girişleri" listesini buraya kopyalayın):
  - (henüz çalıştırılmadı)
- Xbox'ın fiziksel olarak takılı olduğu HDMI girişinin `inputId`'si: ?
- İzin/erişim hatası oluştu mu (oluştuysa tam hata metni): ?

## G003 — Canlı HDMI üstüne sabit Türkçe yazı (sonuçlar buraya)

- `app-tv` probe APK'sı Xbox'ın bağlı olduğu girişe tune olabildi mi: ?
- Türkçe test yazısı (ı/İ/ş/ğ/ü/ö/ç içeren) 30 dakika boyunca görüntü
  üzerinde sürekli görünür kaldı mı: ?
- Geri tuşu / giriş değişimi / kaynak kaybı (Xbox'ı kapatma) sırasında
  davranış: ?
- Desteklenmiyorsa, karşılaşılan tam engel (örn. "TvView passthrough
  girişine tune olmadı", "yazı video arkasında kaldı" vb.): ?

**Not:** Bu alanlar doldurulmadan G001-G003 "tamamlandı" sayılmaz
(`docs/roadmap/tasks/todo.md` ortak tamamlanma ölçütü).
