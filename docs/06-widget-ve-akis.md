# 06 · Widget, Akış açılımı ve detay sahnesi

Referans: `design/Prototip.dc.html` (canlı prototip; ayarlarda boyut, açılış, stil, taraf, hız değiştirilebilir) ve `design/KlasorIcerik.dc.html` (widget ayarları).

## Platform gerçeği (önemli)
Ana ekran widget'ları serbest animasyon çalıştıramaz (RemoteViews/Glance kısıtları, sistem yenileme sınırları). Bu yüzden iki katmanlı mimari:
1. **Widget (Glance):** damlaları durağan görüntü olarak gösterir. Damla görüntüsü uygulama içinde `:engine` çıktısından üretilen bitmap/vektördür (aynı geometri, bkz. 04). Push geldiğinde yeniden çizilir.
2. **Kaplama penceresi (overlay Activity):** widget'a dokununca açılan, arka planı şeffaf, tam ekran Compose penceresi. Tüm zengin animasyonlar (Akış açılımı, detay, nabız, durum vurgusu) burada oynar. Pencere, widget'ın ekrandaki konumunu (`intent.sourceBounds`) alır ve animasyonu tam oradan başlatır; kullanıcı widget'ın "canlandığını" hisseder.

Widget içinde durum değişimi animasyonu mümkünse (ör. desteklenen sürümlerde basit geçiş) değerlendirilir; mümkün değilse anlık değişim kabul edilir. Doğrula ve bildir.

## Widget
- Varsayılan stil **Kutusuz**: damlalar doğrudan duvar kağıdının üzerinde, uygulama simgesi gibi, altında beyaz ad; okunabilirlik için hafif gölge. Alternatif: **Kutulu** (beyaz kart).
- Boyutlar: **Mini** = tek uygulama yuvası içinde 2 veya 3 küçük damla yan yana; **Geniş** = 3×1 / 2×1 yuva; **Tek** = tek varlık. Ana ekran ızgarasına tam hizalı.
- Erişilebilirlik açıklaması: "Damla: USD sabit, Altın yükselişte, Bitcoin düşüşte".

## 1. dokunuş → Akış
- Kaplama açılır, arka plan bulanıklaşır ve karartılır (siyah %30).
- Damlalar yerinden kalkıp widget köşesine yaslı sıkı bir **2×2** düzene yerleşir (yatay aralık 70 px, dikey 80 px). Aşağı inmez, widget hizasında kalır. Kutu/panel yok.
- Her damlanın altında adı ve kendi renginde durum kelimesi.
- Açılış `springOpen`, öğeler `stagger` ile.
- Tek varlık modunda da bu adım atlanmaz.
- Alternatif açılış "Klasör" (cam panel) ayarda korunur.
- **Kapat düğmesi yok:** boş alana dokunmak bir adım geri.

## 2. dokunuş → detay sahnesi
Uygulama penceresi DEĞİL; widget'ın tarafına yaslı, çerçevesiz kompakt sahne (tasarım 260×380, 0,86 ölçekle ~224×327 px). İçerik:
- Büyüyen damla (paylaşılan öğe: hücreden sahneye uçar)
- Damlanın parçalarına ince çizgilerle bağlı üç etiket: Damla / Üst halka / Alt halka + aralık + durum kelimesi
- Varlık adı, alt başlık, **tazelik satırı** ("canlı · şimdi", "canlı · 4 sn önce", yeşil nefes alan nokta)
- Serbest duran fiyat, arka planı olmayan sürüklenebilir grafik (parmakla gezilir), zaman aralığı seçici
- Üç cam daire düğme: **Alarm kur**, **Uygulamada aç**, **Paylaş**
- Varlık geçiş noktaları; yatay kaydırmayla varlık değişir
- Açılış: seçilen varlıktan dairesel açılım (clip-path 24 px → 560 px, 600 ms `easeStandard`); kapanış 440 ms `easeExit`.

## Paylaşım kartı
Detaydaki Paylaş ile: arka plan bulanıklaşır (siyah %60), 300×380 koyu kart (#0E141B, köşe 30) aşağıdan süzülür (24 px, ölçek 0.92→1, 560 ms). İçerik: Damla imzası + tarih, büyük damla ve arkasında durum renginde yumuşak ışık, varlık adı, durum + aralık, üç parçanın özeti. **Fiyat yok** (kullanıcılar tutar değil yön paylaşır). Paylaş düğmesi kartı görüntü olarak Android paylaşım sayfasına verir.

## Nabız
Prototipte her varlık rastgele aralıklarla fiyat alır; gerçekte kaplama açıkken backend'den canlı akış (WebSocket/SSE) dinlenir, kaplama kapanınca bağlantı kapanır.
