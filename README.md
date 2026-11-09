# GAMLETSOL — Game Translate Console

Oyunların ekranda zaten gösterdiği İngilizce altyazıyı **OCR ile okuyup**
Türkçeye çeviren Android uygulaması. Mimari ve yol haritası ayrıntıları için
bkz. proje dokümanı: `GAMLETSOL — Yol Haritası ve Mimari Şema.md`.

## Mimari pivotu (2026-11-09)

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
ortaya çıktığında eklenecek): `data-local` (Room/DataStore — kalıcı
ayar/sözlük gerektiğinde), `feature-session` (TV eşleştirme/QR, P3),
`app-tv`, `receiver-web`, `source-playstation`, `source-xbox`, `backend`
(P6).

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
- [ ] Profilo Android TV'de HDMI passthrough üzerine yazı çizme (G0) henüz
      araştırılmadı/kodlanmadı — bu ilk iskelet yalnızca telefon ekranını
      hedefliyor.

## Geliştirme

```bash
./gradlew :core-model:test :core-pipeline:test   # platformdan bağımsız birim testleri
./gradlew :app-mobile:assembleDebug              # debug APK (Android SDK gerektirir)
```

Legacy Flutter projesi için bkz.
[`legacy-flutter-mic-stt/README.md`](legacy-flutter-mic-stt/README.md).
