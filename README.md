# GAMLETSOL — Game Translate Console

Oyunların ekranda zaten gösterdiği İngilizce altyazıyı **OCR ile okuyup**
Türkçeye çeviren Android uygulaması. Güncel ve en ayrıntılı yol haritası:
[`docs/roadmap/GAMLETSOL geliştirme yol haritası.md`](docs/roadmap/GAMLETSOL%20geli%C5%9Ftirme%20yol%20haritas%C4%B1.md)
— görev kırılımı [`docs/roadmap/tasks/todo.md`](docs/roadmap/tasks/todo.md)
(G001–G072), özet [`docs/roadmap/tasks/plan.md`](docs/roadmap/tasks/plan.md),
mimari şema [`docs/roadmap/GAMLETSOL_mimari.svg`](docs/roadmap/GAMLETSOL_mimari.svg).

**Uygulamayı kurup kullanmak mı istiyorsunuz?** APK indirme, telefon/TV
kurulumu, eşleştirme ve sorun giderme dahil adım adım rehber:
[`docs/KULLANIM-KILAVUZU.md`](docs/KULLANIM-KILAVUZU.md).

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
HDMI giriş keşfi, canlı HDMI üstüne sabit Türkçe yazı denemesi). Bunlar kod
yazmadan önce **gerçek bir Android TV / Google TV cihazında** elle/spike ile
denenmesi gereken adımlar; bu kum havuzunda donanım erişimi olmadığı için
burada "tamamlandı" işaretlenemez — kullanıcının kendi TV'sinde yapılması
gerekir (bkz. `docs/roadmap/tasks/todo.md` G001-G003 kabul/doğrulama
kriterleri).

### `app-tv` — G002/G003 kanıt (probe) APK'sı eklendi (2026-11-09)

G002-G003'ü gerçek donanımda denemeyi mümkün kılmak için yeni bir Android
modül eklendi: **`app-tv`**. Bu, **herhangi bir standart Android TV /
Google TV** cihazında kurulabilen genel bir uygulamadır — Profilo'ya veya
başka bir markaya özel hiçbir API kullanmaz, yalnızca AOSP'nin genel
`android.media.tv.TvInputManager`/`TvView`/`TvContract` API'lerini
kullanır.

- Açılışta cihazdaki TV girişlerini listeler (`TvInputBridge`), HDMI
  passthrough girişlerinden birini seçer (`core-model`'deki saf Kotlin
  `selectPreferredHdmiPassthroughInput` — bu kum havuzunda **gerçekten
  test edildi**, 4 birim testi geçti) ve `TvView` ile o girişe tune olmayı
  dener (`PassthroughTuner`).
- Ekranın altına, canlı güncellenen bir saat içeren sabit bir Türkçe test
  yazısı bindirir (ı/İ/ş/Ğ/ü/ö/ç dahil) — saatin akması, yazının donmuş bir
  ekran görüntüsü olmadığını, gerçekten üst üste render edildiğini
  kanıtlamak içindir.
- Birden fazla HDMI passthrough girişi bulunursa aralarında geçiş yapan bir
  düğme sunar.

**Doğrulanmadı (dürüstlük ilkesi):** `app-tv` bu kum havuzunda yerel olarak
derlenmedi (Android SDK yok — diğer Android modülleriyle aynı sınırlama).
Daha da önemlisi, **TvInputManager/TvView'ın gerçek bir Android TV/Google TV
cihazında HDMI passthrough girişini listeleyip görüntü üstüne yazı
bindirmesi hiç test edilmedi** — bu tam olarak G002/G003'ün kanıtlamaya
çalıştığı şey. Kullanıcının bu APK'yı kendi TV'sine kurup sonucu
`docs/compatibility/hardware.md` dosyasına işlemesi gerekiyor.

### `core-transport` + TV↔telefon bağlantısı — P3/G022-G030'un ilk sürümü (2026-11-09)

Kullanıcı netleştirmesi: asıl istenen özellik, telefonu kamerayla altyazı
okumakla sınırlı değil — **telefon ile TV arasında bağlantı kurup çeviriyi
TV ekranına yansıtmak**. Bu, yol haritasının P3 fazıdır (G022-G030); bu
güncellemeden önce bu faz için **sıfır kod** vardı. Şimdi eklenen ilk/MVP
sürümü:

- **`core-transport`** (saf Kotlin/JVM, `core-model`/`core-pipeline` gibi
  Android SDK gerektirmez): `CueEvent` (tek bir altyazı/çeviri olayı) +
  `CueEventCodec` (Base64 tabanlı, bağımlılıksız, satır bazlı kodlama) +
  `TvEventServer` (TV tarafı TCP sunucusu) + `TvEventClient` (telefon tarafı
  TCP istemcisi).
- **`app-tv`**: artık açılışta bir `TvEventServer` başlatır, ekranında
  bağlanılacak IP:port'u gösterir ve telefondan gelen gerçek `CueEvent`'leri
  (G001-G003 kanıtındaki sabit test yazısı yerine) overlay'de gösterir.
- **`app-mobile`**: üst menüye "TV'ye Yansıt" öğesi eklendi
  (`TvConnectDialog`) — kullanıcı TV'nin IP/port'unu ve eşleştirme kodunu
  elle girer, `CaptionViewModel.connectToTv()` bağlanır; bağlandıktan sonra
  pipeline'ın ürettiği her cue, mevcut telefon-ekranı sink'ine ek olarak
  yeni `TvRelaySubtitleSink` üzerinden TV'ye de gönderilir.

**Gerçekten doğrulanan:** `core-transport`'un TCP davranışı — satır bazlı
okuma, Base64 kodlama, bağlantı kapatma — bu kum havuzunda **gerçek
127.0.0.1 loopback soketleri üzerinden** birim testleriyle doğrulandı
(`TvEventServerClientTest`, `CueEventCodecTest`; mock değil, gerçek
`ServerSocket`/`Socket`). Bu testler sırasında GERÇEK bir yarış koşulu
(race condition) bulundu ve düzeltildi: istemcinin `connect()`'i, sunucunun
`accept()`'i tamamlayıp soketi kayıt altına almasını BEKLEMİYORDU — `stop()`
tam o aralıkta çağrılırsa bağlantı hiç kapatılmadan açık kalabiliyordu;
şimdi kabul ve durdurma aynı kilit altında atomik yapılıyor (bkz. kod
yorumları, `TvEventServer`).

### `core-pairing` — tek kullanımlık eşleştirme kodu (G023, 2026-11-09)

`core-transport`'un bıraktığı en açık güvenlik boşluğu — IP:port'u bilen
HERKESİN bağlanabilmesi — yol haritasının G023 adımıyla kısmen kapatıldı:

- **`core-pairing`** (saf Kotlin/JVM): `PairingToken` (60 saniye geçerli,
  insan tarafından yazılabilir kısa kod — `"7F3K-9QXZ"` gibi, karıştırılabilir
  `0/O/1/I/L` karakterleri hariç) + `PairingTokenGenerator` + `PairingRegistry`
  (tek seferde bir aktif kod; tekrar kullanım/süresi geçmiş/bilinmeyen kod
  reddi; yeni kod üretimi öncekini otomatik geçersiz kılar — "ikinci yayıncı"
  senaryosu).
- **`core-transport`**: `TvEventServer`/`TvEventClient`'a bir el sıkışma
  eklendi — bağlantı kurulduğunda istemci `PAIR:<kod>` gönderir, sunucu
  `PairingRegistry` ile doğrular ve `PAIR_OK`/`PAIR_REJECTED` ile yanıtlar;
  istemci bu yanıtı BEKLER (yalnızca yazmanın başarılı olması yeterli
  sayılmaz — bu ayrımın kendisi bir test yazılırken bulunan gerçek bir
  tasarım hatasıydı, bkz. kod yorumları).
- **`app-tv`**: açılışta bir kod üretir, ekranda geri sayımla gösterir,
  "Yeni eşleştirme kodu oluştur" düğmesi sunar.
- **`app-mobile`**: `TvConnectDialog`'a bir "Eşleştirme kodu" alanı eklendi.

**Gerçekten doğrulanan:** `core-pairing`'in token üretimi/doğrulaması (9
testi) ve core-transport ile birlikte gerçek bir loopback soketi üzerindeki
el sıkışma protokolü (4 ek test: doğru kod, yanlış kod, tekrar kullanım,
eksik kod) — hepsi roadmap'in G023 için açıkça istediği senaryoları
(süresi geçmiş QR, ikinci yayıncı, yanlış TV, tekrar deneme) kapsıyor.

