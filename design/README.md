# Tasarım dosyaları

Bu klasördeki `.dc.html` dosyaları Claude Design'da hazırlanmış **çalışan prototiplerdir**. Kendi çalışma ortamlarına (`support.js`) bağlı oldukları için tarayıcıda tek başına açılmazlar; canlı hallerini görmek için:
**https://claude.ai/artifact/ETq5kFERSqFPiGttTPQmsZ**

## Nasıl okunur
Her dosyada iki bölüm var:
1. `<x-dc>` içindeki işaretleme: yerleşim, ölçüler, renkler. `{{ad}}` şeklindeki yerler aşağıdaki betikten gelen değerlerdir. `<sc-for>` = liste, `<sc-if>` = koşullu gösterim, `<dc-import name="Gosterge" ...>` = damla bileşeninin kullanımı.
2. `<script type="text/x-dc">` içindeki `renderVals()`: durum, veri ve **tüm animasyon değerleri** (süre, gecikme, cubic-bezier eğrisi, dönüşümler). Değerleri buradan birebir al. `t(ms)` = süre × hız çarpanı.
`data-props` özniteliği, prototipin tasarım aracında değiştirilebilen ayarlarını listeler.

## Dosyalar
| Dosya | Ne |
|---|---|
| `Gosterge.dc.html` | **Damla bileşeni**: geometri, durumlar, logolar, tüm animasyon katmanları. Her şeyin temeli. |
| `Prototip.dc.html` | Canlı widget prototipi: widget → Akış → detay → paylaşım. |
| `Panel.dc.html` | Ana panel |
| `VarlikAyar.dc.html` | Varlık ayarları |
| `Ekle.dc.html` | Varlık ekle |
| `CaprazKur.dc.html` | Çapraz kur oluştur |
| `Alarmlar.dc.html` | Alarm listesi |
| `AlarmKur.dc.html` | Yeni alarm |
| `KlasorIcerik.dc.html` | Widget ayarları |
| `Ayarlar.dc.html` | Ayarlar |
| `Bildirim.dc.html` | Kalıcı bildirim (panel + kilit ekranı) |
| `Saat.dc.html` | Wear OS önizlemesi + çalışma mantığı + titreşim desenleri |
| `Masaustu.dc.html` | Bilgisayar eşlikçisi (Windows / macOS) |

Belgeler (`docs/`) ile tasarım dosyası çelişirse belge esastır; çelişkiyi bildir.
