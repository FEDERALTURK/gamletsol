# P0 Gerçek Donanım Test Rehberi (G001-G006 + G030)

> **Bu rehber bu kum havuzunda yazıldı, ama testlerin kendisi
> yazılamaz/çalıştırılamaz.** `docs/roadmap/tasks/todo.md`'deki G001-G006
> (P0) ve G030 (P3 kapanışı) görevlerinin TÜMÜ, gerçek Profilo Android TV +
> Xbox Series X/S + bir Android telefon gerektirir. Bu dosya, o testleri
> SİZİN yapabilmeniz için adım adım bir kılavuzdur — sonuçları
> `docs/compatibility/hardware.md`'deki ilgili bölümlere siz yazacaksınız.
>
> **Dürüstlük ilkesi hatırlatması:** Hiçbir adımı "muhtemelen çalışır" diye
> atlamayın. Bir adım başarısız olursa, başarısız olduğu haliyle (tam hata
> mesajı, ekran görüntüsü tarifi vb.) kaydedin — bu da geçerli bir G00x
> sonucudur, roadmap bunu "tamamlandı değil ama kanıtlı" olarak kabul eder.
>
> **Gizlilik:** G030'un istediği fotoğraf/video kanıtı **yalnızca sizin
> kendi cihazınızda kalır**. Bu kanıtı bana (Claude'a) göndermeniz
> gerekmiyor ve istenmiyor — roadmap'teki "kullanıcının test izniyle yerel
> tut" ifadesi tam olarak bunu söylüyor. Bana yalnızca sonucu (başarılı/
> başarısız, kaç deneme, karşılaşılan hata) yazmanız yeterli.

## 0. Ön hazırlık

1. **En güncel probe APK'larını indirin.** GitHub deposunun "latest"
   release'inde her zaman en son CI derlemesi var:
   - `app-mobile-debug.apk` (telefon — kamera/OCR/çeviri tarafı)
   - `app-tv-debug.apk` (TV — HDMI tuning + eşleştirme ekranı)

   İndirme: `https://github.com/FEDERALTURK/gamletsol/releases/tag/latest`
   sayfasından elle, veya ben size her istediğinizde ikisini de ayrıca
   gönderebilirim (bu oturumda zaten gönderdim; en güncel sürümü istiyorsanız
   söylemeniz yeterli).

2. **Her iki cihazda da "Bilinmeyen kaynaklardan yükleme" izni açık
   olmalı** — her iki APK de Play Store dışından (sideload) kuruluyor.
   Android TV'de bu genelde "Ayarlar → Cihaz tercihleri → Güvenlik ve
   kısıtlamalar → Bilinmeyen kaynaklar" altındadır (TV markasına göre yer
   değişebilir — Profilo'nuzdaki tam yolu not alın, bu zaten G001'in bir
   parçası).

3. **Kurulum sırası önemli değil** ama her iki cihaz da testler sırasında
   **aynı Wi-Fi ağında** olmalı (G030 ve G003-G004'ün ağ üzerinden
   çalışan kısımları için).

4. Elinizde boş bir not/metin dosyası açın (veya doğrudan
   `docs/compatibility/hardware.md`'yi düzenleyin) — her adımın sonucunu
   oraya yazacaksınız.

---

## G001 — Hedef donanım ve deney kaydı

Hiçbir APK gerekmez — sadece TV ve telefonun kendi ayarlar menüsünden
okuma.

**TV'de (Profilo):**

| Nereden bakılır | Ne yazılacak |
|---|---|
| Ayarlar → Cihaz hakkında / Hakkında | Marka/seri tam adı, Android TV sürümü, API seviyesi (genelde "Android TV sürümü: X" olarak yazar) |
| Ayarlar → Cihaz hakkında → Yazılım sürümü | Firmware/yazılım sürümü |
| Ekranda "Google TV ana ekranı" mı yoksa klasik Android TV launcher'ı mı görünüyor? | Google TV / klasik Android TV |
| TV'nin fiziksel HDMI girişleri (TV'nin yan/arka panelini sayın) | HDMI giriş sayısı |
| Ayarlar → Uzaktan kumanda ve aksesuarlar / Genel → HDMI-CEC (TV markasına göre "Anynet+", "Bravia Sync" gibi adlar da olabilir) | HDMI-CEC açık/kapalı |

**Xbox'ta:**

