# GAMLETSOL geliştirme görevleri

9 Kasım 2026 · Sürüm 1 · **72 planlanmış iş; tamamlanmış uygulama işi yok.**

Bu liste tek geliştirici, Xbox Series X/S ve 75 inç Profilo Android TV için hazırlanmıştır. Tam TV modeli/firmware ve telefon modeli bilinmiyor. Hiçbir fiziksel çalışma testi yapılmadı. Öncelik, Profilo’da canlı HDMI üstüne sabit Türkçe yazıyı kanıtlamaktır; bu geçmeden TV üstü çeviri özelliği tamamlandı denemez.

Modül/konum isimleri **önerilen** yerlerdir; mevcut bir repo veya dosya varlığı iddia edilmez. S = sınırlı iş; M = küçük özellik dilimi; Spike = zaman sınırlı deney/karar. M işleri ve büyük spike’lar kodlamadan önce 1–2 saatlik uygulama alt işlerine ayrılır. Faz saati; geliştirme, entegrasyon, cihaz deneyi, hata düzeltme ve dokümantasyonu kapsar, satır başına saat değildir. Kontrol noktaları takvime ek ayrı iş sayısı değildir.

Bağımlılık başarısızsa bağlı özellik **bekler veya kapsam değişir**; yalnız araştırmanın bitmesi teknik kapıyı geçmiş sayılmaz. X fazları koşulludur; ana ürünü hepsinin bitişine bağlamayın. G072’de yalnız ürüne gerçekten eklenen genişleme yolları zorunludur; iptal edilen yol gerekçesiyle kapsam dışı işaretlenir.

| Kapsam | Net efor | %25 payla | 25 saat/hafta |
|---|---:|---:|---:|
| P0–P5 çekirdek | 550 saat | 687,5 saat | 27,5 hafta |
| P0–P7 bulut + ödeme dahil | 750 saat | 937,5 saat | 37,5 hafta |
| Bütün koşullu genişlemeler | Ayrı PoC sonrası tahmin | Ortak planlama bandı | Yaklaşık 18–30 ay veya daha fazla |

25 saat/hafta kullanıcı tarafından doğrulanmış kapasite değil, planlama varsayımıdır. Öğrenme, izin, mağaza ve dış hak beklemeleri ek takvim yaratabilir. İlk 50 saat önemli deneyler içindir; garanti teslim tarihi değildir.

[Plan indeksi](plan.md) · [Ana yol haritası](../GAMLETSOL%20geliştirme%20yol%20haritası.md) · [Mimari SVG](../GAMLETSOL_mimari.svg)

## P0 — Fiziksel cihaz kanıtı ve ilk karar

Faz bütçesi: **50 net saat**.

- [ ] **G001 — Hedef donanım ve deney kaydı** (S)

  **Önerilen konum:** `docs/compatibility` · **Bağımlılık:** —

  **Kabul:** Profilo tam model/firmware, Android build, Xbox modeli, telefon ve ağ kaydı mevcut; bilinmeyenler işaretli.

  **Doğrulama:** TV ve telefon ayarlarını kontrol et; model etiketini deney kaydıyla karşılaştır.

- [ ] **G002 — Profilo HDMI girişlerini keşfet** (Spike)

  **Önerilen konum:** `app-tv/probe` · **Bağımlılık:** G001

  **Kabul:** TvInputManager giriş listesi ve OEM passthrough HDMI kimlikleri kaydedilmiş; erişim hatası da geçerli sonuç.

  **Doğrulama:** Fiziksel TV test APK’sıyla Xbox’ın bağlı olduğu portu ve izin durumunu doğrula.

- [ ] **G003 — Canlı HDMI üstüne sabit Türkçe yazı** (Spike)

  **Önerilen konum:** `app-tv/probe` · **Bağımlılık:** G002

  **Kabul:** TvView/passthrough görüntüsüyle Türkçe metin aynı anda 30 dakika görünür veya desteklenmediği kanıtlanır.

  **Doğrulama:** Gerçek Xbox HDMI görüntüsü, geri tuşu, giriş değişimi ve kaynak kaybını dene; emülatör sonucu yeterli değil.

> **Kontrol noktası P0-1 (G001–G003):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G004 — Görüntü modu ve gecikme tabanı** (Spike)

  **Önerilen konum:** `docs/compatibility` · **Bağımlılık:** G003

  **Kabul:** Native HDMI ile uygulama içi HDMI karşılaştırılmış; 1080p60, HDR, 4K, VRR/ALLM ayrı bilinçli durumlarla kayıtlı.

  **Doğrulama:** Ölçüm düzeneğiyle gecikmeyi karşılaştır; ölçülemeyen 4K120/VRR için destek iddiası yazma.

- [ ] **G005 — TV dağıtımı ve ürün devam kararı** (Spike)

  **Önerilen konum:** `docs/decisions` · **Bağımlılık:** G003,G004

  **Kabul:** Test kurulumu ve tüketici dağıtımı ayrılmış; TV vaadi için devam/ertele kararı ve sınırlı ilk kapsam yazılı.

  **Doğrulama:** Play TV gereksinimlerini yayın öncesi doğrula; root/sistem imzası şartsa ana TV kapısını başarısız işaretle.

- [ ] **G006 — Xbox resmî kaynak erişimi ön deneyi** (Spike)

  **Önerilen konum:** `source-projection/probe` · **Bağımlılık:** G001

  **Kabul:** Resmî Xbox tarayıcı akışı ve izinli MediaProjection için erişilebilir/korumalı kare sonucu; kontrolcü ve HDMI etkileşimi kaydı.

  **Doğrulama:** Kullanıcı onaylı kısa oturum dene; giriş sayfası/çerez toplamadan ve korumayı aşmadan sonucu kaydet.

