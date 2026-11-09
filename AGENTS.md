# AGENTS.md — GAMLETSOL

Bu depoda (ve koddaki yorumlarda) tekrar edilen birkaç ilke, burada tek yerde
toplandı. Bu dosya, `td2-loot-advisor` ana deposunun kendi `AGENTS.md`'sinin
yerini almaz — yalnızca GAMLETSOL'a özgü, kod yorumlarında zaten varsayılan
kurallardır.

## 1. Dürüstlük ilkesi

- Bir şey gerçek bir cihazda/gerçek veriyle denenmediyse, kod yorumunda veya
  README'de **açıkça "denenmedi/doğrulanmadı" yazılır** — "muhtemelen
  çalışır" gibi ima edilmez.
- CI'nın doğruladığı ile yerel/kum havuzunda doğrulananın sınırı net
  tutulur (bkz. kök `README.md` "Doğrulama durumu").
- Varsayımsal sayılar (maliyet, süre, performans eşikleri) her zaman
  "varsayım/tahmin, ölçüm değil" diye işaretlenir.

## 2. Gizlilik — kamera/konsol görüntüsü

- Kamera karesi veya konsol MediaProjection akışı **hiçbir aşamada** bir
  sunucuya yüklenmez.
- Yalnızca OCR ile çıkarılan **altyazı metni**, kullanıcı bulut çeviriyi
  açıkça etkinleştirdiyse bir çeviri servisine gider. Varsayılan: ML Kit
  cihaz-üstü çeviri, tamamen çevrimdışı.
- Yeni bir özellik ağa bir şey gönderiyorsa, bunun kullanıcının hangi açık
  eylemine (bir ekrana API anahtarı girip başlatmak, bir ayarı açmak vb.)
  bağlı olduğu kod yorumunda belirtilir.

## 3. Sırlar

- API anahtarı, imzalama anahtarı (`*.jks`, `*.keystore`, `key.properties`)
  koda, loglara veya versiyon kontrolüne **asla** yazılmaz (bkz. kök
  `.gitignore`). Gerekirse Android Keystore/`flutter_secure_storage`
  benzeri güvenli depo kullanılır.

## 4. Lisans farkındalığı

- AGPL-3.0 gibi copyleft lisanslı bir bileşen (ör. Chiaki-ng/XStreaming)
  kapalı kaynak ticari dağıtıma dahil edilmeden önce **açık bir lisans
  kararı** (yol haritasındaki G-kapıları) gerekir; "deneme amaçlı" diye
  sessizce eklenmez.

## 5. Modül sınırları

- `core-model` ve `core-pipeline` **saf Kotlin/JVM**'dir — Android'e özgü
  hiçbir import içermez; platforma özgü kod yalnızca `feature-*` ve
  `app-mobile` modüllerinde yaşar. Bu sayede en-ve çekirdek mantık
  (`CaptionPipeline`, kimlik takibi, TTL/fuzzy eşleşme) Android SDK'sız
  derlenip test edilebilir.