| Nereden bakılır | Ne yazılacak |
|---|---|
| Konsol kutusu/arka etiketi veya Ayarlar → Sistem → Konsol bilgileri | Series X mi S mi |
| TV'nin "Giriş kaynağı" menüsü | Xbox'ın bağlı olduğu HDMI girişinin numarası (TV'de "HDMI 1/2/3..." olarak görünür) |
| Xbox Ayarlar → Genel → TV ve görüntü seçenekleri | Çıkış çözünürlüğü/Hz, HDR/Dolby Vision açık mı, VRR/ALLM açık mı |

**Telefonda:**

| Nereden bakılır | Ne yazılacak |
|---|---|
| Ayarlar → Telefon hakkında | Model, Android sürümü |
| Kamera uygulaması → ayarlar/özellikler veya telefonun teknik sayfası | Arka ana kamera çözünürlüğü |

**Ağ:**

- TV Ayarlar → Ağ → bağlantı türü (Wi-Fi/kablolu).
- Telefon Ayarlar → Wi-Fi → bağlı ağın adı; Xbox Ayarlar → Ağ → bağlantı
  türü. Üçünün aynı ağda olduğunu (aynı Wi-Fi SSID'si) doğrulayın.
- Router isolation (AP isolation) genelde router'ın yönetim panelinde
  (`192.168.1.1` gibi bir adres) "AP Isolation" veya "Misafir ağı
  izolasyonu" olarak geçer — biliyorsanız yazın, bilmiyorsanız `?` kalsın.

→ Bu tabloyu `docs/compatibility/hardware.md`'deki "TV / Konsol / Telefon /
Ağ" tablolarına işleyin.

---

## G002 — Profilo HDMI girişlerini keşfet

> **Not (2026-11-10):** Uygulamanın gerçek kullanım ekranı artık sade
> (yalnızca eşleştirme/altyazı) — bu testin gerektirdiği teknik satırlar
> varsayılan olarak GİZLİ. Ekranın sağ üst köşesindeki **"Gelişmiş / Test
> modu"** düğmesine basarak açın; bu düğme AÇIK kaldığı sürece aşağıdaki
> satırlar görünür kalır.

1. `app-tv-debug.apk`'yı Profilo TV'ye kurun ve açın.
2. Sağ üst köşedeki **"Gelişmiş / Test modu"** düğmesine basın (metni
   "Test modu: AÇIK" olarak değişir ve sarı renge döner).
3. Test modu açıkken ekranda şu satırları göreceksiniz (gerçek kod,
   `MainActivity.kt`, `TvProbeScreen`):
   - `Durum: <IDLE/TUNING/...> · Giriş: <bulunan inputId veya "bulunamadı">`
   - `Algılanan N giriş: <her girişin id'si>(hdmi=true/false,pt=true/false)`
   - Eğer bir hata oluşursa (örn. `TvInputManager` erişimi reddedilirse):
     `Hata: <tam hata metni>`
4. **Kaydedilecekler** (`hardware.md` → "G002" bölümü):
   - "Algılanan N giriş" satırının **tam metnini** kopyalayın.
   - Bu listede Xbox'ın takılı olduğu HDMI'a karşılık gelen girişi bulun
     (genelde `hdmi=true,pt=true` olanlardan biri; eğer birden fazla
     `hdmi=true,pt=true` giriş varsa, uygulama otomatik olarak birini
     seçer — ekranın altındaki **"Sonraki HDMI girişine geç"** düğmesiyle
     aralarında gezinip hangisinin gerçekten Xbox'a karşılık geldiğini
     görsel olarak (G003'teki tune denemesiyle) belirleyebilirsiniz).
   - Eğer `Hata: ...` satırı görünüyorsa, tam metnini yazın — bu da geçerli
     bir sonuçtur (örn. Profilo'nun `TvInputManager` izinlerini kısıtlamış
     olması ihtimali gerçek bir bulgudur, "henüz çalışır durumda değil"
     diye kaydedin, asla "muhtemelen çalışıyor" demeyin).

---

## G003 — Canlı HDMI üstüne sabit Türkçe yazı (30 dakikalık dayanıklılık testi)

> **Not (2026-11-10):** Bu test, yalnızca **"Gelişmiş / Test modu"** AÇIK
> iken anlamlıdır — kapalıyken `app-tv` artık HDMI'a hiç tune olmaya
> çalışmaz (sade mod). Devam etmeden önce sağ üstteki düğmeye basıp
> "Test modu: AÇIK" olduğundan emin olun.

1. Xbox'ı açın, bir oyun veya ana menü ekranında bırakın (video akışı sürekli
   olsun).
2. `app-tv` açıkken (Test modu AÇIK), ekranın arka planında Xbox'ın görüntüsü görünmelidir
   (bu, `TvView` + `PassthroughTuner`'ın gerçekten o HDMI girişine tune
   olabildiğini kanıtlar). Görünmüyorsa (ör. siyah ekran, "Durum: ERROR"),
   bu zaten bir G003 sonucudur — "tune olmadı" diye kaydedin.
3. Görüntü üstünde, overlay katmanında şu satırları göreceksiniz:
   - Sabit test yazısı (ı/İ/ş/ğ/ü/ö/ç karakterlerini içeren Türkçe metin) —
     henüz telefon bağlanmadıysa bu görünür.
   - `Canlı saat (donmuş değil): HH:mm:ss` — bu saat her saniye
     güncellenir. **Bu satırın gerçekten saniyede bir değiştiğini
     gözlemleyin** — değişmiyorsa, overlay'in donmuş bir ekran görüntüsü
     olduğundan şüphelenin (gerçek render değil).
4. **30 dakika boyunca** TV'nin önünde durup (veya arada kontrol ederek)
   şunları gözlemleyin ve kaydedin:
   - Türkçe karakterler (ı/İ/ş/ğ/ü/ö/ç) okunaklı/bozuk görünüyor mu?
   - Saat 30 dakika boyunca sürekli güncellenmeye devam ediyor mu (yani
     uygulama/overlay donmuyor mu)?
   - Xbox'ın görüntüsü (arka plandaki video) 30 dakika boyunca akmaya
     devam ediyor mu, yoksa bir noktada kayboluyor/donuyor mu?
5. **Ek davranış testleri** (30 dakikalık pencerenin içinde veya sonunda
   yapılabilir):
   - TV kumandasında **geri tuşuna** basın → ne oluyor (uygulamadan çıkıyor
     mu, aynı ekranda kalıyor mu)?
   - TV'nin kendi giriş değiştirme menüsünden **başka bir HDMI girişine
     geçin**, sonra tekrar Xbox'ın girişine dönün → overlay/tune durumu
     kendini düzeltiyor mu, yoksa manuel bir şey mi gerekiyor?
   - **Xbox'ı kapatın** (kaynak kaybı) → TV/uygulama ne gösteriyor (siyah
     ekran, "sinyal yok" TV mesajı, uygulama çökmesi)? Sonra Xbox'ı tekrar
     açın → kendiliğinden düzeliyor mu?
6. **Kaydedilecekler** (`hardware.md` → "G003" bölümü): Yukarıdaki her
   sorunun cevabı, satır satır. Herhangi bir adımda beklenmeyen bir şey
   olursa (çökme, donma, siyah ekran) **tam olarak ne gördüğünüzü**
   yazın — bu, "desteklenmiyor" sonucunun kendisidir, başarısızlık değil.

---

## G004 — Görüntü modu ve gecikme tabanı

Bu görev iki karşılaştırma istiyor: (a) Xbox'ı doğrudan TV'nin HDMI
girişine bakarken oynamakla, (b) `app-tv` açıkken (yani TvView üzerinden)
oynamak arasındaki **algılanan** gecikme farkı. Bu bir hassas ölçüm aracı
gerektirmez — gözle fark edilir bir fark olup olmadığını arıyoruz.

1. **Doğrudan mod:** `app-tv`'yi kapatın, TV'nin kendi giriş menüsünden
   doğrudan Xbox'ın HDMI girişine geçin. Hızlı tepki gerektiren bir oyun/
   menü ile (örn. bir FPS oyununun menüsünde hızlı yukarı/aşağı gezinme,
   ya da basit bir buton-basımına-tepki testi) gecikmeyi "hissedin".
2. **`app-tv` modu:** `app-tv`'yi açın, sağ üstten **"Gelişmiş / Test
   modu"**'nu açın, aynı Xbox girişine tune olmasını bekleyin, aynı testi
   tekrarlayın.
3. **Kaydedilecekler** (`hardware.md`'ye yeni bir "## G004" bölümü
   ekleyerek — şu an dosyada bu bölüm yok, siz veya ben birlikte
   ekleyebiliriz):
   - Gözle fark edilen bir gecikme farkı var mı (evet/hayır, "biraz",
     "belirgin" gibi öznel bir tanım yeterli — kesin milisaniye ÖLÇÜLMÜYOR,
     ölçülmediği de not edilmeli).
   - TV'nin kendi ayarlarında görünen çıkış modu: 1080p60 mı, 4K mi, HDR
     açık mı, VRR/ALLM TV tarafında destekleniyor/açık görünüyor mu
     (Xbox'ın G001'de kaydettiğiniz ayarlarıyla aynı mı yoksa `app-tv`
     açıkken TV bir "oyun modu"ndan çıkıyor mu — bazı TV'ler HDMI
     passthrough uygulamaları açıkken oyun modunu otomatik kapatabilir,
     bunu kontrol edin).
   - **Yapmayın:** "4K120 veya VRR çalışıyor" gibi bir iddia, gerçekten TV
     ekranında o modun aktif olduğunu gösteren bir ayar/gösterge
     görmediğiniz sürece yazılmamalı.

---

## G005 — TV dağıtımı ve ürün devam kararı

Bu bir kod/ölçüm görevi değil, bir **karar** görevi. G001-G004'ün
sonuçlarını gördükten sonra (özellikle G003'ün 30 dakikalık dayanıklılık
sonucu), şu soruları kendinize sorup kısa bir karar yazın:

1. G003 genel olarak "çalışıyor" mu, yoksa temel bir engel mi var (örn.
   TvView hiç tune olamadı, veya 30 dakika içinde donuyor)?
2. Eğer çalışıyorsa: Bu özelliği (TV'ye direkt HDMI tuning) gerçek
   kullanıcılara (sadece siz değil) sunmak ister misiniz, yoksa şimdilik
   yalnızca "test/geliştirme modu" olarak mı bırakmak istersiniz?
3. Google Play'in Android TV uygulamaları için gerektirdiği
   (`android.media.tv.TvInputManager` API'sinin bazı kullanımları
   sistem-imzalı/root erişimi isteyebilir — bunu G002'deki hata mesajınız
   zaten gösterecektir) kısıtlamalar sizi engelliyor mu?
4. Kısa karar: "devam" / "ertele" / "kapsamı sınırla (yalnızca X)" — bir
   iki cümleyle nedeni.

→ Bu kararı bana ilettiğinizde, roadmap'teki G005'i "kullanıcı kararı
alındı: ..." diye not edebilirim.

---

## G006 — Xbox resmi kaynak erişimi ön deneyi

Bu görev, Xbox'ın **resmi** tarayıcı tabanlı bulut oyun arayüzü (xbox.com
üzerinden) veya resmi Xbox uygulamasının ekran paylaşımı/MediaProjection
akışının, telefonun kamerası/OCR'ı DIŞINDA bir yoldan erişilip
erişilemediğini anlamak için küçük, kullanıcı onaylı bir deneydir.

**Önemli sınır:** Bu deneyde **giriş sayfalarını kazımayın, çerezleri/
oturumları ele geçirmeyin, DRM/korumayı atlatmaya ÇALIŞMAYIN.** Amaç
sadece: resmi, halka açık arayüzün (siz zaten giriş yapmış haldeyken)
hangi kısımlarının normal bir ekran görüntüsü/MediaProjection ile
yakalanabilir, hangilerinin (DRM korumalı video gibi) siyah/boş çıktığını
gözlemlemek.

1. Telefonunuzda veya TV'nizde, Xbox'ın resmi tarayıcı arayüzüne (siz zaten
   kendi hesabınızla giriş yapmış durumda) gidin.
2. Android'in kendi ekran kaydı/ekran paylaşımı özelliğini (MediaProjection
   izni istecektir — bu standart bir sistem izni penceresidir) kullanarak
   kısa bir kayıt/paylaşım deneyin.
3. **Kaydedilecekler:**
   - Arayüzün menü/metin kısımları ekran paylaşımında görünüyor mu?
   - Video oynatma kısmı (varsa) görünüyor mu, yoksa siyah/boş mu çıkıyor
     (bu, DRM korumalı yüzeylerin MediaProjection'da siyah çıkması
     beklenen bir davranıştır — görürseniz normal, "engel" diye
     kaydedin, "hata" değil)?
   - Kumanda/HDMI etkileşimiyle ilgili fark ettiğiniz bir şey var mı?
4. Bu deneyde app-tv/app-mobile'dan hiçbir kod kullanılmıyor — tamamen
   Android'in kendi sistem özellikleriyle yapılan, elle gözlemlenen bir
   ön-deney. Sonucu bana kısa bir not olarak yazmanız yeterli.

---

## G030 — Profilo TV ilk kabul matrisi (P3 kapanışı)

Bu, zaten yazılmış ve CI'da doğrulanmış kodun (G021, G027, G028, G029 —
eşleştirme, TLS, kalıcı kimlik, altyazı ayarları) **gerçek donanımda ilk
kez** çalıştırılmasıdır.