> **Kontrol noktası P0-2 (G004–G006):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P1 — Tek kareden TV’ye dikey dilim

Faz bütçesi: **75 net saat**.

- [ ] **G007 — Repo, araç zinciri ve temel proje** (S)

  **Önerilen konum:** `build-logic / app-mobile` · **Bağımlılık:** G005

  **Kabul:** Önerilen Kotlin Android proje iskeleti tek telefonda açılır; SDK seçimi ve bağımlılıklar kaydedilmiş.

  **Doğrulama:** Gerçek Gradle görevlerini projeden bulup debug derleme ve başlangıç kontrolü çalıştır.

- [ ] **G008 — Kaynak ve altyazı olay sözleşmesi** (S)

  **Önerilen konum:** `core-model` · **Bağımlılık:** G007

  **Kabul:** FrameSource, cue/epoch/sequence ve SubtitleSink sözleşmesi minimum alanlarla sürümlü.

  **Doğrulama:** Sahte kaynak ve sink ile tek olayı taşı; bilinmeyen şema sürümünü reddet.

- [ ] **G009 — Tek kare OCR dikey dilimi** (M)

  **Önerilen konum:** `core-ocr` · **Bağımlılık:** G008

  **Kabul:** İzinli örnek resimde Latin metin ve kutular çıkar; okunamayan içerik uydurulmaz.

  **Doğrulama:** Kaynak metni elle doğrulanmış küçük örnek kümesiyle karakter hatasını ölç.

> **Kontrol noktası P1-1 (G007–G009):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G010 — Yerel İngilizce–Türkçe çeviri** (M)

  **Önerilen konum:** `core-translation` · **Bağımlılık:** G009

  **Kabul:** Model indirilince en→tr uçak modunda çalışır; model eksikliği anlaşılır hatadır.

  **Doğrulama:** Model hazır/yok/ağ kapalı üç durumu gerçek telefonda dene.

- [ ] **G011 — TV’ye sentetik cue dikey dilimi** (M)

  **Önerilen konum:** `app-tv/render / core-model` · **Bağımlılık:** G003,G008

  **Kabul:** Test metni sürümlü olaydan çizilir; font, Türkçe karakter ve temizleme çalışır.

  **Doğrulama:** Aşırı uzun metin, çok satır, ı/İ/ş/ğ ve clear olayını fiziksel TV’de doğrula.

- [ ] **G012 — İlk uçtan uca ölçüm altyapısı** (S)

  **Önerilen konum:** `core-metrics` · **Bağımlılık:** G009,G010,G011

  **Kabul:** Kare/OCR/çeviri/render süreleri içeriksiz ölçülür; test verisi ve cihaz sürümü kayıtlı.

  **Doğrulama:** Tek kare çeviriyi TV’de göster; metin veya görüntünün loga yazılmadığını kontrol et.

> **Kontrol noktası P1-2 (G010–G012):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P2 — Canlı kamera ve güvenilir OCR hattı

Faz bütçesi: **125 net saat**.

- [ ] **G013 — CameraX önizleme ve son-kare analizi** (M)

  **Önerilen konum:** `feature-capture` · **Bağımlılık:** G009

  **Kabul:** Analiz kuyruğu sınırlı; ImageProxy her yolda kapanır ve eski kare birikmez.

  **Doğrulama:** Yavaş OCR simülasyonu ve 30 dakikalık kamera oturumunda bellek/kare davranışını izle.

- [ ] **G014 — İzin ve kamera yaşam döngüsü** (M)

  **Önerilen konum:** `feature-capture/lifecycle` · **Bağımlılık:** G013

  **Kabul:** İzin reddi, geri dönüş, döndürme ve durdurmada kamera kaynakları bırakılır.

  **Doğrulama:** İzin iptali, uygulama arka planı ve tekrar açmada kontrollü durum geçişlerini dene.

- [ ] **G015 — ROI ve görüntü dönüşümleri** (M)

  **Önerilen konum:** `core-transform` · **Bağımlılık:** G013

  **Kabul:** Manuel altyazı alanı rotasyon/crop üzerinden doğru kaynak koordinatlarına eşlenir.

  **Doğrulama:** Portre/yatay, farklı çözünürlük ve letterbox örneklerinde kutu hatasını ölç.

> **Kontrol noktası P2-1 (G013–G015):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G016 — Kamera hareketi ve kalibrasyon durumu** (M)

  **Önerilen konum:** `feature-capture/calibration` · **Bağımlılık:** G015

  **Kabul:** Perspektif kalibrasyonu veya sınırlı destek açısı açık; hareket sonrası kullanıcı yeniden yönlendirilir.

  **Doğrulama:** Eğik açı, odak kaybı ve tutucu hareketini dene; geçersiz kutuyu kullanma.

- [ ] **G017 — Satır kararlılığı ve tekrar eleme** (M)

  **Önerilen konum:** `core-pipeline/stabilizer` · **Bağımlılık:** G013,G015

  **Kabul:** Yavaş yazılan/kısmen okunan cue kararlılaşır; aynı replik başka sahnede sonsuza kadar bastırılmaz.

  **Doğrulama:** Typewriter metin, iki satır, OCR titremesi ve sahnede tekrar için birim senaryoları çalıştır.

- [ ] **G018 — Epoch ve eski çeviri yanıtı iptali** (M)

  **Önerilen konum:** `core-pipeline/scheduler` · **Bağımlılık:** G008,G017

  **Kabul:** Kaynak/ROI değişince eski epoch sonucu ve TTS tetikleyicisi gösterilmez.

  **Doğrulama:** Ters sırada tamamlanan çeviriler, timeout ve kaynak değişimi testlerinde eski cue sayısı sıfır.

