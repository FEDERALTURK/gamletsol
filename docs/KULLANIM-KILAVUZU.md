# GAMLETSOL — Kurulum ve Kullanım Kılavuzu

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
5. Uygulama kamera izni isteyecektir (altyazıyı okumak için zorunludur)
   — izin verin.

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

1. **TV tarafında** "GAMLETSOL" uygulamasını açın. Ekranda:
   - TV'nin yerel IP adresi ve port numarası,
   - 60 saniye geçerli, tek kullanımlık bir **eşleştirme kodu**,
   - aynı bilgiyi taşıyan bir **QR kod**
   görünür.
2. **Telefon tarafında** GAMLETSOL'u açın, kamerayı ekrana doğrultmaya
   başlayın (ana ekran) ve **"TV'ye Yansıt"** düğmesine dokunun.
3. Açılan diyalogda üç yol vardır:
   - **Otomatik keşif (NSD):** Telefon ve TV aynı Wi-Fi ağındaysa, TV
     listede otomatik görünür — dokunup seçin.
   - **QR tara:** Kamerayla TV ekranındaki QR kodu okutun — IP/port/kod
     otomatik doldurulur.
   - **Elle gir:** IP adresini, portu ve TV ekranındaki GÜNCEL eşleştirme
     kodunu kendiniz yazın (NSD/QR çalışmazsa yedek yol).
4. "Bağlan"a dokunun. Bağlantı başarılı olursa TV ekranında telefon
   tarafından gönderilen altyazılar görünmeye başlar.

> Eşleştirme kodu 60 saniye içinde kullanılmazsa veya yanlış girilirse
> bağlantı reddedilir — TV ekranındaki kodu/QR'ı yeniden okuyun ya da TV
> uygulamasını kapatıp yeniden açarak yeni bir kod üretin.

**Güvenlik notu:** QR tarama ve otomatik keşif (NSD) yolları, TV'nin TLS
sertifika "fingerprint"ini de taşır ve telefon bu TAM fingerprint'i
**pinler** — yani bağlantı şifrelidir ve yerel ağınızdaki biri "sahte bir
TV" gibi davranıp araya giremez. Elle giriş yolu bu korumayı **sağlamaz**
(fingerprint içermez) — yalnızca güvendiğiniz bir ağda, NSD/QR
çalışmadığında yedek olarak kullanın.

## 8. Altyazı görüntüleme ayarları (TV tarafı)

TV ekranında, kumandanızla (D-pad) gezinip **"Yazı ayarları"** düğmesine
basarak bir panel açabilirsiniz. Buradan:

- **Font boyutu** büyütülüp küçültülebilir,
- Altyazının ekranda **üstte veya altta** gösterilmesi seçilebilir,
- **Arka plan opaklığı** (okunabilirlik için altyazının arkasındaki
  karartma) ayarlanabilir,
- **Kenar boşluğu** (ekranın kenarına ne kadar yakın/uzak durulacağı)
  ayarlanabilir,
- "Varsayılanlara dön" ile tüm ayarlar sıfırlanabilir.

Bu ayarlar TV'de kalıcı olarak saklanır (uygulama yeniden açıldığında
korunur).

## 9. "Bu TV'yi hatırla" ve yeniden bağlanma

QR tarayarak veya otomatik keşifle (NSD) bir TV'ye bağlandığınızda,
telefon tarafındaki eşleştirme diyaloğunda **"Yeni TV'leri hatırla"**
anahtarı görürsünüz (varsayılan: açık). Açıkken, bağlandığınız TV
otomatik olarak **"Hatırlanan TV'ler"** listesine eklenir.

Bir dahaki sefer "TV'ye Yansıt"ı açtığınızda:

- "Hatırlanan TV'ler" listesinde o TV'yi görür, **"Bağlan"**a
  dokunursunuz — host/port otomatik doldurulur VE TV'nin pinlenmiş
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
- **Türkçe seslendirme (karakter dublajı veya TTS) yok.**
- **Otomatik yeniden bağlanma yok** — bağlantı koptuğunda "TV'ye
  Yansıt"ı elle yeniden açmanız gerekir.
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
