# 07 · Uygulama içi ekranlar

Genel görünüm: koyu tema (#0B0F14 zemin), cam kartlar (beyaz %5 zemin + 0,5 px beyaz %8 iç çizgi, köşe 20), birincil metin #F2F4F7, ikincil metin rgba(235,235,245,0.6). Basılınca ölçek 0.94–0.97. Açık tema ve "Sistem" seçeneği Ayarlar'da var; açık tema ekranları henüz tasarlanmadı (bkz. 09).

| Ekran | Dosya | Öz |
|---|---|---|
| Panel | `Panel.dc.html` | Canlı nokta; **piyasa havası şeridi** (takip listesindeki durumların oranı + "2 yükselişte · 1 sabit…"); widget önizleme kartı; takip listesi (damla + ad + üç aralığın minik damla/halka simgeleri + durum); "Nasıl okunur?" alttan açılan öğretici. Açılışta şerit ve satırlar sırayla (55 ms arayla) yukarı kayarak gelir. |
| Varlık ayarları | `VarlikAyar.dc.html` | Büyük damla + parça açıklamaları; Damla/Üst halka/Alt halka için ayrı aralık; stabil ve dalgalanma eşiği kaydırıcıları; "Widget'ta göster"; kaydedilince "Kaydedildi" bildirimi. |
| Varlık ekle | `Ekle.dc.html` | Arama + kategori sekmeleri (Tümü/Döviz/Maden/Kripto/Çapraz); yerinde ekle/çıkar; Çapraz sekmesinde çapraz kur ekranına giriş. |
| Çapraz kur | `CaprazKur.dc.html` | Pay ÷ Payda kartları, kategorili seçim, aynı varlık iki tarafa seçilemez, dönen yer değiştirme düğmesi, sonuç damlası + "1 X = [DEĞER] Y", hazır çiftler (Altın/Gümüş, Bitcoin/Altın, EUR/USD, Gümüş/TRY, Bitcoin/USD, Ons/USD), özel ad, widget'ta gösterme, aralıklar. |
| Alarmlar | `Alarmlar.dc.html` | Kart listesi, açma/kapama (kapalı soluk), durum alarmında iki damlayla geçiş gösterimi, koşul satırına dokunup düzenleme, Geçmiş sekmesi. |
| Yeni alarm | `AlarmKur.dc.html` | Varlık seçimi (çapraz dahil); koşul türleri **Durum / Fiyat / Yüzde / Dalga**; en fazla **3 koşul**, **VE/VEYA** bağlacı; "şu an" rozeti (zaten bulunulan durum hedef seçilemez); bildirim: sıklık (bir kez / her seferinde / saatte 1), geçerlilik (1 gün / 1 hafta / süresiz), sesli uyarı, sessiz saatlerde bekle, piyasa kapalıyken bekle; altta her seçimde yeniden yazılan düz Türkçe özet cümle. |
| Widget ayarları | `KlasorIcerik.dc.html` | Kapalı + açık önizleme yan yana; önizlemede düzenleme modu (sallanan damlalar, × ile kaldır, kesikli + ile ekle, dokununca başa al); Stil, Boyut, Açılış + açıklama satırları; sıralama; **taslak/kaydet akışı** (Kaydedilmedi uyarısı, alt çubuk Geri al/Kaydet, çıkışta "Kaydet ve çık / Kaydetmeden çık / Düzenlemeye devam et"). |
| Ayarlar | `Ayarlar.dc.html` | Nabız (Sakin/Normal/Hızlı/Kapalı, yanında canlı damla önizlemesi), titreşim, piyasa kapalıyken gri, tema, yeni varlıklar için varsayılan aralıklar (yerinde açılır), veri kaynağı bilgisi. |

## Alarm modeli
```kotlin
enum class ConditionType { STATE, PRICE, PERCENT, WAVE }
data class Condition(val type: ConditionType, val part: Part?, val target: State?, val op: Op?, val value: Double?, val interval: Interval?)
data class Alarm(val id: String, val assetId: String, val conditions: List<Condition> /* 1..3 */, val joiner: Joiner /* AND, OR */,
                 val frequency: Frequency /* ONCE, EVERY_TIME, HOURLY */, val validity: Validity /* DAY, WEEK, FOREVER */,
                 val sound: Boolean, val waitQuietHours: Boolean, val waitMarketClosed: Boolean, val enabled: Boolean)
```
Alarmlar backend'de değerlendirilir; cihaz yalnızca bildirimi gösterir. Özet cümle üretimi tek bir fonksiyondadır ve birim testlidir.

## Henüz tasarlanmamış durumlar (Claude Code tasarım dilini koruyarak önerir, onaya sunar)
İlk açılış tanıtımı (widget'ı ana ekrana ekleme rehberi), internet yok, veri gelmedi/gecikiyor, takip listesi boş, alarm yok, kaynak hatası.