### Adımlar

1. `app-mobile-debug.apk`'yı telefona, `app-tv-debug.apk`'yı TV'ye kurun
   (zaten kurduysanız bu adımı atlayın).
2. TV'de `app-tv`'yi açın — ekranda bir **eşleştirme kodu** ve bir **QR
   kod** göreceksiniz (`Eşleştirme kodu: XXXXXX (60s)` satırı + altında QR
   kare).
3. Telefonda `app-mobile`'ı açın, menü (⋮) → "TV'ye Yansıt" → "QR ile
   tara"ya dokunup TV'deki QR'ı okutun. (QR okunmazsa: telefondaki
   diyalogda "QR okunmuyor mu? Elle gir"e basın VE TV'de sağ üstteki
   "Gelişmiş / Test modu"nu açın — IP:port satırı yalnızca Test modu
   açıkken görünür.)
4. Bağlantı başarılı olursa, telefon kamerasını TV ekranına (Xbox'ın
   gösterdiği İngilizce altyazıya) doğrultun — OCR/çeviri pipeline'ı
   çalışıp çevrilen metni TV'ye geri göndermeli, TV overlay'inde
   görünmeli.

### 10 başarılı eşleştirme

- Adım 2-3'ü **10 kez tekrarlayın** (her denemede TV'de "Yeni eşleştirme
  kodu oluştur" düğmesine basıp yeni bir kod alın, ya da uygulamaları
  kapatıp yeniden açın).
