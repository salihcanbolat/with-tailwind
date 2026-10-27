# 02 · Gösterge motoru (durum hesaplama)

Bu belge, damla/halka durumlarının **tek doğru kaynağıdır**. Kod: saf Kotlin modülü `:engine`, yan etkisiz, platformdan bağımsız, %100 birim testli.

## Girdi
Her varlık ve her parça (drop / upperRing / lowerRing) için o parçanın aralığı `T` kadar geriye uzanan fiyat penceresi:
- `p0`: pencerenin başındaki fiyat (T önceki son işlem/kapanış)
- `p1`: en son fiyat
- `hi`, `lo`: pencere içindeki en yüksek ve en düşük fiyat
- `marketOpen`: varlığın piyasası şu an açık mı

## Ölçüler
```
net   = (p1 - p0) / p0 * 100        // yüzde, işaretli
range = (hi - lo) / p0 * 100        // yüzde, işaretsiz
```

## Eşikler (varlık başına ayarlanabilir)
- `s` = **stabil eşiği**: |net| bunun altındaysa varlık "yerinde" sayılır
- `d` = **dalgalanma eşiği**: yerinde olsa bile range bunu aşarsa "dalgalı"
- Kural: `d > s` olmak zorunda; arayüz bunu zorlar.

Başlangıç varsayılanları (ürün sahibi tarafından ayarlanacak, kodda tek yerde sabit):

| Kategori | s (stabil) | d (dalgalanma) |
|---|---|---|
| Döviz | 0,10 % | 0,30 % |
| Maden | 0,15 % | 0,45 % |
| Kripto | 0,50 % | 1,50 % |
| Çapraz | payın kategorisi | payın kategorisi |

## Karar sırası
```
if (!marketOpen)            -> CLOSED  (şekil: son açık durumun şekli korunur, renk gri)
else if (|net| >= s)        -> net > 0 ? UP : DOWN
else if (range >= d)        -> WAVE
else                        -> FLAT
```

## Titreşmeyi önleme (histerezis)
E�ik sınırında durum sürekli gidip gelmesin diye:
- Bir duruma **girmek** için tam eşik gerekir; **çıkmak** için ölçünün eşiğin `h = 0,8` katının altına inmesi gerekir. (UP'tan çıkmak: net < 0,8·s)
- Ayrıca **asgari bekleme**: damla için 60 sn, halkalar için 5 dk boyunca yeni durum korunmadan "durum değişti" olayı üretilmez.
- `h` ve bekleme süreleri yapılandırılabilir sabittir.

## Piyasa açık/kapalı
- Kripto: her zaman açık.
- Döviz/maden: kaynak piyasanın seans takvimi (hafta sonu, resmi tatiller). Takvim backend'de tutulur.
- Çapraz kur: bileşenlerden biri kapalıysa CLOSED.
- Ayarlar'da "Piyasa kapalıyken gri göster" kapalıysa son durum renkli kalır, yalnızca simge açıklamasına "piyasa kapalı" eklenir.

## Çıktı
```kotlin
enum class State { UP, DOWN, FLAT, WAVE, CLOSED }
data class PartState(val state: State, val shapeState: State /* CLOSED iken son açık şekil */, val net: Double, val range: Double, val interval: Interval, val since: Instant)
data class AssetStates(val drop: PartState, val upperRing: PartState, val lowerRing: PartState)
```

## Olaylar
Motor her hesaplamada önceki durumla karşılaştırıp olay üretir:
- `DropStateChanged(asset, from, to)` → bildirim, titreşim, saat uyarısı, widget güncellemesi, varsa durum alarmı
- `RingStateChanged(asset, ring, from, to)` → yalnızca widget/görünüm güncellemesi (+ ilgili alarm)
- `MarketClosed / MarketOpened`

## Zorunlu test tablosu (örnek)
| net | range | s | d | önceki | beklenen |
|---|---|---|---|---|---|
| +0,12 | 0,2 | 0,10 | 0,30 | FLAT | UP |
| +0,09 | 0,2 | 0,10 | 0,30 | UP | UP (histerezis: 0,09 ≥ 0,08) |
| +0,07 | 0,2 | 0,10 | 0,30 | UP | FLAT |
| -0,05 | 0,35 | 0,10 | 0,30 | FLAT | WAVE |
| 0 | 0 | — | — | (kapalı) | CLOSED |
Testlere sınır değerleri, sıfıra bölme (p0 = 0), eksik veri ve çapraz kur bileşimi de eklenir.
