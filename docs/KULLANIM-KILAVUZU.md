# GAMLETSOL — Kurulum ve Kullanım Kılavuzu

*Son güncelleme: 2026-11-13 — TV bağlantı senkronizasyonu ve oyun görüntüsü, çeviri kütüphanesi, yatay kullanım, altyazı çerçevesi (OCR bandı), çevirinin ekranın üstünde gösterilmesi ve yeni sorun giderme satırları eklendi.*

> Bu belge, GAMLETSOL'un ne olduğunu, nasıl çalıştığını, nasıl kurulacağını
> ve nasıl kullanılacağını adım adım anlatır. Kod/mimari detayları için kök
> [`README.md`](../README.md)'ye, yol haritasına
> [`docs/roadmap/GAMLETSOL geliştirme yol haritası.md`](roadmap/GAMLETSOL%20geliştirme%20yol%20haritası.md)'na
> bakabilirsiniz.
>
> **Dürüstlük notu (bu belgenin kendisi hakkında):** Bu kılavuzdaki kurulum
> adımları gerçek bir Android telefon + gerçek bir Android TV/Google TV
> cihazında **henüz denenmedi** — bu geliştirme ortamında (kum havuzu)
> gerçek Android donanımı yok. Adımlar, Android'in resmi/belgeli
> davranışına ve projenin kod/manifest dosyalarının gerçekte ne istediğine
> dayanır. Bir adım beklediğiniz gibi çalışmazsa, lütfen bir
> [GitHub Issue](https://github.com/FEDERALTURK/gamletsol/issues) açın —
> bu, projenin gerçek donanımda doğrulanmasını hızlandıracaktır.

---

## İçindekiler

1. [GAMLETSOL nedir?](#1-gamletsol-nedir)
2. [Nasıl çalışır? (mimari özet)](#2-nasıl-çalışır-mimari-özet)
3. [Gereksinimler](#3-gereksinimler)
4. [APK'ları indirme](#4-apkları-indirme)
5. [Kurulum — Telefon (app-mobile)](#5-kurulum--telefon-app-mobile)
6. [Kurulum — Android TV / Google TV (app-tv)](#6-kurulum--android-tv--google-tv-app-tv)
7. [İlk eşleştirme — TV'ye bağlanma](#7-ilk-eşleştirme--tvye-bağlanma)
8. [Altyazı görüntüleme ayarları (TV tarafı)](#8-altyazı-görüntüleme-ayarları-tv-tarafı)
9. ["Bu TV'yi hatırla" ve yeniden bağlanma](#9-bu-tvyi-hatırla-ve-yeniden-bağlanma)
10. [Sorun giderme](#10-sorun-giderme)
11. [Bilinen sınırlar (dürüstlük ilkesi)](#11-bilinen-sınırlar-dürüstlük-ilkesi)
12. [Geliştiriciler için — kaynaktan derleme](#12-geliştiriciler-için--kaynaktan-derleme)

---

## 1. GAMLETSOL nedir?

GAMLETSOL ("Game Translate Console"), bir oyun konsolu/PC/TV/film/dizi
ekranında **zaten görünen İngilizce altyazıyı** telefonunuzun kamerasıyla
(ya da ileride doğrudan konsol bağlantısıyla) okuyup **Türkçeye çeviren**
bir Android uygulama çiftidir:

- **`app-mobile`** — telefonunuza kurulan ana uygulama. Kamerayı ekrana
  doğrultur, altyazıyı OCR ile okur, çevirir ve isterseniz sonucu bir
  Android TV/Google TV cihazına gönderir.
- **`app-tv`** — Android TV / Google TV cihazınıza kurulan, telefondan
  gelen çeviriyi büyük ekranda gösteren küçük bir uygulama.

Bu, konuşma tanıma (ASR, mikrofonla dinleme) **DEĞİLDİR** — gerçek Game
Caption'da olduğu gibi, oyunun/filmin zaten gösterdiği yazıyı okur. Bu
yüzden en önemli ön koşul: **izlediğiniz/oynadığınız içerikte İngilizce
altyazı açık olmalı.**

**Gizlilik ilkesi:** Kamera görüntüsü hiçbir aşamada bir sunucuya
yüklenmez. Yalnızca OCR ile çıkarılan **metin**, çeviri için kullanılır
(varsayılan: cihaz-üstü ML Kit, tamamen çevrimdışı). Telefon↔TV
bağlantısı da yerel ağınızda kalır, internete çıkmaz.

## 2. Nasıl çalışır? (mimari özet)

```
┌─────────────┐   kamera    ┌──────────────┐   TLS (yerel ağ)   ┌─────────────┐
│ Ekran/Konsol │ ──────────> │  app-mobile  │ ─────────────────> │   app-tv    │
│ (İngilizce   │   görüntü   │  (telefon)   │  şifreli altyazı   │ (TV/Google  │
│  altyazı)    │             │ OCR→Çeviri   │     olayları       │     TV)     │
└─────────────┘             └──────────────┘                    └─────────────┘
                                    │                                   │
                                    ▼                                   ▼
                          Telefon ekranında da                 TV ekranında büyük,
                          gösterilebilir (fallback)            okunabilir altyazı
```

Adım adım:

1. Telefonu ekrana doğrultursunuz; `app-mobile` kamera görüntüsünü OCR
   motoruna (ML Kit) verir.
2. OCR, ekrandaki İngilizce metni çıkarır; ML Kit'in cihaz-üstü çeviri
   modeli bunu Türkçeye çevirir.
3. Sonuç, varsayılan olarak telefon ekranında gösterilir (hiçbir TV
   gerekmez — bu her zaman çalışan, en basit yol).
4. İsterseniz "TV'ye Yansıt" ile telefonu bir Android TV/Google TV
   cihazındaki `app-tv`'ye bağlarsınız; çeviri artık büyük ekranda da
   görünür. Bu bağlantı **TLS ile şifrelenir** ve TV'nin kimliği
   (sertifika fingerprint'i) QR kod veya yerel ağ keşfi (NSD) ile
   **pinlenir** — "ortadaki adam" (MITM) saldırısına karşı korumalıdır.

> **Not:** Konsola doğrudan bağlanma (PS5/PS4/Xbox) ve Akıllı Ekran
> (menü/envanter çevirisi) özellikleri, projenin genel vizyonunda yer alır
> ama bu kılavuzun yazıldığı an itibariyle **henüz uygulanmadı** — bkz.
> [Bilinen sınırlar](#11-bilinen-sınırlar-dürüstlük-ilkesi).

## 3. Gereksinimler

- **Telefon:** Android, kamerası olan herhangi bir cihaz (`app-mobile`
  kamera iznini ister).
- **TV (isteğe bağlı, yalnızca büyük ekranda çeviri istiyorsanız):**
  Android TV veya Google TV işletim sistemi çalıştıran bir cihaz
  (Samsung Tizen / LG webOS cihazlar için ayrı bir "yüklenebilir TV
  uygulaması" yolu projenin vizyonunda var ama **henüz uygulanmadı** —
  bkz. [Bilinen sınırlar](#11-bilinen-sınırlar-dürüstlük-ilkesi); şu an
  yalnızca Android TV/Google TV desteklenir).
- **Aynı yerel ağ:** Telefon ve TV, aynı Wi-Fi ağında olmalı (bağlantı
  yerel ağ üzerinden kurulur, internet gerekmez).
- **"Bilinmeyen kaynaklardan yükleme" izni:** Uygulamalar şu an Google
  Play'de DEĞİL — APK'ları doğrudan kurmanız (sideload) gerekir (bkz.
  aşağıdaki kurulum bölümleri).

## 4. APK'ları indirme

İki yol vardır:

### 4a. GitHub Release'inden (önerilen — en kolay, kalıcı bağlantı)

Depo, `main` dalındaki en güncel kodun APK'larını otomatik olarak
**[Releases](https://github.com/FEDERALTURK/gamletsol/releases/tag/latest)**
sayfasında yayınlar (her `main` push'unda güncellenir). Oradan şu iki
dosyayı indirin:

- `app-mobile-debug.apk` — telefona kurulacak.
- `app-tv-debug.apk` — Android TV/Google TV'ye kurulacak.

> **Not:** Bunlar **debug** (imzasız/debug-signed) derlemelerdir — Google
> Play'den indirilen bir uygulama gibi "release-signed" değildir. Bu, bir
> Android cihazın güvenlik ayarlarında bir farka yol açmaz (debug APK'lar
> normal şekilde kurulur) ama bkz. [Bilinen sınırlar](#11-bilinen-sınırlar-dürüstlük-ilkesi).

### 4b. CI çalıştırmasından (geliştiriciler için / en güncel commit)

Release henüz güncellenmemişse veya belirli bir commit'in APK'sını
istiyorsanız:
[Actions sekmesi](https://github.com/FEDERALTURK/gamletsol/actions/workflows/android.yml) →
istediğiniz başarılı ("✓") çalıştırmayı açın → sayfanın altındaki
**Artifacts** bölümünden `gamletsol-mobile-debug-apk` ve
`gamletsol-tv-probe-debug-apk`'yı indirin (zip olarak gelir, içindeki
`.apk` dosyasını çıkarın). **Not:** CI artifact'leri 90 gün sonra otomatik
silinir — bu yüzden genel kullanım için 4a'daki Release bağlantısı daha
güvenilirdir.

## 5. Kurulum — Telefon (app-mobile)

Telefonunuzda Google Play dışından bir APK kurmak için:

1. İndirdiğiniz `app-mobile-debug.apk` dosyasını telefonunuza aktarın
   (doğrudan telefonda tarayıcıyla indirebilir, veya bilgisayardan
   USB/bulut ile kopyalayabilirsiniz).
2. Dosya yöneticinizde (veya "İndirilenler" bildiriminde) APK dosyasına
   dokunun.
3. Android, ilk seferde "Bu kaynaktan yükleme" iznini isteyecektir —
   izin verin (bu, kullandığınız dosya yöneticisi/tarayıcı uygulamasına
   özeldir, yalnızca bir kez sorulur).
4. "Yükle"ye dokunun. Kurulum bitince "GAMLETSOL" uygulamasını açın.
5. İlk açılışta kısa bir **tanıtım** (3 sayfa) gösterilir — "İleri" ile
   geçin veya "Atla"ya dokunun. (Sonradan menü (⋮) → **"Tanıtımı yeniden
   göster"** ile tekrar açabilirsiniz.)
6. Uygulama kamera izni isteyecektir (altyazıyı okumak için zorunludur)
   — izin verin.
7. **İlk kullanımda telefon Wi-Fi'ye bağlı olsun:** cihaz-üstü çeviri
   modeli ilk açılışta bir kez indirilir. İndikten sonra çeviri internetsiz
   çalışır.

### Telefonda günlük kullanım

1. Oyun/film/dizi ayarlarından **İngilizce altyazıyı açın**.
2. Telefonu, altyazının göründüğü ekranı **net ve sabit** görecek şekilde
   tutun (mümkünse bir stand/tripod kullanın). Telefonu **dikey veya yatay**
   tutabilirsiniz; telefonun otomatik döndürme ayarı açıksa uygulama da
   döner. TV'yi kadraja sığdırmak için genelde **yatay** daha rahattır.
   Kamera görüntüsü kırpılmadan gösterilir (kenarlarda siyah boşluk
   olabilir) — gördüğünüz alan, okunan alanın tamamıdır.
3. Kamera görüntüsünün alt kısmında beyaz bir çerçeve ve **"Altyazıyı bu
   çerçeveye getirin"** yazısı görürsünüz. Uygulama yalnızca **bu çerçevenin
   içindeki** yazıyı okur — telefonu, TV'deki altyazı satırı çerçevenin
   içine düşecek şekilde konumlandırın. Altyazı çerçevenin dışında kalırsa
   çeviri gelmez.
4. Altyazı birkaç kare boyunca net okununca, Türkçe çevirisi telefon
   ekranının **üst kısmında** belirir ve **sesli okunur** (Android'in Türkçe
   metin okuma sesi kullanılır; telefonda Türkçe ses kurulu değilse bununla
   ilgili bir uyarı görürsünüz). Altyazı satırı ekranda uzarsa (harf harf
   yazılan altyazılar) çeviri de güncellenir.
5. Sağ üstteki **menü (⋮)** şunları içerir: **"TV'ye Yansıt"**,
   **"Çeviri kütüphanem"**, **"Kurulum talimatları"**, **"Gizlilik ve nasıl çalışır"**,
   **"Tanıtımı yeniden göster"**, **"Hakkında / yol haritası durumu"**.

### Çeviri kütüphanesi

Uygulama bir satırı çevirirken önce kendi **çeviri kütüphanesine** bakar,
orada yoksa cihaz-üstü canlı çeviriyi kullanır. Kütüphaneden gelen çeviri
anında ve her seferinde aynı çıkar.

- **Hazır ifadeler:** Uygulamayla birlikte oyunlarda sık görülen yaklaşık
  180 ifade gelir — menü ve sistem iletileri ("Loading", "Checkpoint
  reached", "Game over"), tuş istemleri ("Press X to continue" → "Devam
  etmek için X tuşuna basın"; tuş adı ne olursa olsun) ve sık kullanılan
  kısa diyaloglar ("Get down!" → "Eğil!", "Take cover!" → "Siper al!").
  Büyük/küçük harf ve sondaki noktalama farkı önemli değildir.
- **Kendi düzeltmelerin:** Ekrandaki çeviri yanlışsa çeviri kutusuna
  **dokun** → doğru Türkçesini yaz → **Kaydet**. Aynı İngilizce satır bir
  daha göründüğünde senin çevirin gösterilir (ekranda duran satır da hemen
  güncellenir). Düzeltmeler telefonda saklanır, uygulamayı kapatıp açınca
  kaybolmaz, hiçbir yere gönderilmez.
- **Yönetme:** Menü (⋮) → **"Çeviri kütüphanem"**: düzeltmelerini listeler;
  tek tek **"Sil"** ya da **"Tümünü sil"**.

## 6. Kurulum — Android TV / Google TV (app-tv)

Android TV'de dosya yöneticisi genelde yoktur ve ADB/bilgisayar
bağlantısı gerektirmeyen en kolay yol, bir "Downloader" türü uygulama
kullanmaktır:

1. TV'nizde **Google Play Store**'u açın, **"Downloader"** (geliştirici:
   AFTVnews) uygulamasını arayıp kurun (ücretsiz).
2. Downloader'ı açın. Ana ekranda bir URL kutusu göreceksiniz.
3. GitHub Release sayfasındaki `app-tv-debug.apk` dosyasının **doğrudan
   indirme bağlantısını** bu kutuya girin (Release sayfasında dosya
   adına sağ tıklayıp "bağlantı adresini kopyala" diyebilirsiniz; TV'de
   bunu elle yazmak zorunda kalmamak için [bir QR/URL kısaltıcı]
   kullanmak işinizi kolaylaştırır).
4. Downloader dosyayı indirip otomatik olarak kurulum ekranını açar —
   "Yükle"ye basın.
5. Kurulum bitince TV'nin ana ekranında ya da "Uygulamalarım"da
   "GAMLETSOL" (TV probe) simgesini bulup açın.

> **Alternatif:** Eğer TV'nizde zaten bir dosya yöneticisi veya ADB
> erişiminiz varsa, APK'yı doğrudan o yolla da kurabilirsiniz — yukarıdaki
> Downloader yöntemi yalnızca **ADB/bilgisayar gerektirmeyen** en genel
> yoldur.

## 7. İlk eşleştirme — TV'ye bağlanma

En kolay yol **QR ile taramaktır** — üç adım:

1. **TV'de** "GAMLETSOL" uygulamasını açın. Ekranda **"GAMLETSOL'a
   bağlanın"** başlığı, kısa bir yönerge, büyük bir **QR kod** ve kodun
   kalan süresi (60 saniye) görünür.
2. **Telefonda** GAMLETSOL'u açın → sağ üstteki **menü (⋮)** →
   **"TV'ye Yansıt"** → **"QR ile tara"**.
3. Telefonun kamerasını TV'deki QR koda tutun. Kod okununca bağlantı
   kendiliğinden kurulur; TV'deki QR ekranı kaybolur ve telefonun
   okuduğu altyazıların çevirisi TV'de görünmeye başlar.

> QR kodun süresi dolarsa TV'deki **"Yeni eşleştirme kodu oluştur"**
> düğmesine basın ve yeni QR'ı okutun.

**Diyalogda ayrıca şunlar görünebilir** (yalnızca varsa):

- **Hatırlanan TV'ler** — daha önce bağlandığınız TV'ler (bkz. bölüm 9).
- **Yakındaki TV'ler** — aynı Wi-Fi ağında otomatik bulunan (NSD) TV'ler;
  dokunarak seçebilirsiniz.

**QR okunmuyorsa — elle giriş (yedek yol):**

1. Telefondaki diyalogda **"QR okunmuyor mu? Elle gir"** seçeneğine dokunun; IP,
   port ve eşleştirme kodu alanları açılır.
2. TV'nin IP adresi ve portu varsayılan ekranda **gösterilmez**. Görmek
   için TV'de sağ üstteki **"Gelişmiş / Test modu"** düğmesine basın;
   "Telefondan bağlan (manuel): IP:port" satırı belirir.
3. IP'yi, portu ve TV'deki **güncel** eşleştirme kodunu girip "Bağlan"a
   dokunun.

> Eşleştirme kodu 60 saniye içinde kullanılmazsa veya yanlış girilirse
> bağlantı reddedilir — yeni kod oluşturup tekrar deneyin.

**Güvenlik notu:** QR tarama ve otomatik keşif (NSD) yolları, TV'nin TLS
sertifika "fingerprint"ini de taşır ve telefon bu TAM fingerprint'i
**pinler** — yani bağlantı şifrelidir ve yerel ağınızdaki biri "sahte bir
TV" gibi davranıp araya giremez. Elle giriş yolu bu korumayı **sağlamaz**
(fingerprint içermez) — yalnızca güvendiğiniz bir ağda, NSD/QR
çalışmadığında yedek olarak kullanın.

### Bağlıyken TV'de ne görürsünüz?

- Sol üstte küçük, yeşil **"● Telefon bağlı"** yazısı.
- **Oyun görüntüsü:** TV'de bir HDMI girişi bulunursa (konsolun takılı
  olduğu giriş), telefon bağlanınca oyunun görüntüsü GAMLETSOL'un içinde,
  altyazının arkasında gösterilir. Sağ üstteki **"Oyun görüntüsü: Açık /
  Kapalı"** ile açıp kapatabilir, birden fazla HDMI girişi varsa
  **"Giriş: …"** düğmesiyle doğru girişi seçebilirsiniz (seçim hatırlanır).
  Bazı TV'ler kendi HDMI görüntüsünü başka uygulamaların içinde
  göstermeye izin vermeyebilir; o durumda ekranda bununla ilgili bir uyarı
  çıkar.
- Telefonda altyazı ekrandan kalkınca TV'deki altyazı da kalkar.
- TV, uygulama açıkken ekran koruyucuya/uykuya geçmez.

### Bağlantı koparsa

- Telefon bağlantının koptuğunu birkaç saniye içinde fark eder ve
  **kendiliğinden yeniden bağlanmayı dener** (telefon menüsünde "TV
  bağlantısı (yeniden bağlanıyor…)" yazar). Bunun için QR'ı yeniden
  okutmanız gerekmez; yaklaşık bir buçuk dakika boyunca denenir.
- TV tarafında telefon gidince eski altyazı kaldırılır ve QR yeni bir
  kodla yeniden görünür.
- Kendiliğinden bağlanma başarısız olursa (ör. TV uygulaması kapatılıp
  açıldıysa) telefonda "Bağlantı kurulamadı veya koptu" yazar — QR'ı
  yeniden okutun.
- Eşleştirme kodu 60 saniyede bir kendiliğinden yenilenir; "Yeni eşleştirme
  kodu oluştur"a basmanız gerekmez.

## 8. Altyazı görüntüleme ayarları (TV tarafı)

TV ekranının **sağ üst köşesindeki "Yazı ayarları"** düğmesine kumandanızla
(D-pad) gidip basarak bir panel açabilirsiniz ("Yazı ayarlarını gizle" ile
kapanır). Buradan:

- **Font boyutu** büyütülüp küçültülebilir,
- Altyazının ekranda **üstte veya altta** gösterilmesi seçilebilir,
- **Arka plan opaklığı** (okunabilirlik için altyazının arkasındaki
  karartma) ayarlanabilir,
- **Kenar boşluğu** (ekranın kenarına ne kadar yakın/uzak durulacağı)
  ayarlanabilir,
- "Varsayılanlara dön" ile tüm ayarlar sıfırlanabilir.

Bu ayarlar TV'de kalıcı olarak saklanır (uygulama yeniden açıldığında
korunur).

### Gelişmiş / Test modu (TV)

Sağ üstteki ikinci düğme olan **"Gelişmiş / Test modu"**, normal kullanımda
gerekmeyen teknik bilgileri gösterir: TV'nin IP:port'u (elle bağlanmak
için), otomatik keşif durumu, algılanan HDMI girişleri ve canlı HDMI
görüntüsü denemesi. Açıkken düğme sarı renkte **"Test modu: AÇIK"** yazar;
tekrar basınca kapanır. Varsayılan: **kapalı**. Bu mod esas olarak donanım
testleri içindir (bkz. `docs/testing/P0-DONANIM-TEST-REHBERI.md`).

## 9. "Bu TV'yi hatırla" ve yeniden bağlanma

QR tarayarak veya otomatik keşifle (NSD) bir TV'ye bağlandığınızda, TV
varsayılan olarak otomatik şekilde **"Hatırlanan TV'ler"** listesine
eklenir. Bunu kapatmak isterseniz, diyalogda **"QR okunmuyor mu? Elle
gir"** seçeneğini açınca görünen **"Yeni TV'leri hatırla"** anahtarını kapatın
(varsayılan: açık).

Bir dahaki sefer "TV'ye Yansıt"ı açtığınızda:

- "Hatırlanan TV'ler" listesinde o TV'ye dokunursunuz — host/port
  otomatik doldurulur, giriş alanları açılır VE TV'nin pinlenmiş
  fingerprint'i yeniden kullanılır (güvenlik seviyesi düşmez).
- Yine de TV ekranındaki **GÜNCEL** (o anki, tek kullanımlık) eşleştirme
  kodunu girmeniz gerekir — kod güvenliği her bağlantıda yeniden
  doğrulanır, "hatırlama" bu adımı atlamaz.
- Bir TV'yi artık hatırlamak istemiyorsanız, listede o kaydın yanındaki
  **"Unut"** düğmesine dokunun.

**Bu özelliğin gerçek değeri neye bağlı?** TV'nin TLS kimliği (sertifika
fingerprint'i), Android'in `AndroidKeyStore`'unda **kalıcı** olarak
saklanır — yani TV uygulaması yeniden başlatılsa (ör. TV'yi kapatıp
açtığınızda) bile fingerprint **değişmez**, hatırlanan kayıt geçerliliğini
korur. (Bu kalıcılık mekanizması gerçek bir Android TV cihazında henüz
test edilmedi — bkz. [Bilinen sınırlar](#11-bilinen-sınırlar-dürüstlük-ilkesi).
Eğer bir nedenle kalıcı kimlik oluşturulamazsa, TV sessizce geçici bir
kimliğe döner ve o durumda hatırlanan TV'yi yeniden QR ile taramanız
gerekir — bu bir hataya yol açmaz, yalnızca hatırlama kalıcılığını
kaybedersiniz.)

## 10. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| TV listede görünmüyor (otomatik keşif) | Telefon ve TV aynı Wi-Fi ağında olmalı; bazı router'larda mDNS/NSD kapalıdır — QR tarayın veya elle girin. |
| QR okunmuyor | Ekran parlaklığını artırın, telefonu TV ekranına biraz yaklaştırın/açıyı değiştirin; elle giriş yedek yoldur. |
| Elle giriş için TV'nin IP'sini göremiyorum | IP:port varsayılan ekranda gizlidir — TV'de sağ üstteki "Gelişmiş / Test modu"nu açın. |
| TV ekranında çok fazla teknik yazı var | "Test modu: AÇIK" yazıyorsa düğmeye tekrar basıp kapatın. |
| Çeviri gelmiyor, ilk açılış | Çeviri modeli ilk kullanımda indirilir — telefon Wi-Fi'ye bağlı olsun ve birkaç dakika bekleyin. İndirme başarısız olursa uygulama 15 saniyede bir kendiliğinden yeniden dener. |
| Telefon yan çevrilince ekran dönmüyor | Telefonun hızlı ayarlarından **otomatik döndürme**yi açın (uygulama sistemin döndürme kilidine uyar). |
| Çeviri bazen geliyor bazen gelmiyor | Altyazı satırının kamera görüntüsündeki beyaz çerçevenin **içinde** kaldığından emin olun; telefonu sabitleyin, TV'ye biraz yaklaşın ve parlamayı (yansımayı) azaltın. |
| TV'de "bağlı" görünüyordu ama altyazı gelmiyor | Bağlantı koparsa telefon artık bunu "bağlantı başarısız" olarak gösterir — "TV'ye Yansıt" ile yeniden bağlanın. |
| Çeviri sesli okunmuyor | Telefonun ayarlarında Türkçe metin okuma (TTS) sesinin kurulu olduğunu kontrol edin. |
| "Eşleştirme kodu geçersiz/süresi geçti" | Kod 60 saniye geçerlidir — TV ekranındaki GÜNCEL kodu yeniden okuyun/girin. |
| Bağlantı kuruluyor ama altyazı gelmiyor | Telefonda kamera doğru şekilde ekrana yönlendirilmiş mi ve İngilizce altyazı ekranda GÖRÜNÜYOR mu kontrol edin — OCR, görünmeyen bir yazıyı okuyamaz. |
| Bağlantı aniden kesildi, yeniden bağlanmıyor | Otomatik yeniden bağlanma şu an YOK (bkz. Bilinen sınırlar) — "TV'ye Yansıt"ı tekrar açıp yeniden bağlanın. |
| "Hatırlanan TV" artık bağlanmıyor | TV yeniden başlatıldıktan sonra kimliği değişmiş olabilir (bkz. bölüm 9) — TV'yi listede "Unut"un, QR ile yeniden tarayıp yeniden hatırlatın. |
| TV'de "Bilinmeyen kaynaklardan yükleme engellendi" | Android TV ayarlarında güvenlik/kısıtlamalar menüsünden Downloader uygulamasına (veya kullandığınız yükleyiciye) izin verin. |

## 11. Bilinen sınırlar (dürüstlük ilkesi)

Bu proje aktif geliştirme altındadır. Bu kılavuzun yazıldığı an
itibariyle **henüz yapılmamış/doğrulanmamış** olanlar:

- **Gerçek donanım testi yok:** Kamera→OCR→çeviri akışı, telefon↔TV
  bağlantısı ve "bu TV'yi hatırla"nın kalıcılığı gerçek bir Android
  telefon/TV'de hiç denenmedi — bkz. kök `README.md`'deki "Doğrulama
  durumu" bölümü ve `docs/compatibility/hardware.md`.
- **Doğrudan konsol bağlantısı (PS5/PS4/Xbox) yok** — şu an yalnızca
  "kamerayı ekrana doğrultma" yolu var.
- **Samsung Tizen / LG webOS TV uygulamaları yok** — şu an yalnızca
  Android TV/Google TV desteklenir.
- **Akıllı Ekran (menü/envanter/görev çevirisi, Pro katman) yok.**
- **Karakter dublajı yok:** Çeviri yalnızca telefonun standart Türkçe
  metin okuma (TTS) sesiyle okunur; oyun karakterlerine özel Türkçe
  seslendirme yok.
- **Oyun profili seçimi arayüzde yok:** Oyun türüne göre terim koruma
  altyapısı kodda var ama uygulamada henüz bir seçim ekranı yok.
- **Oyuna özel hazır çeviri paketleri yok:** Genel ifadeler kütüphanesi ve
  kişisel düzeltmeler var (bkz. "Çeviri kütüphanesi"), ancak belirli bir
  oyunun tüm diyaloglarını kapsayan bir paket henüz üretilmedi (yol
  haritası G074–G076); kütüphanede olmayan satırlar canlı çevrilir.
- **Otomatik yeniden bağlanma sınırlı** — kopan bağlantı ~1,5 dakika
  boyunca kendiliğinden yeniden denenir; TV uygulaması kapatılıp
  açıldıysa ya da süre dolduysa QR'ı yeniden okutmanız gerekir.
- **TV'nin kendi HDMI girişine geçince altyazı görünmez** — altyazının
  oyunun üstünde görünmesi için GAMLETSOL TV uygulaması açık kalmalı ve
  oyun görüntüsü onun içinde gösterilmeli ("Oyun görüntüsü: Açık"). Bunun
  her TV modelinde çalışıp çalışmadığı henüz gerçek cihazda doğrulanmadı.
- **Ücretli/Pro katman ayrımı henüz uygulanmadı** — tüm özellikler şu an
  ücretsizdir.
- **Debug imzalı APK'lar:** Dağıtılan APK'lar "release" imzalı değildir
  (bkz. bölüm 4a) — bu, uzun vadede Google Play yayınına geçerken
  değişecektir.

## 12. Geliştiriciler için — kaynaktan derleme

Projeyi kendiniz derlemek/değiştirmek isterseniz, kök
[`README.md`](../README.md)'nin "Geliştirme" bölümüne bakın:

```bash
./gradlew :core-model:test :core-pipeline:test :core-pairing:test :core-transport:test  # platformdan bağımsız birim testleri
./gradlew :app-mobile:assembleDebug              # telefon debug APK (Android SDK gerektirir)
./gradlew :app-tv:assembleDebug                  # TV probe debug APK (Android SDK gerektirir)
```

Modül sınırları, gizlilik ilkesi ve diğer katkı kuralları için kök
[`AGENTS.md`](../AGENTS.md)'ye bakın.