- Her denemenin sonucunu (başarılı/başarısız, başarısızsa ne oldu — kod
  süresi doldu mu, bağlantı reddedildi mi, TLS fingerprint uyuşmazlığı mı)
  tek tek not edin.
- **Kabul kriteri:** 10 denemeden kaçı başarılı oldu? (Roadmap "10
  başarılı eşleştirme" istiyor — 10/10 olması gerekmeyebilir, ama kaçının
  başarılı olduğunu ve başarısız olanların NEDEN başarısız olduğunu
  bilmek önemli.)

### 60 dakikalık canlı oturum

- Başarılı bir eşleştirmeden sonra, telefon kamerasını TV'ye doğrultup
  **60 dakika kesintisiz** bırakın (Xbox'ta altyazılı bir oyun/film/dizi
  oynatarak — gerçek İngilizce altyazı olması önemli, sabit test metni
  değil).
- Gözlemleyin: pipeline 60 dakika boyunca kesintisiz çalışıyor mu? Telefon
  ısınıyor mu, batarya ne kadar tükeniyor, uygulama çöküyor mu, bağlantı
  kopuyor mu (koparsa kendiliğinden yeniden bağlanıyor mu)?

### Bağlantı kesildiğinde temizlik davranışı

- 60 dakikalık oturumun sonunda (veya ayrı bir denemede) telefon
  uygulamasını kapatın / TV'den Wi-Fi'ı kapatın (bağlantıyı zorla kesin).
