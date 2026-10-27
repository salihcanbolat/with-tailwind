# 04 · Göstergenin çizimi ve animasyonları

Referans uygulama: `design/Gosterge.dc.html` (HTML/SVG). Android'de **tek bir Compose bileşeni** olarak yazılır: `@Composable fun Damla(state: AssetVisual, size: Dp, ...)`. Widget, bildirim ve saat için aynı geometriden bitmap/vektör üretilir (bkz. 06, 08).

## Koordinat sistemi
Tasarım birimi: göstergenin genişliği 76 birim. `u = sizePx / 76`. Yükseklik = `size * 92 / 76` (halkalar için alt pay). Merkez (0,0), gövde kutusu −38..38.

## Gövde
- Damla yolu (sivri ucu yukarı, UP konumunda):
  `M0,-34 C3,-24 11.4,-12.2 19.05,1 A22 22 0 1 1 -19.05,1 C-11.4,-12.2 -3,-24 0,-34 Z`
  Gövde dairesinin merkezi (0,12), yarıçap 22.
- Durumlara göre dönüşüm (merkez etrafında):
  - UP: `rotate(0)`
  - DOWN: `translateY(-6u) rotate(180°)`
  - WAVE: `translateY(+6u) rotate(90°)` (sivri uç sağa)
  - FLAT: damla `scale(0.55)` + opaklık 0; yerine daire `(0,6) r=25` görünür (scale 0.5→1, opaklık 0→1, köken %50/%58)
- Logo rozetinin konumu (gövde merkezi, birim): UP (0,12), DOWN (0,−18), WAVE (−12,6), FLAT (0,6).

## Halkalar
- Üst halka: `M-22.9,32.8 A40 40 0 0 0 22.9,32.8`
- Alt halka: `M-24,41.6 A48 48 0 0 0 24,41.6`
- Çizgi kalınlığı 5,5 birim, uçlar yuvarlak, dolgu yok. Renk = o parçanın durum rengi. Gri yalnızca CLOSED.

## Rozet (logo)
Çap = `round(size * 0.40)`, beyaz kenar `max(0.6, size*0.014)` px, döndürülmez. <14 px'te sade sembole geçer (bkz. 01).

## Malzeme (durgun hal)
Gövdenin sol üstünde yumuşak yansıma: merkezden (−12u, −15u) konumunda 8u×12u elips, beyaz, opaklık 0,26, bulanıklık `max(0.4, 1.1u)`, −24° eğik. **Döndürülmeyen katmanda** çizilir; damla dönse de ışık hep üstten gelir.

## Animasyon katmanları
| Katman | Tetik | Ne olur | Süre / eğri |
|---|---|---|---|
| Şekil geçişi | Damla durumu değişti | Gövde yeni dönüşüme döner, logo yeni merkeze kayar | 700 ms, `cubic-bezier(0.25,1.1,0.5,1)`; renk 450 ms ease |
| Sıvı esneme | Damla durumu değişti | Gövde: 22% → (1.07, 0.90), 50% → (0.96, 1.05), 76% → (1.015, 0.99), 100% → 1. Köken: damla %50/%66, daire %50/%58 | 720 ms, `cubic-bezier(0.3,0.7,0.4,1)`, 140 ms gecikme |
| Işık yayılımı | Damla durumu değişti | Gövde merkezinde 60u çaplı, yeni renkte, 9u bulanık daire: 0% (op 0, ölçek 0.6) → 25% (op 0.5) → 100% (op 0, ölçek 1.7). Gövdenin arkasında. | 1300 ms, `cubic-bezier(0.2,0.7,0.3,1)` |
| Halka yeniden çizimi | Halka rengi değişti **veya** damla durumu değişti | Yol uzunluğu 100'e normalize, dash 100, offset 100 → 0 (soldan sağa çizilir). Üst halka 200 ms, alt halka 320 ms gecikmeli | 720 ms, `cubic-bezier(0.32,0.72,0,1)` |
| Nabız (yeni fiyat) | Her yeni fiyat | Gövde içindeki ince ışık yayı merkez etrafında bir tur döner: yükselişte saat yönü (+360°), düşüşte ters (−360°) | 1100 ms, `cubic-bezier(0.45,0.05,0.2,1)` |
| Parıltı (yeni fiyat) | Her yeni fiyat | Yansıma 0,26 → 0,70 opaklığa çıkıp (%10, %12) kayar, geri döner | 1100 ms, aynı eğri |

Kurallar:
- Durum değişimi efektleri **bir kez** oynar, tekrar etmez.
- Nabız ayarı (Sakin / Normal / Hızlı / Kapalı) yalnızca nabız ve parıltı katmanını etkiler.
- Sistemde "animasyonları kaldır" açıksa: geçişler anlık, esneme/ışık/nabız yok.
- Tüm süreler test/inceleme için global bir çarpanla ölçeklenebilir (prototipteki "Hız" ayarı).

## Compose notları
- Gövde `Path` + `graphicsLayer` (rotation, scale, transformOrigin). Esneme ayrı bir iç `graphicsLayer`.
- Halkalar `drawPath` + `PathEffect.dashPathEffect` / `PathMeasure` ile kısmi çizim.
- Işık yayılımı ve yansıma: `Modifier.blur` (API 31+) veya önceden bulanıklaştırılmış radyal fırça; API 26–30 için yedek.
- Durum değişimini algılamak için `LaunchedEffect(state)` + `Animatable`.
- Bu bileşen bir **önizleme ekranında** (tüm durumlar, tüm boyutlar, hız çarpanı) prototiple yan yana karşılaştırılarak onaylanır.
