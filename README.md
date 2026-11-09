# GAMLETSOL — Game Translate Console

Oyunların ekranda zaten gösterdiği İngilizce altyazıyı **OCR ile okuyup**
Türkçeye çeviren Android uygulaması. Güncel ve en ayrıntılı yol haritası:
[`docs/roadmap/GAMLETSOL geliştirme yol haritası.md`](docs/roadmap/GAMLETSOL%20geli%C5%9Ftirme%20yol%20haritas%C4%B1.md)
— görev kırılımı [`docs/roadmap/tasks/todo.md`](docs/roadmap/tasks/todo.md)
(G001–G072), özet [`docs/roadmap/tasks/plan.md`](docs/roadmap/tasks/plan.md),
mimari şema [`docs/roadmap/GAMLETSOL_mimari.svg`](docs/roadmap/GAMLETSOL_mimari.svg).

## Yol haritası güncellemesi (2026-11-09, ikinci belge) — ÖNEMLİ SIRA DEĞİŞİKLİĞİ

Kullanıcının yerel bilgisayarından okunan daha ayrıntılı/kesin yol haritası
belgesi, bu depodaki ilk native iskeletin kurduğu sırayı **tersine
çeviriyor**:

- **Yeni P0 — tek öncelik:** Kullanıcının gerçek donanımı (Xbox Series X/S +
  75" Profilo Android TV, tam model/firmware bilinmiyor) üzerinde, canlı HDMI
  görüntüsünün üstüne sabit Türkçe yazının **TvInputManager/TvView ile aynı
  anda görüntülenebildiğini kanıtlamak** (`G001`-`G006`). Bu kapı geçmeden TV
  üstü çeviri vaadi "tamamlandı" sayılmaz; telefon ekranı/TTS alternatifleri
  bu vaadin yerini tutmaz.
- Bu depoda **şu ana kadar yapılan iş** (`app-mobile` + `core-model` +
  `core-pipeline` + `feature-capture` — telefon kamerası → OCR → çeviri →
  telefon ekranı/TTS) yeni belgenin sıralamasında **P1-P2'ye karşılık
  gelir**, yani P0 (TV HDMI kanıtı) henüz hiç başlamadan ileri bir faza
  atlanmış durumda. Bu bilinçli bir hata değil, bu depo önceki (daha kısa) yol
  haritası belgesine göre kurulmuştu; yeni belge daha sonra geldi.
  **Dürüstlük ilkesi gereği bunu açıkça yazıyoruz:** mevcut kod hâlâ
  geçerli ve çalışır durumda (bkz. "Doğrulama durumu"), ama şu an asıl kapı
  olan TV HDMI kanıtı (`G001`-`G006`, `app-tv/probe`) için **hiçbir satır
  kod yazılmadı**.
- Yeni belge 72 görevi (**G001-G072**) P0-P7 + X1-X5 (koşullu genişlemeler)
  olarak numaralandırıyor ve her fazın net efor bütçesini veriyor:
  **P0-P5 çekirdek 550 saat (≈687,5 saat %25 payla, ≈27,5 hafta)**,
  **P0-P7 (bulut+ödeme dahil) 750 saat (≈937,5 saat, ≈37,5 hafta)**,
  tüm koşullu genişlemeler (Xbox doğrudan bağlantı, PS4/PS5, Akıllı Ekran,
  Samsung/LG, ses paketleri) ayrı PoC sonrası tahmin, kabaca **18-30 ay**.
  Bu rakamlar **plan/tahmin, ölçüm değil** — ilk 50 saatten sonra gerçek
  hızla yeniden değerlendirilecek.
- Dört farklı görüntü+yazı bindirme yolu (A: yerel HDMI + ayrı metin olayları;
  B: uygulama-içi video+metin tek alıcıdan; C: harici donanım HDMI
  birleştirici — MVP kapsamı DIŞI; D: ikinci ekran/TTS — "TV üstü çeviri"
  hedefini KARŞILAMIYOR) belgede ayrıntılı karşılaştırılıyor; hangisinin
  gerçekte çalıştığı yalnızca G001-G006 sonucunda bilinecek.
- Yeni belge ayrıca bir **G6 karar kapısı** (yayın-hazırlığı: gizlilik/ödeme/
  destek/cihaz uyumluluğu bir arada) ve nested bir `android/` dizin yapısı
  (`app-tv`, `core/transport`, `data/local`, `feature/session`,
  `feature/speech`, `feature/smart-screen`, kök seviyede `backend/`,
  `receivers/tizen/`, `receivers/web/`, `contracts/`, `tests/fixtures/`)
  öneriyor. **Bu dizin yeniden yapılandırması bu commit'te YAPILMADI** —
  mevcut düz modül yapısı (`app-mobile`, `core-model`, `core-pipeline`,
  `feature-capture`, repo köküne düz) hâlâ geçerli. Gradle modül ağacının
  yeniden organize edilmesi, P0 kanıtı (gerçek TV donanımıyla) sonuçlanmadan
  önce gereksiz risk olarak değerlendirildi: önce `app-tv/probe` modülünü bu
  düz yapıya **ekleyerek** P0'ı kanıtlamak, ardından gerekirse tüm ağacı
  taşımak daha güvenli bir sıra.

**Pratik sonuç — bir sonraki gerçek iş:** `G001`-`G003` (hedef donanım kaydı,
Profilo HDMI giriş keşfi, canlı HDMI üstüne sabit Türkçe yazı denemesi).
Bunlar kod yazmadan önce **gerçek Profilo TV + Xbox'ta** elle/spike ile
denenmesi gereken adımlar; bu kum havuzunda donanım erişimi olmadığı için
burada "tamamlandı" işaretlenemez — kullanıcının kendi TV'sinde yapılması
gerekir (bkz. `docs/roadmap/tasks/todo.md` G001-G003 kabul/doğrulama
kriterleri).

## Mimari pivotu (2026-11-09, ilk belge)

Bu depo başlangıçta mikrofonla TV/konsol sesini dinleyip buluta gönderen bir
Flutter uygulamasıydı. Yol haritası araştırması, gerçek Game Caption
mekanizmasının mikrofon/ASR değil **ekran OCR'ı** olduğunu doğruladı. Buna
göre:

- **Aktif geliştirme** artık bu dizindeki **native Kotlin + Jetpack Compose**
  projesindedir (aşağıya bakın).
- Eski Flutter/mikrofon+bulut-ASR MVP'si **dondurulmuştur** ve
  [`legacy-flutter-mic-stt/`](legacy-flutter-mic-stt/) altında arşivlenmiştir
  — yalnızca referans/mimari ders amaçlı, üzerinde geliştirme yapılmıyor.

## Kapsam (özet)

- **Yakalama:** kamerayı ekrana doğrultma (cihaz-bağımsız); konsol bağlantısı
  (PS5/PS4/Xbox) ileride, lisans kararı onaylandıktan sonra.
- **Görüntüleme:** telefon ekranı (bu ilk sürümün tek hedefi); Android TV,
  Samsung Tizen, LG webOS ileride (karar kapıları G0 vb.).
- **Gizlilik:** kamera görüntüsü asla sunucuya yüklenmez; yalnız OCR ile
  çıkarılan altyazı metni, çeviri için kullanılır (varsayılan: ML Kit
  cihaz-üstü, tamamen çevrimdışı).

Tam kapsam, karar kapıları (G0-G5), riskler ve fazlı yol haritası (P0-P6) için
`GAMLETSOL — Yol Haritası ve Mimari Şema.md` dosyasına bakın.

## Mimari (native çekirdek)

```
gamletsol/
├─ app-mobile/        # Android uygulaması: Compose UI, CameraX önizleme,
│                      # ML Kit OCR/çeviri adaptörleri, TextToSpeech
├─ core-model/         # Saf Kotlin — Cue, FrameRef, TextRegion, Session...
├─ core-pipeline/      # Saf Kotlin — FrameSource/TextRecognizer/Translator/
│                      # SubtitleSink/SpeechOutput arayüzleri + CaptionPipeline
├─ feature-capture/    # Android kütüphanesi — CameraX tabanlı CameraSource
└─ legacy-flutter-mic-stt/  # DONDURULMUŞ eski Flutter/mikrofon+ASR MVP'si
```

`core-model` ve `core-pipeline` platformdan bağımsız saf Kotlin/JVM
modülleridir — Android SDK olmadan da derlenip test edilebilirler.
`feature-capture` ve `app-mobile` Android modülleridir (CameraX, ML Kit,
Compose), bu nedenle yalnızca gerçek bir Android SDK ile (yerelde Android
Studio, CI'da GitHub'ın hazır SDK'lı ubuntu-latest imajı) derlenebilirler.

Henüz eklenmeyen modüller (ilgili karar kapısı onaylandığında veya ihtiyaç
ortaya çıktığında eklenecek) — isimler güncel yol haritasının (`docs/roadmap/`)
önerdiği adlarla hizalandı: `app-tv/probe` (**sıradaki asıl iş — G001-G006,
P0**), `core-transport` (TLS+QR eşleştirme, P3/G023-G024), `data-local`
(Room/DataStore — kalıcı ayar/sözlük/trusted-devices, P2-P3), `feature-session`
/`feature-tv-connect` (TV eşleştirme/QR, P3), `receiver-tizen`, `receiver-web`,
`source-playstation`, `source-xbox` (X1-X4, koşullu), `backend/*` (P6-P7).

## İlk sürümün yaptığı (ve yapmadığı)

**Yapıyor (bu PR'daki ilk iskelet):**
- CameraX ile kamera karelerini yakalar (`KEEP_ONLY_LATEST`).
- ML Kit Latin Text Recognition ile cihaz-üstü OCR.
- OCR çıktısını normalize eder, ardışık iki kare arasında fuzzy eşleşmeyle
  "kararlı" metni belirler (tek bir yanlış OCR okuması yeni bir altyazı
  doğurmaz).
- Kararlı metni ML Kit Translation (cihaz-üstü, önceden indirilen en↔tr
  modeli) ile çevirir, kimlik takibi (`cueId`/`seq`/TTL) ile tutarlı şekilde
  telefon ekranında gösterir ve Türkçe TTS ile okur.

**Henüz yapmıyor (bilinçli olarak sonraki fazlara bırakıldı — dürüstlük
ilkesi):**
- Gerçek görüntü düzeyinde ROI kırpma/döndürme/perspektif düzeltme — ilk
  sürüm tam kareyi OCR'a verip bölgeye göre metin bloklarını filtreliyor
  (daha basit, daha pahalı; bkz. `MlKitTextRecognizer` yorumları).
- Kare-öncesi değişim algılama (OCR'ı her karede çalıştırmamak) — P2.
- TV'ye yansıtma, konsol bağlantısı, Akıllı Ekran, dublaj paketleri — bkz.
  yol haritası P3-P6.
- Kalıcı ayarlar/oyun profilleri (`data-local`/Room).

## Doğrulama durumu (dürüstlük ilkesi)

- [x] `core-model` ve `core-pipeline`: bu kum havuzunda **gerçekten
      derlendi ve test edildi** (`./gradlew :core-model:test
      :core-pipeline:test` — 9 birim testi (2 + 7), hepsi geçti; JDK 21 ile,
      CI'da ayrıca JDK 17 ile doğrulanacak).
- [ ] `feature-capture` ve `app-mobile`: **bu kum havuzunda yerel olarak
      DERLENMEDİ** — ortamda Android SDK yok ve SDK lisanslarını kabul etme
      izni bu oturumda verilmedi. Kod, resmi CameraX/ML Kit/Compose
      dokümantasyonuna göre yazıldı ama derlemesi doğrulanmadı; bu, CI'nın
      (`.github/workflows/android.yml`, GitHub'ın hazır Android SDK'lı
      runner'ında çalışır) ilk görevidir.
- [ ] Gerçek bir Android cihazda kamera + OCR + çeviri + TTS uçtan uca hiç
      denenmedi.
- [ ] Profilo Android TV'de HDMI passthrough üzerine yazı çizme (artık
      **G001-G006, P0** — güncel yol haritasının *tek* önceliği) henüz
      araştırılmadı/kodlanmadı — bu ilk iskelet yalnızca telefon ekranını
      hedefliyor. Bu kapı geçilmeden TV üstü çeviri özelliği "çalışıyor"
      sayılmaz; ayrıntı için `docs/roadmap/tasks/todo.md` P0 bölümü.

## Geliştirme

```bash
./gradlew :core-model:test :core-pipeline:test   # platformdan bağımsız birim testleri
./gradlew :app-mobile:assembleDebug              # debug APK (Android SDK gerektirir)
```

Legacy Flutter projesi için bkz.
[`legacy-flutter-mic-stt/README.md`](legacy-flutter-mic-stt/README.md).