> **Kontrol noktası P2-2 (G016–G018):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G019 — Cache ve sözlük sürümü** (M)

  **Önerilen konum:** `data-local/cache` · **Bağımlılık:** G010,G018

  **Kabul:** Cache dil, oyun profili, sözlük ve model/bağlam sürümünü içerir; tekrarlar tek çeviri olur.

  **Doğrulama:** Aynı metin farklı oyun/bağlam ve sözlük güncellemesinde yanlış cache paylaşımı olmadığını doğrula.

- [ ] **G020 — Cue süresi ve kaynak kaybında temizleme** (M)

  **Önerilen konum:** `core-pipeline/expiry` · **Bağımlılık:** G018

  **Kabul:** Cue tolerans ve TTL sonunda temizlenir; kopma sonrası metin sonsuza kadar kalmaz.

  **Doğrulama:** Kamera kapatma, boş kare, geç yanıt ve duvar saati değişimiyle deterministic saat testleri.

- [ ] **G021 — Kamera geri besleme koruması** (M)

  **Önerilen konum:** `feature-capture/masking` · **Bağımlılık:** G015,G016,G020

  **Kabul:** Türkçe alan İngilizce kaynağı kapatmaz; çıktı kutuları OCR’dan dışlanır.

  **Doğrulama:** Aynı TV’yi kamera çekerken 30 dakika çevirinin kendini tekrar çevirmediğini ve yeni cue’yu gördüğünü doğrula.

> **Kontrol noktası P2-3 (G019–G021):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P3 — Native TV alıcısı ve güvenli LAN

Faz bütçesi: **100 net saat**.

- [ ] **G022 — TV capability sözleşmesi ve destek kaydı** (S)

  **Önerilen konum:** `app-tv/capabilities` · **Bağımlılık:** G003,G008

  **Kabul:** Model/firmware, HDMI-compose ve render yetenekleri alıcı tarafından bildirilir; destek bilinmiyorsa deneysel.

  **Doğrulama:** Bilinmeyen/uyumsuz alıcı için başlangıç ekranında doğru sınır ve hata görülür.

- [ ] **G023 — Tek kullanımlık QR eşleştirme** (M)

  **Önerilen konum:** `core-pairing` · **Bağımlılık:** G022

  **Kabul:** Kısa ömürlü rastgele token, alıcı kimliği ve fingerprint doğrulanır; token tekrar kullanılamaz.

  **Doğrulama:** Süresi geçmiş QR, ikinci yayıncı, yanlış TV ve tekrar denemesinin reddini test et.

- [ ] **G024 — Şifreli kimliği doğrulanan LAN kanalı** (M)

  **Önerilen konum:** `core-transport` · **Bağımlılık:** G023

  **Kabul:** TLS kimliği QR güven bilgisiyle doğrulanır; tüm sertifikaları kabul eden yol yok.

  **Doğrulama:** Yanlış fingerprint, sahte endpoint ve bağlantı kesilmesi senaryolarını yerel test ortamında dene.

> **Kontrol noktası P3-1 (G022–G024):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G025 — Keşif ve yerel ağ izinleri** (M)

  **Önerilen konum:** `feature-tv-connect` · **Bağımlılık:** G024

  **Kabul:** NSD başarısızsa manuel QR/IP yolu ve anlaşılır ağ teşhisi vardır; güncel LAN izni hesaba katılır.

  **Doğrulama:** Misafir ağ, AP isolation, kablolu TV/Wi-Fi telefon ve izin reddini dene.

- [ ] **G026 — Olay doğrulama ve sıra güvenliği** (M)

  **Önerilen konum:** `app-tv/protocol` · **Bağımlılık:** G024

  **Kabul:** Boyut sınırı, şema, sequence, epoch ve revision kontrol edilir; metin HTML çalıştırmaz.

  **Doğrulama:** Bozuk JSON, aşırı uzun cue, tekrar/reorder, özel HTML karakterleri ve eski sürüm testleri.

- [ ] **G027 — TV yazı yerleşimi ve okunabilirlik** (M)

  **Önerilen konum:** `app-tv/render` · **Bağımlılık:** G011,G026

  **Kabul:** Font/konum/arka plan/safe-area ayarları kumandayla kullanılabilir; kaynak alanı korunur.

  **Doğrulama:** 3 metre izleme, büyük font, uzun cümle ve overscan benzetimiyle kullanıcı kontrolü.

> **Kontrol noktası P3-2 (G025–G027):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G028 — Reconnect, snapshot ve süre sonu** (M)

  **Önerilen konum:** `core-transport/session` · **Bağımlılık:** G020,G026

  **Kabul:** Reconnect yalnız güncel snapshot alır; eski olaylar/TTS tekrar oynatılmaz; saatler doğrudan eşit varsayılmaz.

  **Doğrulama:** 10 saniye ağ kaybı, uyku/uyanma, receiver restart ve saat farkında TTL/clear doğrula.

- [ ] **G029 — Eşleştirmeyi hatırlama ve iptal** (M)

  **Önerilen konum:** `data-local/trusted-devices` · **Bağımlılık:** G023,G028

  **Kabul:** Kalıcı güven kullanıcı seçimiyle saklanır; cihaz iptalinde anahtar erişimi biter.

  **Doğrulama:** TV’yi unut, uygulamayı yeniden kur ve eski token ile bağlanmayı dene; loglarda sır yok.