**Doğrulanmadı / bilinçli MVP sınırları (dürüstlük ilkesi, hâlâ geçerli):**
- **Kanal artık ŞİFRELİ** (bkz. aşağıdaki "TLS + sertifika fingerprint
  pinning" bölümü, G024, 2026-11-09) — bu notun yazıldığı an itibariyle
  eksikti, şimdi tamamlandı. QR ile taranan bağlantılarda TV'nin kimliği de
  PİNLENİR (gerçek MITM koruması); manuel girişte kanal yine şifrelidir ama
  TV kimliği pinlenmez.
- **Kamera ile QR TARAMA artık VAR** (bkz. aşağıdaki "Kamerayla QR
  eşleştirme" bölümü, 2026-11-09) — bu notun yazıldığı an itibariyle eksikti,
  şimdi tamamlandı; yine de gerçek donanımda hiç denenmedi.
- **NSD (otomatik TV keşfi) artık VAR** (bkz. aşağıdaki "NSD/mDNS otomatik
  keşif" bölümü, G025, 2026-11-09) — bu notun yazıldığı an itibariyle
  eksikti, şimdi tamamlandı; QR ve manuel giriş yine de kalır (yedek yol).
- **Reconnect/hatırlama artık VAR** (G028-G029, bkz. aşağıdaki
  "G026-G029" ve "G029 kimlik kalıcılığı" bölümleri, 2026-11-09) — bu
  notun yazıldığı an itibariyle eksikti, şimdi tamamlandı; otomatik
  yeniden bağlanma (bağlantı koptuğunda istemcinin kendiliğinden yeniden
  denemesi) hâlâ YOK — kullanıcı manuel olarak yeniden bağlanmalı.
- Gerçek bir telefon ile gerçek bir TV arasında, gerçek bir Wi-Fi ağında
  **hiç denenmedi** (NAT/güvenlik duvarı, Wi-Fi gecikmesi, bağlantı kopması
  gibi gerçek dünya koşulları test edilmedi).
- Bu özellik, hâlâ doğrulanmamış G001-G003 (HDMI passthrough üstüne yazı
  bindirme) sonucundan **bağımsız** olarak inşa edildi — ikisi birbirini
  gerektirmez (TV overlay yerine TV'nin kendi uygulama ekranında metin
  göstermek yeterli), ama tam "oyun görüntüsü + çeviri aynı TV ekranında"
  vaadi için G001-G003'ün de ayrıca doğrulanması gerekiyor.

### `core-transport` — TV yetenek sözleşmesi (G022, 2026-11-09)

Yol haritasındaki `TvReceiverAdapter (probeCapabilities, pair, sendSubtitle,
sendLayout, clear, heartbeat, disconnect)` arayüzü eklendi:

- **`TvCapabilities`**: TV'nin desteklediği protokol sürümü + üç bayrak
  (`clear`, `heartbeat`, `layout`). `TvEventServer`, eşleştirme adımından
  (varsa) SONRA, cue döngüsüne girmeden önce bunu HER bağlantıda tek satır
  olarak gönderir; `TvEventClient.connect()` bunu okuyup `capabilities`
  olarak saklar — satır eksik/bozuksa (eski/uyumsuz bir taraf) bağlantı
  `null` döner, tıpkı eşleştirme reddi gibi.
- **`TvReceiverAdapter`** arayüzü + **`SocketTvReceiverAdapter`** (bu
  modüldeki TCP istemcisini saran tek uygulama) eklendi.
  `TvRelaySubtitleSink` (app-mobile) artık doğrudan `TvEventClient`'a değil
  bu arayüze bağlı — ileride başka bir TV ailesi (Samsung/LG) eklenirse
  `TvRelaySubtitleSink`'in değişmesi gerekmeyecek.
- Roadmap'teki `pair` ve `probeCapabilities` metodları burada AYRI çağrılar
  DEĞİL: `pair`, `TvEventClient.connect()`'in zaten bir parçası; yetenek
  bildirimi de bağlanırken otomatik alınıyor, ayrıca sorulmuyor.

**Dürüstçe eksik/kısıtlı olanlar:**
- `TvCapabilities.CURRENT` şu an HER ZAMAN aynı sabit değeri gönderiyor —
  gerçek, çalışma-zamanında-değişen bir yetenek keşfi değil (tek bir TV
  alıcısı uygulaması olduğu için henüz gerek yok).
- `clear` ve `layout` bayrakları `false` — protokolde "temizle" mesaj türü
  ve Akıllı Ekran hâlâ yok.
- `heartbeat()` **gerçek bir round-trip PING/PONG DEĞİL** — yalnızca yerel
  soket durumunu kontrol ediyor; karşı tarafın ağ bağlantısını sessizce
  kaybetmesi (Wi-Fi kopması, çökme) hâlâ fark edilemeyebilir. Gerçek
  canlılık kontrolü G028'e (yeniden bağlanma/oturum yönetimi) bırakıldı.
- G024 (TLS + sertifika fingerprint) artık eklendi — bkz. aşağıdaki "TLS +
  sertifika fingerprint pinning" bölümü (2026-11-09).

**Gerçekten doğrulanan:** `TvCapabilities.encode()`/`decode()` round-trip
ve bozuk/eksik satır davranışı (6 test), `SocketTvReceiverAdapter`'ın gerçek
bir loopback soketi üzerinde yetenek bildirimini doğru okuduğu ve
`sendSubtitle`/`clear`/`heartbeat`/`disconnect`'in sözleşmeye uyduğu (3
test), ve eşleştirmeden sonra istemcinin yetenek bildirimini aldığı (1 yeni
test, `TvEventPairingHandshakeTest`). Ayrıca bu değişiklik, eski
`"missing pairing code"` testindeki flaky "eventually fails" kontrolünü
ortadan kaldırdı: artık kodsuz bir istemcinin `connect()`'i, sunucu
yetenek bildirimini göndermeden karşılıklı kilitlenip zaman aşımına
uğradığı için, temiz ve HIZLI bir şekilde `null` döner (bkz. kod yorumları).

### Kamerayla QR eşleştirme — G023'ün eksik kalan QR kısmı tamamlandı (2026-11-09)

`core-pairing`/`core-transport` bölümlerinde "kamera ile QR TARAMA YOK"
diye açıkça işaretlenen eksik artık kapatıldı:

- **`core-pairing`**: `PairingQrPayload` (host/port/eşleştirme kodunu TEK
  bir metne kodlar/çözer — `"gamletsol-pair:1;host=...;port=...;code=..."`)
  + `QrMatrix`/`QrMatrixEncoder` (zxing-core ile, saf Kotlin/JVM — Android
  bağımlılığı YOK, bu yüzden `core-pairing`'in "Android SDK gerektirmez"
  kuralı bozulmadı).
- **`app-tv`**: eşleştirme kodunun yanına, aynı host/port/kodu taşıyan bir
  QR kodu da çiziliyor (`QrCodeView`, salt Compose `Canvas` ile — ek bir
  Android QR kütüphanesi gerekmedi).
- **`app-mobile`**: "TV'ye Yansıt" diyaloğuna bir "QR ile tara" düğmesi
  eklendi; basıldığında tam ekran bir kamera tarayıcısı (`QrScannerScreen`,
  CameraX + ML Kit `barcode-scanning`) açılır, QR okunduğunda IP/port/kod
  OTOMATİK doldurulup bağlantı başlatılır. **Manuel giriş alanları
  KALDIRILMADI** — QR okunamazsa/kamerası yoksa kullanıcı hâlâ elle
  girebilir (roadmap'in zaten istediği yedek yol, G025).
- Ana OCR kamerası ile QR tarayıcı AYNI ANDA bağlı değildir: QR tarama
  ekranı açıldığında ana kamera composition'dan tamamen kaldırılıp
  `ProcessCameraProvider.unbindAll()` + yeni bir bağlama yapılır; tarama
  bitip ana ekrana dönüldüğünde `CameraPreview` composable'ı YENİDEN
  oluşturulduğu için OCR kamerası otomatik olarak yeniden bağlanır.

**Gerçekten doğrulanan:** `PairingQrPayload`'ın kodlama/kod çözme
davranışı (7 test — round-trip, eksik/bozuk alan, bilinmeyen sürüm,
ilgisiz bir QR'ın [ör. Wi-Fi QR'ı] sessizce reddi) ve `QrMatrixEncoder`'ın
zxing ile gerçekten kare, hem karanlık hem açık hücreler içeren bir matris
ürettiği (4 test) — core-pairing artık 20 test (önceden 9).

**Doğrulanmadı / dürüstçe eksik kalan (MVP sınırı):**
- Bu QR'ın gerçek bir TV ekranından, gerçek bir telefon kamerasıyla
  (parlaklık, açı, mesafe, ekran yansıması) okunup okunamadığı bu kum
  havuzunda **hiç denenmedi** — yalnızca kodlama/kod çözme mantığı birim
  test edildi, kamera/gerçek donanım yok.
- QR, yalnızca IP/port/kodu taşır — NSD/mDNS tabanlı gerçek bir otomatik
  keşif artık VAR (bkz. aşağıdaki "NSD/mDNS otomatik keşif" bölümü,
  G025, 2026-11-09), ama QR ve bu manuel/elle-tarama yolu da yedek olarak
  kalıyor.
- G024 (TLS + sertifika fingerprint) artık eklendi — QR'ın sağladığı
  "fingerprint'i elle yazmaya gerek yok" imkanı tam olarak kullanıldı, bkz.
  bir aşağıdaki bölüm.

### TLS + sertifika fingerprint pinning (G024, 2026-11-09)

`core-pairing`'in "kanal hâlâ şifrelenmemiş" notuyla işaretlediği boşluk
kapatıldı — QR taramanın tamamlanması, 64 karakterlik bir SHA-256
fingerprint'i QR payload'ı üzerinden taşıma imkanı verdiği için artık bu
adım mümkün:

- **`core-transport`**: `TvTlsIdentity` (Bouncy Castle `bcprov-jdk18on` +
  `bcpkix-jdk18on` ile — Android ART'ın `sun.security.x509.*`'ı
  içermemesi nedeniyle saf JDK API'leri yetersiz kaldı) açılışta, **kalıcı
  OLMAYAN** (uygulama her başlatıldığında yeniden üretilen) kendi-imzalı
  bir X.509 sertifikası + anahtar çifti üretir ve SHA-256 fingerprint'ini
  hesaplar. `TvEventServer`'ın dinleme soketi artık KOŞULSUZ bir
  `SSLServerSocket`'tir (düz TCP yedeği YOK — kırılma değişikliği, bkz.
  aşağıdaki not). `TvEventClient.connect()` düz soketi koşulsuz bir
  `SSLSocket`'e yükseltir ve `startHandshake()`'i açıkça çağırır.
- **Fingerprint pinning**: `TvEventClient.connect(..., expectedFingerprintSha256 = ...)`
  verilirse, özel bir `X509TrustManager` TV'nin sunduğu sertifikanın
  SHA-256'sını bu değerle karşılaştırır — uyuşmazsa bağlantı reddedilir
  (gerçek MITM koruması; eşleştirme kodu KİMLİK doğrulamaz, bunu pinning
  yapar). `expectedFingerprintSha256 = null` geçilirse (manuel giriş
  yolu — 64 karakterlik bir hex'i elle yazmak pratik değil), bağlantı yine
  TLS üzerinden ŞİFRELİDİR ama TV'nin kimliği PİNLENMEZ.
- **`core-pairing`**: `PairingQrPayload`'a `fingerprintSha256` alanı
  eklendi, protokol sürümü 1'den 2'ye çıkarıldı — eski (v1) bir QR, bu
  alanı taşımadığı için artık bilerek reddedilir (`decode()` → `null`);
  v1/v2 karışık bir TV+telefon çiftinin "yarı güvenli" bir bağlantı
  kurmasına izin vermek yerine net bir uyumsuzluk hatası tercih edildi.
- **`app-tv`**: `MainActivity` açılışta bir `TvTlsIdentity` üretir, bunu
  `TvEventServer`'a verir ve QR payload'ına fingerprint'ini ekler.
- **`app-mobile`**: `TvRelaySubtitleSink.connectBlocking()`,
  `CaptionViewModel.connectToTv()` ve `CaptionScreen`'deki hem QR-tarama
  hem manuel-giriş çağrı yolları, yeni `fingerprintSha256`/
  `expectedFingerprintSha256` parametresini taşıyacak şekilde güncellendi
  — QR yolunda gerçek değer, manuel yolda bilerek `null` geçilir.

**Gerçekten doğrulanan:** `TvTlsIdentity.generate()`'in geçerli bir
kendi-imzalı sertifika + anahtar çifti ürettiği, `TvEventServer`/
`TvEventClient`'ın artık her zaman gerçek bir TLS el sıkışması üzerinden
(127.0.0.1 loopback, mock değil) bağlandığı, doğru fingerprint'le bağlanmanın
başarılı olduğu, YANLIŞ fingerprint'in doğru eşleştirme koduyla BİLE
reddedildiği (MITM senaryosu simülasyonu — iki ayrı `TvTlsIdentity`
üretilip biri "saldırgan" sertifikası gibi kullanıldı) ve fingerprint
verilmeden (manuel giriş) bağlantının yine şifreli kanalda çalıştığı (3 yeni
test, `TvEventPairingHandshakeTest`); `PairingQrPayload`'ın v2 formatını
doğru kodladığı/çözdüğü ve eski v1 formatını bilerek reddettiği (yeni
testler, `PairingQrPayloadTest`). `core-transport` 21→24 teste, `core-pairing`
20→22 teste çıktı (bkz. "Doğrulama durumu"). Tüm testler bu kum havuzunda
`--rerun-tasks` ile 5 kez tekrar çalıştırılıp TLS el sıkışmasında flaky bir
davranış olmadığı ayrıca doğrulandı.

**Doğrulanmadı / bilinçli MVP sınırları (dürüstlük ilkesi):**
- (2026-11-09 — ÇÖZÜLDÜ, bkz. aşağıdaki "G029 kimlik kalıcılığı" bölümü)
  Bu notun yazıldığı sırada `TvTlsIdentity` KALICI DEĞİLDİ (`generate()`,
  her başlatmada yeni sertifika/fingerprint). `app-tv` artık
  `PersistentTvIdentity` ile `AndroidKeyStore`'da kalıcı bir kimlik
  kullanıyor; `generate()`'in kendisi hâlâ (kasıtlı olarak) birim testler
  için geçici kalıyor.
- Sunucu, istemci sertifikası TALEP ETMEZ/doğrulamaz (tek yönlü TLS) — bu
  MVP için kabul edilebilir kabul edildi (telefon tarafının kimliği zaten
  eşleştirme koduyla doğrulanıyor).
- Gerçek bir telefon ile gerçek bir TV arasında, gerçek bir Wi-Fi ağında
  TLS el sıkışması/fingerprint pinning **hiç denenmedi** — yalnızca
  127.0.0.1 loopback üzerinde test edildi; gerçek ağ koşullarında (NAT,
  sertifika/saat uyumsuzluğu gibi) sorun çıkıp çıkmayacağı bilinmiyor.
- QR'ın gerçek bir kamerayla gerçek bir TV ekranından okunabildiği hâlâ
  doğrulanmadı (bkz. yukarıdaki QR bölümü) — bu nedenle fingerprint'in
  QR üzerinden gerçek dünyada taşınabildiği de dolaylı olarak doğrulanmamış
  sayılır.

### Lint uyarılarının temizlenmesi (2026-11-09)

CI'nın her zaman yeşil çıkardığı derleme/lint koşusu HATA vermiyordu, ama
`app-mobile` 44, `app-tv` 3 lint UYARISI biriktirmişti (gerçek CI
çalışmalarının `-reports` artifact'leri indirilip incelenerek bulundu).
Gerçek kod/yapılandırma sorunu olan uyarılar düzeltildi:

- **Eksik uygulama simgesi**: `app-mobile`'da hiç `res/` klasörü yoktu,
  dolayısıyla `android:icon` ayarlanmamıştı. Her iki uygulama da o anda
  `app-tv`'nin zaten kullandığı yaklaşımla sistemin genel yer tutucu
  simgesini (`@android:drawable/sym_def_app_icon`) kullanmaya başladı —
  gerçek bir marka/logo tasarımı o sırada yoktu, bu yalnızca lint'in
  "hiç simge yok" uyarısını gidermek içindi. (2026-11-09 — ÇÖZÜLDÜ, bkz.
  aşağıdaki "GAMLETSOL marka kimliği" bölümü: artık her iki uygulama da
  gerçek, tasarlanmış bir adaptive icon + TV banner kullanıyor.)
- **Eksik "data extraction rules"**: `android:allowBackup="false"` zaten
  her türlü yedeklemeyi kapatıyor, ama Android 12+ için
  `android:dataExtractionRules` ve minSdk 26-30 aralığı için de (eski
  biçim) `android:fullBackupContent`'in AYRICA belirtilmesini lint
  istiyor — her iki uygulamaya da "hiçbir şeyi yedekleme" diyen birer
  `res/xml/data_extraction_rules.xml` + `res/xml/backup_rules.xml`
  eklendi (gerçekte hiçbiri okunmuyor, çünkü yedekleme zaten tamamen
  kapalı — salt lint uyumluluğu).
- **Sabit ekran yönü uyarıları** (`LockedOrientationActivity` +
  `DiscouragedApi`): telefon uygulamasının `portrait` (OCR akışı için
  kasıtlı) ve TV uygulamasının `landscape` (TV'de döndürme kavramı yok)
  sabitlemesi BİLİNÇLİ bir tasarım kararı — `tools:ignore` ile açık bir
  Türkçe yorumla bastırıldı, kazara bir kısıtlama değil.
- **Autoboxing uyarısı**: `app-tv/MainActivity.kt`'deki `nowMillis`
  state'i `mutableStateOf<Long>` yerine `mutableLongStateOf` kullanacak
  şekilde değiştirildi (gerçek bir performans iyileştirmesi, davranış
  değişikliği yok).
- **`TrustAllX509TrustManager` uyarısı** (Güvenlik kategorisi, ilk bakışta
  en ciddi görünen): incelendiğinde bu uyarının BİZİM kodumuzdan
  gelmediği, bağımlılık olarak eklenen `bcpkix-jdk18on-1.78.1.jar`'ın
  İÇİNDEKİ bir sınıftan geldiği görüldü (lint, jar'ların `.class`
  dosyalarını da tarıyor) — `core-transport`'un kendi `pinningTrustManager`
  fonksiyonunun `checkServerTrusted`'ı ASLA boş değildir (G024'ün testleri
  zaten bunu kanıtlıyor). Her iki modüle, bu ayrımı açıklayan bir yorumla
  birlikte bu tek uyarıyı bastıran bir `lint.xml` eklendi.

**Bilerek DOKUNULMADI:** "Obsolete Gradle Dependency" (33 öneri) ve
"Obsolete Android Gradle Plugin Version" (6 öneri) — bunlar gerçek bir
kod/yapılandırma sorunu değil, yalnızca kullanılan kütüphanelerin/AGP'nin
daha yeni sürümlerinin var olduğu bildirimi. Bu kum havuzunda Android
SDK/gerçek derleme olmadığı için (yalnızca CI'da derlenebiliyor) birden
çok kütüphane sürümünü toptan yükseltmek, tek tek CI'da doğrulanana kadar
geriye dönük uyumluluk riski taşır — ayrı, küçük adımlarla denenmesi
gereken bir iş, lint "temizliği" kapsamına dahil edilmedi.

**Sonuç (gerçek CI'da doğrulandı, run #42594586779):** `app-mobile` 44→39
uyarı (kalan 39'u tamamen yukarıdaki "bilerek dokunulmadı" sürüm önerileri),
`app-tv` 3→0 uyarı.

### NSD/mDNS otomatik keşif (G025, 2026-11-09)

Roadmap'in G025 adımı: TV'yi aynı yerel ağda otomatik olarak bulma
(elle IP/port girme ya da QR tarama ZORUNLULUĞUNU kaldırma). Gerçek
Android `NsdManager`'ı (mDNS/DNS-SD) kullanılarak eklendi:

- **`core-transport`** (saf Kotlin/JVM): `TvNsdAttributes` — eşleştirme
  kodu + TLS fingerprint'i + protokol sürümünü, gerçek
  `NsdServiceInfo.setAttribute`/`attributes`'in beklediği
  `Map<String, ByteArray>` TXT kayıt biçimine kodlar/çözer; `DiscoveredTv`
  — çözümlenmiş bir TV'nin (serviceName/host/port/pairingCode/fingerprint)
  saf veri sınıfı. Servis türü sabiti: `_gamletsol._tcp.`.
- **`app-tv`**: `TvNsdAdvertiser`, gerçek `NsdManager.registerService` ile
  bu TV'yi ağda reklam eder; `MainActivity` her yeni eşleştirme kodu
  üretildiğinde (ilk açılış veya "Yeni eşleştirme kodu oluştur" düğmesi)
  reklamı günceller (Android'in NSD API'si mevcut bir kaydı YERİNDE
  güncellemeyi desteklemediği için önce kaldırıp sonra yeniden kaydeder).
  Ekranda "Otomatik keşif (NSD): ... yayında" satırı eklendi.
- **`app-mobile`**: `TvNsdDiscoverer`, gerçek
  `NsdManager.discoverServices`/`resolveService` ile ağdaki
  `_gamletsol._tcp` servislerini tarar ve çözümler. "TV'ye Yansıt"
  diyaloğu artık bulunan TV'leri bir liste olarak gösterir — birine
  dokunmak, QR ile aynı güvenlik seviyesinde (fingerprint pinlenerek)
  doğrudan bağlanır. QR ve manuel giriş KALDIRILMADI — NSD'nin
  engellendiği/desteklenmediği ağlar (bazı misafir ağları/router'lar mDNS
  trafiğini filtreler) için yedek yol olarak kalıyorlar; NSD başarısız
  olursa kullanıcıya engelleyici bir hata gösterilmez, liste sadece boş
  kalır.

**Gerçekten doğrulanan:** `TvNsdAttributes.toTxtRecordMap()`/
`fromTxtRecordMap()`'in round-trip çalıştığı, eksik/bozuk/ilgisiz bir TXT
kayıt setini doğru reddettiği (7 yeni test, `TvNsdAttributesTest`) — bu,
core-transport'u 24'ten 31 teste çıkardı. `TvNsdAdvertiser`/
`TvNsdDiscoverer`'ın kendisi Android'e bağımlı olduğu için bu kum
havuzunda unit test edilemedi (ama gerçek CI'da derlendi/lint geçti).

**Doğrulanmadı / bilinçli MVP sınırları (dürüstlük ilkesi):**
- Gerçek bir Android cihazda/TV'de NSD reklamı/taraması/çözümlemesi **hiç
  denenmedi** — `NsdManager`'ın kendisi bu kum havuzunda hiçbir zaman
  çalıştırılmadı (Android SDK yok); yalnızca TXT kayıt kodlama/kod çözme
  mantığı (saf Kotlin) birim test edildi.
- Bazı ev router'ları/misafir ağları mDNS (multicast) trafiğini
  engeller — bu durumda NSD hiç çalışmaz, kullanıcı sessizce boş bir
  "yakındaki TV'ler" listesiyle karşılaşıp QR/manuel girişe geçmek
  zorunda kalır; bu senaryo gerçek bir ağda hiç test edilmedi.
- `TvNsdAdvertiser.restart()`, mevcut kaydı kaldırıp yeniden kaydeder —
  bu kısa pencerede TV ağda GÖRÜNMEZ olabilir (gerçek bir cihazda bu
  pencerenin süresi/etkisi ölçülmedi).
- Bu, G025'in roadmap'teki TAM kapsamı olabilir ya da olmayabilir
  (örn. Samsung/LG TV'lere özel keşif mekanizmaları burada YOK) — bu
  yalnızca genel Android TV/Google TV hedefi için.

### G026-G029 — olay doğrulama, yazı okunabilirliği, reconnect ve "bu TV'yi hatırla" (2026-11-09)

Roadmap'in P3-2/P3-3 kontrol noktalarındaki dört adım (G030 — gerçek Profilo
TV kabul matrisi — hariç, o yalnızca gerçek donanımda kullanıcının kendisi
tarafından yapılabilecek bir QA kontrol listesi):

**G026 — Olay doğrulama ve sıra güvenliği:**
- `CueEventCodec.decode` artık ham satır uzunluğunu (`MAX_LINE_LENGTH`),
  çözülmüş metin alanlarının uzunluğunu (`MAX_TEXT_LENGTH`) ve şemayı (tam
  5 alan — eski 3-alanlı G025-öncesi biçim artık BİLİNÇLİ olarak
  reddedilir, breaking change) doğrular.
- Her `CueEvent` artık bir `sequence` (monoton artan) + `epochId`
  (bağlantı/oturum kimliği, `TvEventClient.connect()` her çağrıldığında
  yeniden üretilir) taşır — yeni `CueSequenceGuard` sınıfı, aynı epoch
  içinde tekrar oynatılan (replay) veya sırası bozulmuş (reorder)
  olayları sessizce reddeder; her `TvEventServer` bağlantısı kendi
  bağımsız `CueSequenceGuard` örneğini kullanır.
- "Metin HTML çalıştırmaz" kabul kriteri zaten baştan beri doğruydu —
  `app-tv` bu metni her zaman düz Compose `Text` olarak çizdi, hiçbir
  zaman bir WebView/HTML motoruna vermedi (bkz. `CueEvent.kt` dokümantasyonu).

**G027 — TV yazı yerleşimi ve okunabilirlik:**
- Yeni `core-model` sınıfı `CaptionDisplaySettings` (saf Kotlin): font
  ölçeği, dikey konum (üst/alt), arka plan opaklığı, kenar (safe-area)
  boşluğu — her biri kumandayla (D-pad) adım adım değiştirilebilir, her
  zaman güvenli bir aralığa kelepçelenir (clamp).
- `app-tv`'de "Yazı ayarları" düğmesiyle açılan bir panel (+/- düğmeleri,
  konum değiştir, varsayılanlara dön); seçimler `CaptionDisplayPrefs`
  (`SharedPreferences`) ile kalıcı olarak saklanır, TV yeniden
  başlatıldığında KAYBOLMAZ.

**G028 — Reconnect, snapshot ve süre sonu:**
- `TvEventServer` artık TEK bir "birincil" bağlantı kavramı uygular —
  eşleştirme+yetenek el sıkışmasını tamamlayan her yeni bağlantı, önceki
  birincil bağlantıyı (varsa, ör. ağı kaybedip yeniden bağlanan AYNI
  telefonun eski yarı-ölü soketi) ZORLA kapatır. Eski MVP davranışı
  (sınırsız eşzamanlı bağlantı) kaldırıldı.
- "Eski oturumun olayları yeni oturuma sızmaz" gereksinimi G026'nın
  epoch mekanizmasıyla karşılanır — her yeni bağlantı yeni bir epoch açar.

**G029 — Eşleştirmeyi hatırlama ve iptal:**
- Yeni `core-pairing` sınıfları `TrustedTv`/`TrustedTvCodec`/
  `TrustedTvRegistry` (saf Kotlin) — hatırlama/unutma/tekilleştirme
  (fingerprint'e göre) mantığı.
- `app-mobile`'da `TrustedTvStore` (`SharedPreferences`) ile kalıcı
  depolama. QR tarama ya da NSD ile bulunan bir TV'ye bağlanıldığında
  ("Yeni TV'leri hatırla" anahtarı açıkken, varsayılan açık) o TV
  otomatik hatırlanır. "TV'ye Yansıt" diyaloğunda "Hatırlanan TV'ler"
  listesi + her kayıt için "Unut" düğmesi var. Hatırlanan bir TV'ye
  yeniden bağlanırken host/port otomatik doldurulur VE kayıtlı
  fingerprint pinlenerek kullanılır — kullanıcı yalnızca TV ekranındaki
  GÜNCEL (tek kullanımlık) eşleştirme kodunu girer.

**Gerçekten doğrulanan (unit test, bu kum havuzunda çalıştırıldı):**
`CueEventCodec`in boyut/şema/sequence/epoch doğrulaması ve eski 3-alanlı
biçimi reddetmesi, `CueSequenceGuard`'ın replay/reorder reddi ve epoch
geçişinde sıfırlama, `TvEventServer`'ın yeni bağlantı geldiğinde eskisini
GERÇEKTEN kapattığı (gerçek loopback TLS soketi üzerinde), tüm bu
değişikliklerden sonra önceki `TvEventServerClientTest`/
`TvEventPairingHandshakeTest`/`SocketTvReceiverAdapterTest` testlerinin
HÂLÂ geçtiği, `CaptionDisplaySettings`'in her alanının kelepçelenmesi
(ondalık birikim hatası olmadan), `TrustedTvCodec`/`TrustedTvRegistry`'nin
hatırlama/unutma/tekilleştirme/case-insensitive fingerprint eşleşmesi.
core-transport 31→38 test, core-pairing 22→34 test, core-model'e 8 yeni
test eklendi — repo toplamı 66→98 test. (2026-11-09, G029 kimlik
kalıcılığı eklemesiyle core-transport'a `TvTlsIdentityTest` — 2 yeni test —
eklendi: 38→40, repo toplamı 98→100; bkz. aşağıdaki "G029 kimlik
kalıcılığı" bölümü.)

**Doğrulanmadı / bilinçli MVP sınırları (dürüstlük ilkesi):**
- G027'nin ayar paneli gerçek bir TV kumandasıyla (D-pad fokus/gezinme)
  HİÇ denenmedi — yalnızca `CaptionDisplaySettings`'in kelepçeleme
  mantığı birim test edildi.
- G028'in "tek birincil bağlantı" davranışı GERÇEK bir ağ kesintisi
  senaryosunda (Wi-Fi kopması, 10 saniyelik kayıp, uyku/uyanma) HİÇ
  denenmedi — yalnızca "ikinci bir bağlantı geldiğinde sunucunun
  davranışı" loopback soketle doğrulandı. Otomatik yeniden bağlanma
  (bağlantı koptuğunda istemcinin kendiliğinden yeniden denemesi) HÂLÂ
  YOK — kullanıcı manuel olarak yeniden bağlanmalı.
- TTL/zaman-aşımına dayalı otomatik "temizle" (clear) mesajı protokole
  HENÜZ eklenmedi (`TvCapabilities.supportsClear` hâlâ `false`) — TV,
  son aldığı cue'yu yenisi gelene kadar ekranda tutar.
- G030 (Profilo TV ilk kabul matrisi) bu kum havuzunda YAPILAMAZ — gerçek
  Xbox+Profilo donanımında 10 güvenli eşleşme + 60 dakika canlı kamera→TV
  + kopmada temizleme kaydı gerektirir, kullanıcının kendisi tarafından
  yapılmalıdır.

### G029 kimlik kalıcılığı — TV TLS kimliği artık AndroidKeyStore'da (2026-11-09)

Yukarıdaki "G029'un güvenlik değeri KISITLI" notu artık GEÇERSİZ — şöyle
çözüldü:

- `core-transport`'taki `TvTlsIdentity`'ye yeni bir fabrika eklendi:
  `fromKeyManagers(certificate, keyManagers, fingerprintSha256)` — bu,
  ÖNCEDEN başka bir yerde üretilmiş/yüklenmiş bir sertifika+anahtar
  çiftini SARAR (kendisi hiçbir şey üretmez), böylece `core-transport`
  Android'e bağımlı hale GELMEZ (AGENTS.md madde 5).
- `app-tv`'de yeni `PersistentTvIdentity` sınıfı (`PersistentTvIdentity.kt`)
  — gerçek `android.security.keystore.KeyGenParameterSpec` +
  `AndroidKeyStore` ile 2048-bit'lik bir RSA anahtar çifti ve ona eklenen
  kendi-imzalı bir X.509 sertifika üretir/yükler (`loadOrCreate()`). Özel
  anahtar `AndroidKeyStore`'un kendisinden hiçbir zaman dışarı ÇIKMAZ.
- `MainActivity`, TLS kimliğini artık `TvTlsIdentity.generate()` (geçici)
  yerine `PersistentTvIdentity.loadOrCreate()` ile alır — bu sayede TV'nin
  fingerprint'i **uygulama/cihaz yeniden başlatılsa bile DEĞİŞMEZ** ve
  G029'un ("bu TV'yi hatırla") beklediği "sabit/kalıcı kimlik" koşulu
  GERÇEKTEN karşılanır.
- `PersistentTvIdentity.loadOrCreate()`, HERHANGİ bir istisnada (ör. bu
  cihazda/OEM'de beklenmeyen bir AndroidKeyStore davranışı) sessizce
  `TvTlsIdentity.generate()`'e (geçici kimlik) düşer — TV hiçbir zaman bu
  yüzden çökmez, yalnızca o oturumda hatırlama kalıcılığı kaybolur.

**Gerçekten doğrulanan:** Hiçbir şey yeni bu değişiklikte — `core-transport`
modülünde Android SDK'sı OLMADIĞI için `PersistentTvIdentity`'nin kendisi
(tamamen `app-tv`'de, Android'e özgü) bu kum havuzunda ne derlenebildi ne
de birim test edilebildi; yalnızca `TvTlsIdentity.fromKeyManagers`
fabrikasının (saf Kotlin, core-transport) davranışı mevcut
`TvTlsIdentityTest`-benzeri testlerle dolaylı olarak kapsanır (gerçek
sertifika/keyManager nesneleri verildiğinde doğru alanlarla bir
`TvTlsIdentity` döndürdüğü).

**Doğrulanmadı / dürüstlük ilkesi — gerçek, açık kalan sınır:**
`PersistentTvIdentity`'nin GERÇEK bir Android cihazda/TV'de — gerçek
`AndroidKeyStore` sağlayıcısıyla, gerçek bir TLS el sıkışmasında — çalıştığı
HENÜZ denenmedi (bu kum havuzunda Android SDK/cihaz yok). Bu, Android'in
resmi/belgeli davranışına dayanan makul bir tasarımdır ama AndroidKeyStore
destekli anahtarlarla özel TLS sunucuları kurmanın bazı OEM/Android sürüm
kombinasyonlarında bilinen uyumluluk tuhaflıkları vardır — bu yüzden G030
(gerçek donanım QA'sı) sırasında özellikle izlenmesi gereken bir nokta
olarak işaretlendi. Ayrıca anahtarın 10 yıllık geçerlilik süresi dolduğunda
ne olacağı (`PersistentTvIdentity` şu an bunu ELE ALMIYOR) ayrı, açık bir
MVP sınırıdır.

### GAMLETSOL marka kimliği — gerçek logo/simge (2026-11-09)

Her iki uygulama da (`app-mobile`, `app-tv`) daha önce Android'in genel
yer tutucu simgesini (`@android:drawable/sym_def_app_icon` — sistemin
"uygulamanın gerçek bir simgesi yok" durumunda gösterdiği jenerik Android
robotu) kullanıyordu; `app-tv`'nin TV ana ekranı banner'ı da düz renkli bir
dikdörtgendi. Kullanıcının isteği üzerine gerçek bir marka kimliği
tasarlandı ve uygulandı:

- **Logo konsepti:** Bir oyun kumandası siluetinin (tutamaçlar + D-pad +
  aksiyon düğmeleri) ortasında, kumandanın "ekranı" gibi duran küçük, ortalı
  bir konuşma/altyazı balonu — GAMLETSOL'un "oyun ekranındaki yazıyı okuyup
  çevirir" işlevini tek bakışta anlatması hedeflendi. Mor→turkuaz gradyan
  zemin, hafif 3D/parlak plastik gövde (gradyan + ince üst parlama + zemin
  gölgesi).
- **Uygulama simgesi:** `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive
  icon) + `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_background.png`
  / `ic_launcher_foreground.png` — her iki uygulamada da aynı tasarım.
  minSdk 26 olduğu için eski (legacy, API<26) PNG simgeye gerek yok.
  `AndroidManifest.xml`'deki `android:icon`/`android:roundIcon` artık
  `@mipmap/ic_launcher`'a işaret ediyor.
- **TV banner'ı:** `app-tv/src/main/res/drawable-xhdpi/gamletsol_tv_banner.png`
  (640×360px, 320×180dp'nin 2x'i) — logo + "GAMLETSOL" / "Game Translate
  Console" yazısı, aynı gradyan zemin üzerinde. Eski düz renkli
  `tv_probe_banner.xml` placeholder'ı silindi.
- **Görünür isimler:** `app-mobile` zaten `android:label="GAMLETSOL"`
  kullanıyordu (değişmedi). `app-tv`'nin adı `strings.xml`'de
  "GAMLETSOL TV Probe"'tan "GAMLETSOL TV"ye kısaltıldı — iki uygulama aynı
  marka adını taşıyor, TV'ninki yalnızca hangi cihaza kurulduğunu
  ayırt etmek için "TV" ekliyor.
- Logo, raster (PNG) olarak üretildi — gradyan + yumuşak gölge/parlama
  efektleri Android'in vektör çizim (`VectorDrawable`) formatında
  desteklenmediği için (filtre/blur yok), SVG'den `rsvg-convert` ile her
  yoğunluk için doğrudan rasterize edildi (küçültülmüş bir PNG'den
  büyütme değil).

**Doğrulanmadı (dürüstlük ilkesi):** Simgenin/banner'ın gerçek bir
telefon/TV başlatıcısında (launcher) tam olarak nasıl göründüğü bu kum
havuzunda denenmedi (Android SDK/cihaz yok) — yalnızca CI'daki gerçek
`assembleDebug` derlemesiyle (kaynakların derlendiği, APK'ya paketlendiği)
doğrulandı. Google Play Store'a özgü ayrı bir "yüksek çözünürlüklü simge"
(512×512, feature graphic vb.) gerekmiyor çünkü uygulama şu an Play
Store'da değil (bkz. "Kurulum ve Kullanım Kılavuzu" — APK'lar doğrudan
sideload ile kuruluyor).

### G031-G033 — Oyun profili/terim sözlüğü ve Türkçe TTS iyileştirmeleri (2026-11-09)

Yol haritasının P4 fazından ("Kalite, TTS ve uzun oturum") üç kapı:

- **G031 — Oyun profili ve terim sözlüğü.** `core-model`'e saf Kotlin
  `GameProfile`/`TermOverride`/`GameProfileStore` eklendi (bu dosyadaki ESKİ,
  hiçbir yerde kullanılmayan basit `GameProfile` taslağı — `Session.kt`
  içindeydi — bu gerçek sürümle değiştirildi). `GameProfileStore`: bilinmeyen
  bir `profileId`'yi silmek sessizce no-op'tur (hata fırlatmaz), ve eski bir
  `schemaVersion` ile gelen bir profil otomatik olarak güncel şemaya taşınır
  — ikisi de `GameProfileStoreTest`'te doğrulandı. `SampleGameProfiles` üç
  türden (RPG/FPS/STRATEGY) örnek profil tanımlıyor; her biri buton adları
  (RT/LB/X), istatistik kısaltmaları (HP/Armor/XP/Mana) ve örnek özel isimler
  (Geralt/Ciri) içeriyor — kabul kriterindeki "üç oyun türü için terim ayarı"
  budur. Bunları gerçek çeviriye bağlayan parça `core-pipeline`'daki YENİ
  `TermPreservingTranslator` — bir `Translator` dekoratörü: metindeki
  sayıları (HER ZAMAN, profilsiz de) ve etkin profildeki terimleri ML Kit'e
  göndermeden önce yer tutucuyla maskeler, çeviri sonrası orijinal metinle
  (veya `displayTerm` belirtilmişse onunla) geri yükler — kelime sınırı
  (`\b`) kullanılır ki örn. "X" terimi "Explosion" gibi bir kelimenin içine
  yanlışlıkla eşleşmesin. `app-mobile`'da `CaptionViewModel`, ML Kit
  çeviricisini artık doğrudan değil bu dekoratörle sarmalanmış olarak
  kullanıyor (`activeGameProfile`/`selectGameProfile` ile seçilebilir, ama bu
  turda henüz bir profil-seçim EKRANI/UI'si eklenmedi — yalnızca
  ViewModel API'si hazır, bir sonraki round'a kalan bir UI eksiği).
- **G032 — Türkçe TTS motoru ve ses seçimi.** `AndroidTtsSpeechOutput`'a
  `turkishVoiceStatus()` eklendi: kurulu bir Türkçe ses var mı
  (`TextToSpeech.isLanguageAvailable`) ve varsa ağ bağlantısı gerektiriyor mu
  (`Voice.isNetworkConnectionRequired`) sorgulanabiliyor. `CaptionViewModel`
  bunu kısa bir yoklama ile okuyup `turkishVoiceStatusMessage` olarak
  dışa veriyor; `CaptionScreen`'deki durum kutusunda (varsa) ikinci, küçük
  bir uyarı satırı olarak gösteriliyor. Kabul kriterinin ikinci yarısı
  ("ses yoksa altyazı çalışmaya devam eder") zaten `CaptionPipeline`'ın
  tasarımından geliyordu — `speechOutput` tamamen ayrı ve isteğe bağlı bir
  adım, `sinks.forEach { it.show(cue) }` her durumda çalışır — bu turda
  yalnızca kullanıcıya GÖRÜNÜR hale getirildi, pipeline mantığı
  değişmedi.
- **G033 — TTS güncellik ve ses odağı.** `speak()` artık her çağrıda bir
  `AudioFocusRequest` (geçici, `AUDIOFOCUS_GAIN_TRANSIENT`) istiyor —
  reddedilirse (örn. bir telefon araması odağı tutuyorsa) hiç konuşmaya
  başlamıyor; konuşma bitince/hata olunca (`UtteranceProgressListener`) odak
  otomatik bırakılıyor; `stop()` de odağı bırakıyor. "Eski kuyruk
  birikmesin" kriteri zaten mevcut `TextToSpeech.QUEUE_FLUSH` kullanımıyla
  (ve `CaptionPipeline`'ın yalnızca GERÇEKTEN yeni cue'lar için `speak()`
  çağırmasıyla) karşılanıyordu — bu turda değişmedi, sadece KDoc'ta açıkça
  belgelendi.

**Doğrulanmadı (dürüstlük ilkesi):** `GameProfile`/`GameProfileStore`/
`TermPreservingTranslator` saf Kotlin'dir ve bu kum havuzunda GERÇEKTEN
test edildi (`TermPreservingTranslatorTest`, yukarıdaki test sayısına
dahil) — ama bu testler `FakeTranslator` ile, GERÇEK ML Kit modeliyle
DEĞİL. Yer tutucu jetonun ("§0§" gibi) gerçek ML Kit çevirisi sırasında
bozulmadan (boşluk eklenmeden, büyük/küçük harf değişmeden) hayatta kalıp
kalmadığı hiç denenmedi. `AndroidTtsSpeechOutput`'taki ses durumu/ses odağı
kodu Android'e özgüdür, bu modülde hiç testi yok (önceki sürümünde de
yoktu) — yalnızca CI'nın gerçek `assembleDebug`+lint derlemesiyle
doğrulandı; gerçek bir cihazda, gerçek bir Türkçe sesle veya gerçek bir
kulaklık/Bluetooth çıkışıyla hiç denenmedi. Bir oyun profili SEÇME EKRANI
henüz yok (yalnızca ViewModel API'si var) ve kalıcı profil depolama
(DataStore/Room) henüz eklenmedi — `GameProfileStore` yalnız bellek-içi.

### G034-G036 — kalite veri kümesi (bloke), gizlilik denetimi ve performans uyarlaması (2026-11-09)

Yol haritasının P4 fazının kalanı:

- **G034 — OCR ve çeviri kalite veri kümesi: BİLEREK YAPILMADI.** Kabul
  kriteri "izinli gold metinler ve görülmemiş test sahneleri" ve
  doğrulaması "en az 100 cue için kör anlam değerlendirmesi" gerçek,
  lisanslı oyun görüntüsü/metni gerektiriyor — bu kum havuzunda ne gerçek
  oyun ekran görüntüsü var ne de bunları üretecek bir cihaz/oyun. Rastgele
  İngilizce metin üretip "gold veri kümesi" diye sunmak dürüstlük ilkesine
  aykırı olurdu (gerçek OCR hatalarını/gerçek oyun yazı tipi/kontrastını
  temsil etmez). Bu görev sizin elinizde kalıyor — gerçek oyun
  sahnelerinden ekran görüntüsü/kayıt toplamanız gerekiyor.
- **G035 — Gizlilik ve ağ akışı denetimi (statik kod denetimi).** Tüm
  kaynak kodu (`app-mobile`, `app-tv`, `core-*`, `feature-*`) üzerinde
  `grep` ile denetim yapıldı:
  - **Ağ:** Kod tabanındaki TEK ağ çağrıları: (1) `core-transport`'taki
    `SSLServerSocket`/`SSLSocket` — bunlar yalnız LAN üzerinde, kullanıcının
    açıkça başlattığı "TV'ye Yansıt" bağlantısı (internete çıkmaz); (2)
    `MlKitTranslator.ensureModelDownloaded()` — ML Kit'in kendi çeviri
    MODELİNİ indirir (bir kere, Wi-Fi'da), altyazı/OCR İÇERİĞİNİ göndermez;
    (3) `NsdManager` (G025) — yalnız yerel ağda mDNS, internete çıkmaz.
    Başka hiçbir `Socket`/`HttpURLConnection`/`OkHttp`/`Retrofit` kullanımı
    YOK.
  - **Log/içerik sızıntısı:** Kod tabanındaki TÜM `Log.*` çağrıları
    tek tek incelendi — hiçbiri OCR'dan okunan veya çevrilmiş altyazı
    metnini loglamıyor (yalnız NSD/TV-giriş/kimlik OLAY'ları loglanıyor,
    örn. "NSD taraması başladı", bağlantı durumları — gerçek cue içeriği
    asla).
  - **Cihaz depolaması:** `SharedPreferences` kullanan 5 dosya incelendi
    (`OnboardingPrefs`, `TrustedTvStore`, `CaptionDisplayPrefs` +
    kullanım yerleri) — hepsi yalnız AYAR (onboarding bayrağı, güvenilir
    TV listesi+fingerprint, yazı görünüm ayarları) saklıyor; hiçbiri
    OCR/çeviri İÇERİĞİNİ kalıcı olarak saklamıyor.
  - **Sonuç:** Kabul kriterinin "görüntü/ses upload'u ve içerik log'u yok"
    kısmı statik koddan DOĞRULANDI. "SDK teknik veri akışı beyana dahil"
    kısmı henüz yapılmadı (bir Play Store Data Safety beyanı yok çünkü
    uygulama Play Store'da değil — bkz. "GAMLETSOL marka kimliği"
    bölümündeki not).
  - **Doğrulanmadı (dürüstlük ilkesi):** Bu statik kod denetimidir —
    kabul kriterinin "kontrollü ağ trafiği" doğrulaması (örn. mitmproxy
    ile gerçek cihazdan geçen trafiği yakalayıp inceleme) ve gerçek crash
    kayıtlarının (Play Console/Firebase Crashlytics YOK, hiç
    entegre edilmedi) incelenmesi hiç yapılmadı — ikisi de gerçek
    cihaz/gerçek kullanım gerektirir.
- **G036 — Termal, pil ve performans uyarlaması (kısmi).** Kabul
  kriterinin ÇEKİRDEK mantığı — "yük artınca OCR frekansı kontrollü
  düşer" — yeni `core-pipeline` sınıfı `AdaptiveFrameThrottle` ile
  uygulandı: son işlenen karelerin (pencereli) ortalama OCR+normalizasyon
  süresi sürekli olarak hedef bütçenin üstündeyse kare atlama oranını
  kademeli artırır (`skipFactor`), cihaz toparlanınca kademeli azaltır
  (histerezis — tek bir yavaş kare anında atlamaya geçirmez).
  `CaptionPipeline.onFrame` artık her kareden önce `shouldProcess()`
  sorup `false` dönerse kareyi OCR'a HİÇ vermeden serbest bırakıyor.
  Saf Kotlin/JVM'dir, 6 yeni birim testi + 1 `CaptionPipeline`
  entegrasyon testi ile doğrulandı (sürekli yavaş kareler → atlama oranı
  artıyor; sürekli hızlı kareler → geri düşüyor; üst sınır aşılmıyor).

  **Doğrulanmadı (dürüstlük ilkesi):** `targetFrameBudgetMillis`
  varsayımsal bir başlangıç değeridir (150ms), gerçek bir orta sınıf
  telefonda ÖLÇÜLMEDİ. Kabul kriterinin asıl istediği "60 dakika crash
  yok; 120 dakika pil/ısı/RAM raporu" ve "gecikme p50/p95" ölçümü gerçek
  bir orta sınıf telefonda uzun bir oturum gerektirir — bu kum havuzunda
  hiç yapılamadı, hâlâ sizin elinizde. `AdaptiveFrameThrottle` yalnızca
  "kontrollü düşüş" DAVRANIŞINI sağlıyor; gerçek eşik değerleri gerçek
  ölçümle ayarlanmalı.

Bu üçü de (G034-G036) `docs/roadmap/tasks/todo.md`'de BİLEREK
işaretlenmedi — P4-2 kontrol noktası notu gerçek cihaz kanıtı istiyor ve
G034 hiç başlanmadı.

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
├─ app-tv/             # Android TV / Google TV probe uygulaması — G001-G006
│                      # (P0): TvInputManager ile HDMI girişi bul, TvView ile
│                      # tune ol, sabit Türkçe test yazısı bindir
├─ core-model/         # Saf Kotlin — Cue, FrameRef, TextRegion, Session,
│                      # TvInputSummary/selectPreferredHdmiPassthroughInput...
├─ core-pairing/       # Saf Kotlin — PairingToken/PairingTokenGenerator/
│                      # PairingRegistry (tek kullanımlık eşleştirme kodu,
│                      # G023) + PairingQrPayload/QrMatrix/QrMatrixEncoder
│                      # (QR kodlama/kod çözme, zxing-core ile)
├─ core-pipeline/      # Saf Kotlin — FrameSource/TextRecognizer/Translator/
│                      # SubtitleSink/SpeechOutput arayüzleri + CaptionPipeline
├─ core-transport/     # Saf Kotlin — CueEvent/CueEventCodec + TvEventServer/
│                      # TvEventClient (telefon↔TV TCP taşıma) + TvCapabilities/
│                      # TvReceiverAdapter/SocketTvReceiverAdapter (G022 yetenek
│                      # sözleşmesi) — P3/G022-G030 MVP
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
önerdiği adlarla hizalandı: `data-local` (Room/DataStore — kalıcı ayar/sözlük/
trusted-devices, P2-P3), `receiver-tizen`, `receiver-web`, `source-playstation`,
`source-xbox` (X1-X4, koşullu), `backend/*` (P6-P7). `app-tv`, `core-transport`
ve `core-pairing` artık eklendi (yukarıya bakın) — birlikte G022/G023/G024/
G025'i tamamlıyor (TV yetenek sözleşmesi + tek kullanımlık eşleştirme kodu +
TLS/fingerprint pinning + NSD/mDNS otomatik keşif); `feature-tv-connect`
adıyla ayrı bir modül açılmadı, NSD mantığı doğrudan `core-transport`/
`app-tv`/`app-mobile`'a eklendi.

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
- Konsol bağlantısı, Akıllı Ekran, dublaj paketleri — bkz. yol haritası
  P3-P6. (TV'ye yansıtmanın İLK/MVP sürümü artık var — şifreli kanal +
  QR ile MITM korumalı eşleştirme dahil, bkz. yukarıdaki `core-transport`
  bölümleri — ama gerçek donanım/gerçek ağ testi hâlâ yok.)
- Kalıcı ayarlar/oyun profilleri (`data-local`/Room).

## Doğrulama durumu (dürüstlük ilkesi)

- [x] `core-model`, `core-pipeline`, `core-pairing`, `core-transport`: bu
      kum havuzunda **gerçekten derlendi ve test edildi** (`./gradlew
      :core-model:test :core-pipeline:test :core-pairing:test
      :core-transport:test` — 118 birim testi (2026-11-09, G036 eklemesiyle
      111'den 118'e — 6 yeni `AdaptiveFrameThrottleTest` + 1 yeni
      `CaptionPipelineTest` entegrasyon testi; bkz. aşağıdaki "G035-G036"
      bölümü. Önceki artış: G031 eklemesiyle 100'den 111'e — 6 yeni
      `GameProfileStoreTest` + 5 yeni `TermPreservingTranslatorTest`; bkz.
      aşağıdaki "G031-G033" bölümü. Daha önceki artış: G026-G029 eklemeleriyle 66'dan 98'e, G029 kimlik
      kalıcılığı eklemesiyle 98'den 100'e; bkz. yukarıdaki "G026-G029" ve
      "G029 kimlik kalıcılığı" bölümleri — 8 CaptionDisplaySettings + 6
      CueSequenceGuard + 1 TvEventServerReconnect + 5 yeni CueEventCodec
      doğrulama testi + 4 TrustedTvCodec + 8 TrustedTvRegistry + 2
      TvTlsIdentity.fromKeyManagers eklendi). Önceki sayım: 2 Cue +
      7 CaptionPipeline + 4 TvInputSelection + 3 CueEventCodec + 4
      TvEventServer/Client + 9 PairingRegistry/Token + 4 QrMatrixEncoder +
      9 PairingQrPayload (G024 v2 dahil) + 8 TvEvent+Pairing el sıkışma
      (G024'ün 3 TLS/fingerprint testi dahil) + 6 TvCapabilities + 3
      SocketTvReceiverAdapter (G022) + 7 TvNsdAttributes (G025), hepsi
      geçti; soket kullanan testler gerçek 127.0.0.1 loopback TCP/TLS
      bağlantıları üzerinden çalışır, mock değil — bu kum havuzunda art
      arda birden çok kez `--rerun-tasks` ile de doğrulandı. Bu
      testler yazılırken/koşulurken İKİ gerçek hata
      bulundu ve düzeltildi (dürüstlük ilkesi için kayıt): (1) istemcinin
      `connect()`'i sunucunun handshake'i GERÇEKTEN doğruladığını
      beklemiyordu, yalnızca yazmanın başarılı olmasına bakıyordu — artık
      sunucu bir `PAIR_OK`/`PAIR_REJECTED` yanıtı gönderiyor ve istemci bunu
      bekliyor; (2) `TvEventServer`'da gerçek bir yarış koşulu vardı —
      istemcinin `connect()`'i, sunucunun `accept()`'i tamamlayıp soketi
      kayıt altına almasını beklemediği için, `stop()` tam o aralıkta
      çağrılırsa bağlantı hiç kapatılmadan sonsuza kadar açık
      kalabiliyordu (izole bir Java soket scriptiyle doğrulandı); artık
      kabul ve durdurma aynı kilit altında atomik. JDK 21 ile, CI'da JDK 17
      ile de **gerçekten tekrar doğrulandı**, aşağıya bakın).
- [x] `feature-capture`, `app-mobile`, `app-tv`: **artık GERÇEK CI'da
      derlendi** (`.github/workflows/android.yml`,
      [run #42567524803](https://github.com/FEDERALTURK/gamletsol/actions/runs/42567524803) —
      `success`): gerçek Android SDK ile derleme + lint (0 hata) +
      `assembleDebug` başarılı, iki debug APK üretildi
      (`app-mobile-debug.apk`, `app-tv-debug.apk`) ve CI artifact'leri
      olarak indirildi/kullanıcıya teslim edildi. **Bu kum havuzunda hâlâ
      yerel olarak derlenmedi** (Android SDK yok) — ama artık bu bir
      varsayım değil, GitHub'ın gerçek runner'ında gerçekten çalıştı.
      Yol boyunca 3 gerçek hata bulunup düzeltildi (kayıt için, dürüstlük
      ilkesi): (1) `sdkmanager` ubuntu-latest'te PATH'te değilmiş →
      `android-actions/setup-android@v3` eklendi; (2) o action'ın
      varsayılan paket listesi artık var olmayan eski `tools` paketini
      istiyordu → yalnızca `platform-tools` istendi; (3)
      `MlKitTextRecognizer.kt`'deki `ImageProxy.image` kullanımı
      `@ExperimentalGetImage` opt-in'i olmadan lint hatası veriyordu →
      eklendi. (4, G024'te bulundu/düzeltildi:) Bouncy Castle'ın üç modülü
      (`bcprov`/`bcpkix`/`bcutil-jdk18on`) aynı
      `META-INF/versions/9/OSGI-INF/MANIFEST.MF` yolunu taşıdığı için
      `assembleDebug` `DuplicateRelativeFileException` ile patladı → her iki
      app modülünün `packaging.resources.excludes` listesine bu yol eklendi
      (çalışma zamanı davranışını etkilemeyen, salt OSGi meta verisi).
      Ayrıca (2026-11-09) CI'nın `-reports` artifact'leri indirilip
      incelenerek lint UYARILARI (hata değil, build zaten başarılıydı)
      temizlendi — bkz. yukarıdaki "Lint uyarılarının temizlenmesi"
      bölümü; `app-mobile` 44→39, `app-tv` 3→0 uyarı (kalan 39'u bilerek
      dokunulmayan kütüphane-sürümü önerileri).
- [ ] Gerçek bir Android cihazda kamera + OCR + çeviri + TTS uçtan uca hiç
      denenmedi — CI yalnızca derleme/lint/APK üretimini doğrular, çalışma
      zamanı davranışını (kamera izni akışı, gerçek OCR sonucu, TTS sesi)
      DOĞRULAMAZ.
- [ ] Herhangi bir Android TV / Google TV cihazında HDMI passthrough
      üzerine yazı çizme (**G001-G006, P0** — güncel yol haritasının *tek*
      önceliği) için `app-tv` probe APK'si artık gerçekten derlendi ve
      kullanıcıya teslim edildi, ama **gerçek donanımda hiç
      çalıştırılmadı**. Bu kapı geçilmeden TV üstü çeviri özelliği
      "çalışıyor" sayılmaz; sonuçlar `docs/compatibility/hardware.md`'ye
      işlenecek.
- [ ] Telefon↔TV bağlantısı (P3/G022-G025, `core-transport` + `core-pairing`
      + "TV'ye Yansıt" menüsü): kod yazıldı, gerçek loopback soket/TLS
      testleriyle doğrulandı (eşleştirme kodu + yetenek sözleşmesi + QR
      kodlama/kod çözme + TLS/fingerprint pinning + NSD TXT kayıt kodlama/
      kod çözme dahil), ama **gerçek bir telefon ile gerçek bir TV
      arasında, gerçek bir Wi-Fi ağında hiç denenmedi** — QR'ın gerçek bir
      kamerayla gerçek bir TV ekranından okunup okunamadığı, TLS el
      sıkışmasının gerçek ağ koşullarında sorunsuz çalışıp çalışmadığı, ve
      gerçek `NsdManager` reklamının/taramasının gerçek bir router'da
      çalışıp çalışmadığı da dahil (bu üçü de hiçbir zaman gerçek Android
      donanımında/ağında çalıştırılmadı). Kanal artık şifreli, QR/NSD
      yolunda fingerprint pinlenir, ve TV'ler artık otomatik olarak
      listelenebilir, TLS kimliği artık `AndroidKeyStore`'da KALICIDIR ve
      "bu TV'yi hatırla" gerçek güvenlik değeri taşır (G022-G029 tamam) —
      ama bu kalıcılığın GERÇEK bir Android cihazda çalıştığı HENÜZ
      denenmedi (bkz. yukarıdaki "G029 kimlik kalıcılığı" bölümü). Bkz.
      yukarıdaki `core-pairing`/`core-transport`/"Kamerayla QR eşleştirme"/
      "TLS + sertifika fingerprint pinning"/"NSD/mDNS otomatik keşif"/"G029
      kimlik kalıcılığı" bölümleri.

## Geliştirme

```bash
./gradlew :core-model:test :core-pipeline:test :core-pairing:test :core-transport:test  # platformdan bağımsız birim testleri
./gradlew :app-mobile:assembleDebug              # telefon debug APK (Android SDK gerektirir)
./gradlew :app-tv:assembleDebug                  # TV probe debug APK (Android SDK gerektirir)
```

Legacy Flutter projesi için bkz.
[`legacy-flutter-mic-stt/README.md`](legacy-flutter-mic-stt/README.md).
