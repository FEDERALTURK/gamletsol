# GAMLETSOL plan indeksi

9 Kasım 2026 · Game Translate Console · Tek geliştirici için planlama paketi.

## Başlangıç ve kapsam

Kullanıcının mevcut donanımı Xbox Series X/S + 75 inç Profilo Android TV. Profilo’nun tam modeli, Android build/firmware ve telefon modeli bilinmiyor. İlk hedef kamera OCR + cihazda İngilizce–Türkçe çeviri + Profilo’nun kendi işletim sistemindeki native TV alıcısıdır. İlk karar, gerçek Xbox HDMI görüntüsü üstünde sabit Türkçe yazının desteklenen yöntemle görüntülenmesidir.

Fiziksel prototip veya uygulama geliştirmesi bu hazırlık çalışmasında yapılmadı. Bu dosyalar önerilen plan ve görevlerdir. TV kapısı geçmezse telefon/TTS alternatifleri değerlendirilebilir; bunlar TV üstü çeviri vaadinin tamamlanması sayılmaz.

## Dosyalar

- [Tam ürün ve teknik yol haritası](../GAMLETSOL%20geliştirme%20yol%20haritası.md): kapsam, mimari, kaynaklar, veri/API, ekonomi, riskler ve zaman planı.
- [76 görev ve kontrol noktaları](todo.md): kararlı G001–G076 kimlikleri, önerilen modüller, bağımlılıklar, kabul ve doğrulama.
- [Düzenlenebilir mimari SVG](../GAMLETSOL_mimari.svg).
- [Mimari PNG](../GAMLETSOL_mimari.png).

Bağlantılar aynı çıktı paketinde göreli tutulmuştur; tasks klasöründen ana rapora doğru yol bir üst klasördür.

## Sıra ve efor

P0 50 + P1 75 + P2 125 + P3 100 + P4 100 + P5 100 = **550 net saat çekirdek**. P6 bulut 100 + P7 ödeme 100 eklenince **750 net saat**. %25 payla sırasıyla **687,5** ve **937,5 saat**; haftada varsayımsal 25 üretken saatte **27,5** ve **37,5 hafta**. Bu bir teslim taahhüdü değildir. İlk 50 saatten sonra gerçek hızla yeniden tahmin edilir.

Xbox doğrudan bağlantı, PS4/PS5, Akıllı Ekran, Samsung, LG, lisanslı ses paketleri ve oyun bazlı önceden hazırlanmış çeviri satır veritabanı (G073–G076, kendi editoryal içeriğimizle, rakip incelemesinden sadece kavram seviyesinde esinle) koşullu genişlemedir. Bütün ürün için 18–30 aylık planlama bandı teknik erişim, lisans, cihaz ve içerik beklemelerine bağlıdır. Sıra: eldeki Profilo + Xbox ihtiyacını kanıtla → çekirdeği güvenilir yap → beta → ölçümlü bulut/ödeme → doğrulanmış genişlemeler.

## Çalışma ilkeleri

76 görev bir uygulama backlog’udur; iş kutuları başlangıçta boştur. M işleri kodlamadan önce 1–2 saatlik alt adımlara bölünür. Önerilen klasörler mevcut dosya gibi sunulmaz. Her üç görevde kontrol noktası vardır; başarısız deneyi sonuç olarak kaydetmek, ilgili ürün kabiliyetini başarılı saymak değildir. Güncel mağaza ve tedarikçi şartları uygulama/yayın anında yeniden doğrulanır.