- [ ] **G030 — Profilo TV ilk kabul matrisi** (M)

  **Önerilen konum:** `qa/tv-acceptance` · **Bağımlılık:** G021,G027,G028,G029

  **Kabul:** 10 güvenli eşleşme, 60 dakika canlı kamera→TV ve kopmada temizleme kayıtlı.

  **Doğrulama:** Gerçek Xbox+Profilo üzerinde foto/video kanıtını yalnız kullanıcının test izniyle yerel tut; ölçümleri tabloya işle.

> **Kontrol noktası P3-3 (G028–G030):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P4 — Kalite, TTS ve uzun oturum

Faz bütçesi: **100 net saat**.

- [ ] **G031 — Oyun profili ve terim sözlüğü** (M)

  **Önerilen konum:** `feature-profiles` · **Bağımlılık:** G019

  **Kabul:** Üç oyun türü için ROI/terim/isim ayarı; profil silme ve sürüm değişimi güvenli.

  **Doğrulama:** Sayılar, butonlar, HP/Armor ve isimleri örneklerde korunduğunu kontrol et.

- [ ] **G032 — Türkçe TTS motoru ve ses seçimi** (M)

  **Önerilen konum:** `feature-speech` · **Bağımlılık:** G010,G018

  **Kabul:** Kurulu Türkçe sesler/ağ gereksinimi gösterilir; ses yoksa altyazı çalışmaya devam eder.

  **Doğrulama:** En az iki telefonda çevrimdışı ses, eksik ses ve ağ isteyen ses durumlarını dene.

- [ ] **G033 — TTS güncellik ve ses odağı** (M)

  **Önerilen konum:** `feature-speech/queue` · **Bağımlılık:** G032,G020

  **Kabul:** Eski konuşma kuyruğu birikmez; durdur/son cue tercihi ve kulaklık çıkışı çalışır.

  **Doğrulama:** Hızlı diyalog, Bluetooth gecikmesi, arama/ses odağı kaybı ve kaynak kopması testleri.

> **Kontrol noktası P4-1 (G031–G033):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G034 — OCR ve çeviri kalite veri kümesi** (M)

  **Önerilen konum:** `qa/quality-fixtures` · **Bağımlılık:** G021,G031

  **Kabul:** İzinli gold metinler ve görülmemiş test sahneleri ayrılmış; OCR/çeviri hata sınıfları mevcut.

  **Doğrulama:** Kamera koşullarını sabitleyip CER ve en az 100 cue için kör anlam değerlendirmesi raporla.

- [ ] **G035 — Gizlilik ve ağ akışı denetimi** (M)

  **Önerilen konum:** `qa/privacy` · **Bağımlılık:** G024,G032

  **Kabul:** Görüntü/ses upload’u ve içerik log’u yok; SDK teknik veri akışı beyana dahil.

  **Doğrulama:** Kontrollü ağ trafiği, crash kayıtları ve cihaz depolamasını incele; sırların çıktısını paylaşma.

- [ ] **G036 — Termal, pil ve performans uyarlaması** (M)

  **Önerilen konum:** `core-pipeline/performance` · **Bağımlılık:** G030,G034

  **Kabul:** 60 dakika crash yok; 120 dakika pil/ısı/RAM raporu, yük artınca OCR frekansı kontrollü düşer.

  **Doğrulama:** Orta sınıf telefonlarla gecikme p50/p95 ve uzun oturum profilini ölç; hedefleri sonuçla yeniden değerlendir.

> **Kontrol noktası P4-2 (G034–G036):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P5 — Kapalı beta ve çekirdek yayın

Faz bütçesi: **100 net saat**.

- [ ] **G037 — Onboarding ve oturum ekranları** (M)

  **Önerilen konum:** `feature-onboarding / feature-session` · **Bağımlılık:** G030,G036

  **Kabul:** İzin→kamera hizalama→TV eşleştirme→test yazısı→oyna akışı tamamlanır; büyük durdur düğmesi vardır.

  **Doğrulama:** İlk kez kullanan biriyle yardım vermeden akışı dene; hata metinlerini gözden geçir.

- [ ] **G038 — Erişilebilirlik ve durum mesajları** (M)

  **Önerilen konum:** `app-mobile/ui / app-tv/ui` · **Bağımlılık:** G037

  **Kabul:** TalkBack etiketleri, odak, büyük font ve kumanda erişimi; çevrimdışı/deneysel durum açık.

  **Doğrulama:** Gerçek telefon+TV’de ekran okuyucu, font büyütme ve yalnız kumandayla dolaşım kontrolü.

- [ ] **G039 — CI ve sürüm paketleme** (M)

  **Önerilen konum:** `ci / build-logic` · **Bağımlılık:** G007,G018,G026

  **Kabul:** Gerçek proje görevleriyle lint/unit/build çalışır; sürüm ve imzalama sırları korumalıdır.

  **Doğrulama:** Temiz ortamda derle; hatalı çekirdek testi yayını engeller; artefakt sürümünü doğrula.

> **Kontrol noktası P5-1 (G037–G039):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G040 — Beta dağıtımı ve desteklenen cihaz listesi** (M)

  **Önerilen konum:** `release/beta / docs/compatibility` · **Bağımlılık:** G035,G038,G039

  **Kabul:** 10–20 pilot için kurulum ve destek akışı; cihaz matrisi ve kritik hata kayıtları mevcut.

  **Doğrulama:** Play hesap tipine uygun güncel kapalı test şartını kontrol et; gerçek kullanıcı oturumlarını incele.

