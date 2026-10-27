# 05 · Hareket ve dokunsal dil

## İlke
"İlgi çekici" = premium, akıcı, yerinde ve uyumlu. Abartılı efekt, sürekli hareket eden arka plan, parçacık, yanıp sönme yok. Hareketin büyüklüğü olayın önemiyle orantılıdır:

| Olay sıklığı | Örnek | Görsel tepki | Dokunsal tepki |
|---|---|---|---|
| Çok sık | Yeni fiyat | Fısıltı: ışık turu + parıltı | Yok |
| Seyrek | Damla durumu değişti | Tek net vurgu: esneme + ışık + halka çizimi | Durum deseni |
| Kullanıcı eylemi | Dokunma, açma, kaydırma | Doğrudan, fiziksel his | Hafif tık (sistem) |

## Hareket token'ları
| Ad | Değer | Kullanım |
|---|---|---|
| `easeStandard` | cubic-bezier(0.32, 0.72, 0, 1) | Açılmalar, kaymalar, varsayılan |
| `easeExit` | cubic-bezier(0.4, 0, 0.2, 1) | Kapanmalar (kısa ve sakin) |
| `springOpen` | cubic-bezier(0.25, 1.12, 0.5, 1) | Akış açılımı: yaylı ama esnemesiz |
| `springSoft` | cubic-bezier(0.25, 1.04, 0.5, 1) | Detay öğelerinin gelişi |
| `springShape` | cubic-bezier(0.25, 1.1, 0.5, 1) | Damla şekil geçişi |
| `pulse` | cubic-bezier(0.45, 0.05, 0.2, 1) | Nabız ve parıltı |
| `dur.fast` | 160–200 ms | Basma geri bildirimi (ölçek 0.94–0.97) |
| `dur.base` | 420–560 ms | Çoğu geçiş |
| `dur.open` | 600 ms | Detay sahnesi açılışı |
| `dur.close` | 440 ms | Kapanışlar |
| `stagger` | 40–55 ms | Liste/öğe sıralı gelişleri |

Compose'da cubic-bezier eğrileri `CubicBezierEasing` ile; mümkün olduğunda yaylı eğriler yerine bu eğriler birebir kullanılır ki prototiple aynı hissi versin.

## Dokunsal dil
Amaç: **ekrana bakmadan, titreşimden ne olduğunu anlamak.** Her olay türünün kendine özgü ritmi vardır. Desenler milisaniye ve genlik (0–255) olarak:

| Olay | Ritim | Zamanlama (ms) | Genlik |
|---|---|---|---|
| Yükselişe döndü | Tık-TIK (yukarı çıkan) | `[0, 25, 70, 35]` | `[0, 120, 0, 255]` |
| Düşüşe döndü | TIK-tık (aşağı inen) | `[0, 35, 70, 25]` | `[0, 255, 0, 120]` |
| Sabite / dalgalıya döndü | Tek uzun yumuşak | `[0, 260]` | `[0, 80]` |
| Fiyat / yüzde alarmı | Üç eşit kısa | `[0, 30, 70, 30, 70, 30]` | `[0, 200, 0, 200, 0, 200]` |
| Piyasa kapandı | Tek hafif dokunuş (ayardan kapatılabilir) | `[0, 20]` | `[0, 60]` |
| Yeni fiyat | Yok | — | — |

Android: `VibrationEffect.createWaveform(timings, amplitudes, -1)`. Cihaz genlik kontrolü desteklemiyorsa (`hasAmplitudeControl() == false`) desenler süre farkıyla ayrışacak biçimde yedeğe düşer (ör. yükseliş: kısa-uzun, düşüş: uzun-kısa). Wear OS'ta aynı desenler. Ayarlar → Titreşim kapalıysa hiçbiri çalmaz; sessiz saatler ve sistem "rahatsız etme" kurallarına uyulur.

Referans: `design/Saat.dc.html` içindeki `pats` nesnesi ve "Bileğinde hissettiğin" görselleştirmesi.