- TV tarafında ne oluyor gözlemleyin: eski cue ekranda asılı kalıyor mu,
  yoksa temizleniyor mu? Yeni bir eşleştirme koduyla tekrar bağlanabiliyor
  musunuz (sunucu durumu bozulmadı mı)?

### Kaydedilecekler

`hardware.md`'ye yeni bir "## G030" bölümü ekleyin (şu an yok) ve şunları
yazın: 10 denemeden kaç başarılı; 60 dakikalık oturumun tam sonucu
(tamamlandı mı, kaç dakikada kesildi, neden); bağlantı-kesme temizlik
davranışı. **Fotoğraf/video kanıtını kendi cihazınızda/galerinizde
saklamanız yeterli — bana göndermeniz gerekmiyor.**

---

## Sonuçları nereye yazacaksınız

- G001, G002, G003: `docs/compatibility/hardware.md` içindeki ilgili
  bölümlere (zaten orada, boş satırlar olarak hazır).
- G004, G005, G006, G030: `hardware.md`'de şu an bu görevler için ayrı
  bölüm yok — bana sonuçları (kısa metin olarak, bu sohbette veya başka
  bir şekilde) ilettiğinizde, ben `hardware.md`'ye uygun yeni bölümler
  ekleyip roadmap'teki (`docs/roadmap/tasks/todo.md`) ilgili kutucukları
  **gerçek kanıt geldiği için** işaretleyebilirim. Hiçbir kutucuk, siz bu
  testleri bana bildirmeden işaretlenmeyecek — bu, projenin kuruluşundan
  beri uygulanan kural.