- [ ] **G041 — Gizlilik, yardım ve mağaza sunumu** (M)

  **Önerilen konum:** `docs/public / web-support` · **Bağımlılık:** G035,G040

  **Kabul:** Gerçek veri akışı, sınırlamalar, destek adresi ve özgün ürün metni hazırlanmış; yalnız kanıtlı özellikler yazılı.

  **Doğrulama:** Mağaza Data Safety ile SDK/ağ raporunu karşılaştır; bilinmeyen Profilo modellerine destek vaadi olmadığını kontrol et.

- [ ] **G042 — Çekirdek yayın adayı ve geri dönüş planı** (M)

  **Önerilen konum:** `release/core` · **Bağımlılık:** G036,G040,G041

  **Kabul:** Ana cihaz akışında kritik hata yok; sürüm geri alma/destek yolu ve kabul kanıtı hazır.

  **Doğrulama:** Tam kabul senaryosunu temiz kurulumda tekrarla; TV kapısı geçmediyse TV MVP tamamlandı işaretleme.

> **Kontrol noktası P5-2 (G040–G042):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P6 — İsteğe bağlı bulut çeviri ve hesap altyapısı

Faz bütçesi: **100 net saat**.

- [ ] **G043 — Bulut sağlayıcı kalite/maliyet seçimi** (Spike)

  **Önerilen konum:** `backend/translation-gateway` · **Bağımlılık:** G034,G042

  **Kabul:** Aynı veri kümesinde kalite/gecikme/karakter maliyeti karşılaştırılmış; saklama koşulları kayıtlı.

  **Doğrulama:** En az iki erişilebilir seçeneği bütçeli örnekle değerlendir; güncel resmî fiyatı ve tarihini kaydet.

- [ ] **G044 — Hesap ve sunucu güven temeli** (M)

  **Önerilen konum:** `backend/auth / database` · **Bağımlılık:** G043

  **Kabul:** Kullanıcı/device ilişkisi ve minimum veri modeli; sağlayıcı sırları yalnız sunucuda.

  **Doğrulama:** Yetkisiz çapraz kullanıcı erişimi ve token süresi testlerini çalıştır; migration geri dönüşünü dene.

- [ ] **G045 — Metin çeviri gateway sözleşmesi** (M)

  **Önerilen konum:** `backend/translations` · **Bağımlılık:** G044

  **Kabul:** Yalnız metin+sınırlı bağlam, katı şema, uzunluk sınırı ve deadline; oyun metni talimat kabul edilmez.

  **Doğrulama:** Bozuk girdi, HTML, aşırı bağlam ve prompt-benzeri oyun cümlelerinin yalnız çeviri üretmesini test et.

> **Kontrol noktası P6-1 (G043–G045):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G046 — Atomik kota ve kullanım muhasebesi** (M)

  **Önerilen konum:** `backend/usage-ledger` · **Bağımlılık:** G045

  **Kabul:** requestId UNIQUE; rezervasyon/kesinleştirme tek kez; kullanıcı/cihaz/global harcama freni var.

  **Doğrulama:** Aynı isteği eşzamanlı gönder; timeout sonrası retry ve sağlayıcı faturalanmış belirsiz sonucu dene.

- [ ] **G047 — Bulut hatası ve yerel fallback** (M)

  **Önerilen konum:** `core-translation/router` · **Bağımlılık:** G010,G018,G046

  **Kabul:** 429/timeout/5xx anlaşılır; yalnız güncel cue yerel fallback alır; sınırsız retry yok.

  **Doğrulama:** Sağlayıcı hata enjeksiyonu, offline geçiş ve geç gelen cloud yanıtında eski çeviriyi reddet.

- [ ] **G048 — Maliyet gözlemi ve bulut beta kapısı** (M)

  **Önerilen konum:** `backend/metrics / qa/cloud` · **Bağımlılık:** G035,G047

  **Kabul:** 20–30 beta oturumunda p50/p95 kullanıcı maliyeti; kota ve saklama metni gerçek davranışa uygun.

  **Doğrulama:** Sayaçları sağlayıcı ölçümüyle uzlaştır; bütçe hard-cap gerçekten çağrıyı durduruyor mu dene.

> **Kontrol noktası P6-2 (G046–G048):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## P7 — Ödeme ve işletilebilir ücretli ürün

Faz bütçesi: **100 net saat**.

- [ ] **G049 — Ürün ve abonelik yetki modeli** (S)

  **Önerilen konum:** `backend/entitlements` · **Bağımlılık:** G048

  **Kabul:** Ücretsiz/yerel/bulut/Pro hakları ve şeffaf limitler tanımlı; sınırsızlık vaadi ölçümsüz yok.

  **Doğrulama:** Plan değişimi ve limit sınırlarında özelliklerin doğru açılıp kapandığını tabloyla doğrula.

- [ ] **G050 — Play Billing istemci akışı** (M)

  **Önerilen konum:** `feature-billing` · **Bağımlılık:** G049

  **Kabul:** Satın al, bekleyen ödeme, iptal ve geri yükleme UI durumları ayrılmış.

  **Doğrulama:** Play test hesaplarıyla başarı/iptal/pending senaryolarını gerçek dağıtılmış test paketinde dene.

- [ ] **G051 — Sunucuda satın alma doğrulama** (M)

  **Önerilen konum:** `backend/purchases` · **Bağımlılık:** G044,G050

  **Kabul:** Haklar istemci beyanından açılmaz; bildirim tekrarları tekil işlenir ve mağaza durumu esas alınır.

  **Doğrulama:** Sahte purchase token, tekrar bildirim, yeniden kurulum ve başka hesapla kullanım testleri.

