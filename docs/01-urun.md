# 01 · Ürün

## Tek cümle
Damla, takip edilen her varlığın üç farklı zaman aralığındaki yönünü tek bir simgeyle (damla + iki halka) gösterir; kullanıcı ana ekrana bakıp rakam okumadan "ne oluyor" sorusunun cevabını alır.

## Gösterge dili
Bir gösterge = **damla gövdesi** + altına kavisle sarılan **üst halka** ve **alt halka**.

| Parça | Anlam | Neyi gösterir |
|---|---|---|
| Damla | En kısa aralık (ör. 15 dk) | Şekil + renk |
| Üst halka | Orta aralık (ör. 4 sa) | Yalnızca renk |
| Alt halka | Uzun aralık (ör. 1 gün) | Yalnızca renk |

Üç aralık da varlık başına kullanıcı tarafından serbestçe seçilir: 15 dk, 1 sa, 4 sa, 1 gün, 1 hafta, 1 ay, özel (dakika). Yeni eklenen varlıklar Ayarlar'daki varsayılanları alır.

| Durum | Şekil | Renk (gövde) | Anlam |
|---|---|---|---|
| UP | Sivri ucu yukarı damla | #34C759 | Net yükseliş |
| DOWN | Sivri ucu aşağı damla | #FF3B30 | Net düşüş |
| FLAT | Yuvarlak | #FFCC00 | Neredeyse hiç oynamadı |
| WAVE | Sivri ucu sağa yatay damla | #FF9500 | İndi çıktı ama net sonuç yerinde |
| CLOSED | Son şekil korunur | #AEAEB2 | Piyasa kapalı (yalnızca o zaman) |

Metin karşılıkları: Yükselişte / Düşüşte / Sabit / Dalgalı / Piyasa kapalı. Metin içindeki parlak tonlar: UP #30D158, FLAT #FFD60A, DOWN #FF453A, WAVE #FF9F0A.

## Varlıklar (v1)
- Döviz: USD/TRY, EUR/TRY, (genişletilebilir)
- Maden: gram altın, ons altın, gümüş
- Kripto: BTC, ETH ve popüler çiftler
- **Çapraz kurlar:** kullanıcı Pay ÷ Payda seçer (ör. Altın/Gümüş). Ayrı veri çekilmez; iki fiyattan hesaplanır, motor aynen uygulanır. Aynı varlık iki tarafa seçilemez.

## Logolar
Damlanın içinde, gövde merkezinde, damla dönse de dönmeyen beyaz kenarlı yuvarlak rozet (çap = gösterge genişliğinin %40'ı). Bitcoin: turuncu daire + eğik beyaz ₿. USD: ABD bayrağı. EUR: AB bayrağı. Altın: altın zemin + külçe. Gümüş: gümüş zemin + para istifi. Rozet 14 px'in altına düşerse sade sembole geçer ($, €, Ag, sade külçe). Lisans gerektiren logolar (ETH vb.) harf sembolüyle gösterilir.

## Kapsam dışı (v1)
Renk körlüğü modu, değişim şiddetine göre görünüm, widget'ta fiyat/yüzde gösterimi, iOS, yapay zeka destekli "Günün özeti" (sonraki dönem; bkz. 09).

## Marka ve hukuk
Veri sağlayıcıların kullanım koşulları ve "Harem Altın" gibi marka/logo kullanımları mağazaya çıkmadan kontrol edilecek. Bu kontrol yapılmadan üçüncü taraf markası arayüzde gösterilmez.