> **Kontrol noktası P7-1 (G049–G051):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G052 — Abonelik yaşam döngüsü** (M)

  **Önerilen konum:** `backend/subscription-state` · **Bağımlılık:** G051

  **Kabul:** Yenileme, grace/hold, süre sonu, iade ve revoke doğru yetkiye dönüşür.

  **Doğrulama:** Mağaza test akışlarıyla durum geçişlerini ve geciken/sırasız bildirimleri test et.

- [ ] **G053 — Hesap/veri silme ve iptal ayrımı** (M)

  **Önerilen konum:** `backend/deletion / web-support` · **Bağımlılık:** G044,G052

  **Kabul:** Uygulama ve webden silme başlatılır; gerekli saklama istisnaları açık; abonelik iptalinin ayrı olduğu belirtilir.

  **Doğrulama:** Hesap sil→token iptal→cihaz ayrıştır→veri temizleme zinciri; yedeklerden geri yüklemede silme kaydı testi.

- [ ] **G054 — Ücretli yayın ve işletim kontrolü** (M)

  **Önerilen konum:** `ops / release/paid` · **Bağımlılık:** G048,G052,G053

  **Kabul:** Yedek geri yükleme, alarm, provider kill-switch, destek ve güncel mağaza kontrolleri tamam.

  **Doğrulama:** Test ortamında DB restore, kota kesmesi, sürüm rollback ve satın alma uzlaştırmasını uygula.

> **Kontrol noktası P7-2 (G052–G054):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X1 — Xbox kaynaklarının genişletilmesi

Koşullu genişleme: erişim, cihaz, lisans ve önceki kapıların sonucuna göre yeniden tahmin edilir.

- [ ] **G055 — Xbox yakalama kararını yeniden doğrula** (Spike)

  **Önerilen konum:** `source-projection` · **Bağımlılık:** G006,G042

  **Kabul:** Güncel resmî tarayıcı/MediaProjection kombinasyonu gerçek telefonda erişilebilir kare verir veya açıkça reddedilir.

  **Doğrulama:** Korumalı/boş kare, giriş sayfası dışlama ve ekran kilidi durma davranışını kaydet.

- [ ] **G056 — MediaProjection FrameSource** (M)

  **Önerilen konum:** `source-projection/lifecycle` · **Bağımlılık:** G008,G055

  **Kabul:** Her oturum onayı, doğru foreground service, onStop temizliği; token tekrar kullanılmaz.

  **Doğrulama:** Başlat/durdur 10 döngü, ekran kilidi, rotasyon ve kullanıcı iptalinde kaynaklar bırakılır.

- [ ] **G057 — Xbox kontrolcü ve HDMI birlikteliği** (M)

  **Önerilen konum:** `qa/xbox` · **Bağımlılık:** G056

  **Kabul:** Konsolda fiziksel kontrolcüyle oynama ve TV HDMI sürekliliği doğrulanmış kombinasyonlarla belgeli.

  **Doğrulama:** Hesap değişimi, konsol uyku/uyanma, 60 dakika oturum ve ağ kesilmesini test et.

> **Kontrol noktası X1-1 (G055–G057):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

- [ ] **G058 — Doğrudan Xbox adaptörü lisans/erişim kararı** (Spike)

  **Önerilen konum:** `docs/decisions/xbox` · **Bağımlılık:** G057

  **Kabul:** Güncel kaynak/SDK bakım durumu ve AGPL/ticari izin stratejisi yazılı; çözümsüzse durdur.

  **Doğrulama:** Lisans envanteri ve entegrasyon biçimini incelet; JNI/ayrı süreç yükümlülüğü otomatik siler varsayma.

- [ ] **G059 — Xbox decoder erişimi teknik spike** (Spike)

  **Önerilen konum:** `source-xbox/probe` · **Bağımlılık:** G058

  **Kabul:** İzinli oturum ve decoder kareleri Android’de 30 dakika OCR’a erişebilir; ABI/codec riski kaydı.

  **Doğrulama:** Fiziksel Xbox’ta derle/bağlan/çöz/ayır; gizli API erişimini veya desteklenmeyen SDK’yı hazır sayma.

- [ ] **G060 — Xbox doğrudan kaynak adaptörü** (M)

  **Önerilen konum:** `source-xbox` · **Bağımlılık:** G059,G018,G020

  **Kabul:** Ortak FrameSource’a bağlanma/kopma/hata akışı; video ve kimlik bilgisi sızmıyor.

  **Doğrulama:** Küçük alt işlere böl; her alt işte fake source sözleşmesi, sonunda 60 dakika gerçek konsol testi.

> **Kontrol noktası X1-2 (G058–G060):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X2 — PlayStation ve küçük pencere

Koşullu genişleme: erişim, cihaz, lisans ve önceki kapıların sonucuna göre yeniden tahmin edilir.

- [ ] **G061 — PlayStation erişim ve lisans fizibilitesi** (Spike)

  **Önerilen konum:** `docs/decisions/playstation` · **Bağımlılık:** G060

  **Kabul:** PS4/PS5 test cihazı, resmî capture ve Chiaki-ng AGPL/Android bakım/ABI kararı ayrı kaydedilmiş.

  **Doğrulama:** Güncel birincil kaynak ve gerçek derleme spike’ı; Sony onaylı SDK gibi sunma.

- [ ] **G062 — PlayStation kaynak dilimi** (M)

  **Önerilen konum:** `source-playstation` · **Bağımlılık:** G061,G008

  **Kabul:** Tek doğrulanmış PS ailesi FrameSource üzerinden cue üretir; bağlantı bilgileri güvenli saklanır.

  **Doğrulama:** Eşleştirme, yanlış kod, tekrar bağlanma, kontrolcü+HDMI ve izinli kare erişimi testleri.

- [ ] **G063 — Kendi video ekranında PiP** (M)

  **Önerilen konum:** `feature-player/pip` · **Bağımlılık:** G060 veya G062

  **Kabul:** Yalnız uygulamanın kendi video activity’si küçük pencereye geçer; TV HDMI yetkisiyle karıştırılmaz.

  **Doğrulama:** Arka plan, rotasyon, PiP kapatma, geri dönüş ve kaynak kaybı durumlarını dene.

> **Kontrol noktası X2-1 (G061–G063):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X3 — Akıllı Ekran

Koşullu genişleme: erişim, cihaz, lisans ve önceki kapıların sonucuna göre yeniden tahmin edilir.

- [ ] **G064 — Akıllı Ekran durdur-çevir** (M)

  **Önerilen konum:** `feature-smart-screen` · **Bağımlılık:** G034,G015

  **Kabul:** Sabit menüde bloklar okunur/çevrilir; tuşlar ve sayısal değerler korunur; konsol oyunu durdu denmez.

  **Doğrulama:** 100 izinli menü örneğinde kutu/çeviri doğruluğu ve yanlış sayı hata sınıflarını incele.

- [ ] **G065 — Ekran dönüşümü ve blok render** (M)

  **Önerilen konum:** `core-transform / app-tv/layout` · **Bağımlılık:** G064,G027

  **Kabul:** Kaynak→ROI→TV normalize dönüşümü 1080p/4K/letterbox için tek zincir; bloklar taşmaz.

  **Doğrulama:** Sentetik geometriler ve fiziksel TV’de köşe/kenar kutuları; kamera modu kaynak örtme sınırını koru.

- [ ] **G066 — Sürekli blok takibi ve kalite kapısı** (M)

  **Önerilen konum:** `feature-smart-screen/tracking` · **Bağımlılık:** G065,G019,G020

  **Kabul:** Hareket eden kutuda çeviri yeniden gereksiz çağrılmaz; kaybolan kutu silinir; yük üst sınırı var.

  **Doğrulama:** Menü kaydırma, hızlı sahne değişimi, çok kutu ve termal test; savaş HUD desteğini kanıtsız açma.

> **Kontrol noktası X3-1 (G064–G066):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X4 — Samsung ve LG genişlemesi

Koşullu genişleme: erişim, cihaz, lisans ve önceki kapıların sonucuna göre yeniden tahmin edilir.

- [ ] **G067 — Samsung HDMI ve mağaza PoC** (Spike)

  **Önerilen konum:** `receiver-tizen/probe` · **Bağımlılık:** G042

  **Kabul:** TVWindow+BEHIND gerçek modelde çalışır; geliştirici sertifikası ve tüketici dağıtımı ayrılmış.

  **Doğrulama:** Samsung fiziksel TV, HDMI/Game Mode ve Seller Office koşullarını doğrula; telefondan kitlesel yüklemeyi varsayma.

- [ ] **G068 — Samsung protokol alıcısı** (M)

  **Önerilen konum:** `receiver-tizen` · **Bağımlılık:** G067,G026,G028

  **Kabul:** Ortak cue/clear/expiry, kumanda ve yeniden bağlantı çalışır; bağlantı yönü TV yeteneğine uyar.

  **Doğrulama:** Tizen lifecycle, WSS, Türkçe font ve HDMI+ses/kalite matrisi; model bazlı beta kaydı.

- [ ] **G069 — LG tarayıcı ve gerekirse relay fizibilitesi** (Spike)

  **Önerilen konum:** `receiver-web/probe / relay` · **Bağımlılık:** G042

  **Kabul:** 2021+ genellemesi yapılmadan gerçek modellerde destekli HDMI yolu/WebCrypto/WSS araştırılır; belirsizse deneysel kalır.

  **Doğrulama:** Tarayıcı restart, güvenli bağlam, HDMI+DOM ve şifreli relay tehdidini dene; TLS’ye E2EE adı verme.

> **Kontrol noktası X4-1 (G067–G069):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X5 — Ses paketleri ve uzun vadeli bakım

Koşullu genişleme: erişim, cihaz, lisans ve önceki kapıların sonucuna göre yeniden tahmin edilir.

- [ ] **G070 — Ses paketi hakları ve üretim örneği** (Spike)

  **Önerilen konum:** `content/voice-pipeline` · **Bağımlılık:** G033,G054

  **Kabul:** Tek oyun için metin/çeviri/ses hakları ve editör onayı; lisanslı sesle küçük cue örneği hazırlanır.

  **Doğrulama:** İzin kayıtları, telaffuz ve karakter/sahne eşleşmesini insan kontrolüyle doğrula; hak yoksa paket dağıtma.

- [ ] **G071 — Sürümlü ses paketi indirme ve oynatma** (M)

  **Önerilen konum:** `feature-voice-packs / backend/catalog` · **Bağımlılık:** G070

  **Kabul:** İmzalı manifest/hash, atomik kurulum, depolama sınırı ve sürüm geri dönüşü; yanlış cue’da altyazı/TTS fallback.

  **Doğrulama:** Bozuk/yarım indirme, disk dolu, farklı oyun sürümü ve aynı metnin farklı karakterde eşleşmesi testleri.

- [ ] **G072 — Geniş ürün bakım ve sürüm döngüsü** (M)

  **Önerilen konum:** `ops/compatibility / release` · **Bağımlılık:** G060,G063,G066,G068,G069,G071

  **Kabul:** Eklenen her kaynak×TV×firmware için destek durumu, yeniden test ve geri alma sahibi kayıtlı; deneysel yollar kapatılabilir.

  **Doğrulama:** Firmware/SDK güncellemesi sonrası risk odaklı regresyon; gizlilik, maliyet ve paket haklarını periyodik gözden geçir.

> **Kontrol noktası X5-1 (G070–G072):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## X6 — Oyun bazlı önceden hazırlanmış çeviri satır veritabanı

Koşullu genişleme: içerik stratejisi kararına bağlı, önceki kapıların sonucuna göre yeniden tahmin edilir. **Not:** Bu bölüm rakip ürün incelemesinden (bkz. `analiz/gamecaption-karsilastirma.md`) esinle eklendi — yalnızca kavram seviyesinde ("oyun başına önceden hazırlanmış çeviri hızlı yoldan gösterilebilir, canlı MT yedek kalır"); format, alan adları, eşleştirme algoritması ve editoryal içerik tamamen GAMLETSOL'a özgüdür ve sıfırdan tasarlanmıştır. Bir oyunun satırları yalnız GAMLETSOL'un kendi OCR yakalamasından ve kendi editoryal çevirisinden üretilir; başka bir ürünün çeviri/dublaj verisi hiçbir biçimde alınmaz veya içe aktarılmaz.

- [ ] **G073 — Satır veritabanı format ve eşleştirme sözleşmesi** (S)

  **Önerilen konum:** `core-model / core-pipeline/lookup` · **Bağımlılık:** G019,G031

  **Kabul:** GAMLETSOL'a özgü paket şeması (sürüm alanı, satır başına normalize-İngilizce anahtar, tam eşleşme ve önek indeksi, opsiyonel konuşmacı/stil alanı) tanımlı; OCR çıkışı önce bu indekste aranır, bulunamazsa G010/G047 canlı çeviriye düşer; G019 cache anahtarına paket sürümü eklenir.

  **Doğrulama:** Aynı sahne iki kez okutulduğunda ilk eşleşme canlı çeviriyi atlıyor mu, paket sürümü değişince cache'in bozulmadığını ve eşleşmeyen satırın sorunsuz fallback aldığını küçük sahte paketle doğrula.

- [ ] **G074 — Editoryal satır üretim hattı** (M)

  **Önerilen konum:** `tools/content-authoring` · **Bağımlılık:** G073,G034

  **Kabul:** Tek oyun için: gerçek OCR yakalamalarından İngilizce satır çıkarma → ekip/insan tarafından yazılan özgün Türkçe çeviri → inceleme onayı → pakete derleme adımları belgeli ve araçla desteklenmiş; hiçbir adımda üçüncü bir üründen çeviri metni veya ses kopyalanmıyor.

  **Doğrulama:** Bir pilot oyun için üretilen paketin tüm satırlarının kaynağının "kendi OCR + kendi çevirimiz" olduğu editoryal kayıtla kanıtlı; dış kaynak satırı varsa pakete girmeden reddedilir.

- [ ] **G075 — Sürümlü metin paketi dağıtımı** (M)

  **Önerilen konum:** `feature-content-packs / backend/catalog` · **Bağımlılık:** G073,G071

  **Kabul:** İmzalı manifest/hash, atomik kurulum, depolama sınırı ve sürüm geri dönüşü G071'deki ses paketi altyapısıyla paylaşılır; bir paket yalnız metin, metin+TTS veya metin+lisanslı dublaj olarak işaretlenebilir.

  **Doğrulama:** Bozuk/yarım indirme, disk dolu, farklı oyun sürümü ve paket olmadan (tamamen canlı MT) çalışma senaryolarını dene.

- [ ] **G076 — Ücretsiz/Pro paket kapsamı ve içerik haklarının takibi** (S)

  **Önerilen konum:** `backend/catalog / docs/decisions/content-rights` · **Bağımlılık:** G075,G049,G054

  **Kabul:** Hangi oyunun paketinin ücretsiz/Pro katmanda olduğu, hangi satırların insan incelemesinden geçtiği ve haklar/sorumluluk kaydı (ör. bir satırın yanlış çıkması durumunda sorumluluk) yazılı.

  **Doğrulama:** Yetkisiz kullanıcı Pro pakete erişemiyor mu ve paket kataloğu güncellemesi mevcut kullanıcıları bozmuyor mu test et.

> **Kontrol noktası X6-1 (G073–G076):** Bu grubun kabul kanıtlarını kaydet; uygulanmış kod varsa ilgili derleme ve hedefli testleri çalıştır; fiziksel cihaz iddiasını gerçek cihazla doğrula. Açık engeli ve sonraki bağımlılığı yaz. Kanıt yoksa kutuları işaretleme.

## Ortak tamamlanma ölçütü

Bir iş, yalnız kod yazıldığı için tamamlanmaz. Kabul kanıtı, ilgili hedefli test, kullanıcıya açık hata davranışı ve güncellenmiş destek kapsamı gerekir. Gizli anahtar, oturum çerezi, kullanıcı görüntüsü veya oyun metni standart günlüğe yazılmaz. TV özellikleri mock/emülatörle doğrulanmış sayılmaz. Bir kaynak kapandığında kamera/decoder/çevrim dışı model kaynakları bırakılır; son metin ve eski konuşma kuyruğu temizlenir.

Kalite sayıları ana raporda başlangıç hedefi olarak tanımlanmıştır; ilk ölçümden sonra gerekçeli güncellenir. Sessizce düşürülmez. Ücretli yayın için yalnız bütçe alarmı yeterli değildir: uygulanmış kota/harcama freni ve satın alma doğrulaması gerekir.
