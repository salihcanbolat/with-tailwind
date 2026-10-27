# 03 · Veri kaynakları ve backend

## İlke
Telefon hiçbir veri sağlayıcıya doğrudan bağlanmaz. Araya kendi backend'imiz girer: anahtarları gizler, önbellek tutar, kaynak düşerse yedeğe geçer, durumu **tek yerde** hesaplar ve yalnızca değişiklik olduğunda cihazlara push gönderir.

## Kaynaklar (araştırma sonucu, entegrasyon öncesi güncel koşullar doğrulanacak)
| Kaynak | Kullanım | Notlar |
|---|---|---|
| Binance (halka açık REST + WebSocket) | Kripto, anlık | Piyasa verisi için anahtar gerekmez. WS bağlantısı 24 saatte kapanır → otomatik yeniden bağlanma şart. |
| TCMB EVDS (evds3.tcmb.gov.tr) | Resmi kur, geçmiş veri | Ücretsiz anahtar. Anlık değil, günlük. Eski evds2 REST API'si kapatıldı. |
| Trunçgil Finans (api.truncgil.com/finance) | Serbest piyasa döviz + altın, hızlı başlangıç | Ücretsiz JSON. Ticari kullanım koşulları netleştirilecek. |
| altinapi | Alternatif altın/döviz | Ücretsiz katman var, WebSocket yalnızca ücretli planlarda. Harem Altın ile resmi bağı olmadığını belirtiyor. |

Harem Altın'ın geliştiricilere açık resmi API'si yok; adı/logosu izin alınmadan kullanılmaz.

Her kaynak bir `PriceSource` arayüzünü uygular; birincil/yedek sırası yapılandırmadadır. Kaynak sağlığı (son veri zamanı, hata oranı) izlenir.

## Backend bileşenleri
1. **Toplayıcılar (ingestors):** kaynaklardan fiyat çeker/dinler, `ticks` tablosuna yazar.
2. **Mum üretici:** 1 dk'lık OHLC mumları üretir (pencere hesapları bunlardan yapılır).
3. **Motor çalıştırıcı:** her yeni mumda (ve canlı fiyatta damla için) `:engine` ile durumları hesaplar, Redis'te son durumu tutar, olay üretir.
4. **Alarm değerlendirici:** kullanıcı alarmlarını olaylar ve fiyatlar üzerinden değerlendirir.
5. **Bildirim gönderici:** FCM ile cihazlara veri mesajı gönderir.
6. **API:** REST (Ktor).

Çapraz kurlar ayrı çekilmez: `pay / payda` fiyatı mum düzeyinde hesaplanır, motor aynen uygulanır.

## API sözleşmesi (v1)
`GET /v1/snapshot?assets=usdtry,xau_gram,btcusdt`
```json
{
  "serverTime": "2026-10-27T10:42:05Z",
  "assets": [{
    "id": "usdtry", "kind": "fx", "name": "USD/TRY", "short": "USD", "badge": "usd",
    "price": 0.0, "currency": "TRY", "updatedAt": "...", "marketOpen": true,
    "drop":      {"interval": "15m", "state": "FLAT", "shapeState": "FLAT", "net": 0.03, "range": 0.11, "since": "..."},
    "upperRing": {"interval": "4h",  "state": "FLAT", "shapeState": "FLAT", "net": 0.05, "range": 0.20, "since": "..."},
    "lowerRing": {"interval": "1d",  "state": "UP",   "shapeState": "UP",   "net": 0.42, "range": 0.60, "since": "..."}
  }]
}
```
`GET /v1/history?asset=usdtry&range=1d` → detay grafiği için seri.
`GET /v1/assets` → eklenebilir varlık kataloğu (kategori, ad, rozet).
`PUT /v1/devices/{id}` → FCM token, takip listesi, varlık başına aralık/eşik ayarları, alarmlar.

Kullanıcının seçtiği aralıklar ve eşikler kişiye özel olduğu için motor **cihaz-varlık-ayar** üçlüsü başına çalışır. Aynı ayarı kullananlar gruplanarak hesap paylaşılır (önbellek anahtarı: `asset|interval|s|d`).

## Push (FCM veri mesajı)
```json
{"type": "state_change", "asset": "usdtry", "part": "drop", "from": "FLAT", "to": "UP", "at": "..."}
{"type": "alarm", "alarmId": "...", "asset": "btcusdt", "kind": "price", "text": "..."}
{"type": "refresh"}
```
Neden bu tasarım: Android widget'ı ve arka plan işleri sistemce kısıtlıdır (periyodik iş en sık ~15 dk). Kullanıcı için önemli olan **durum değişimidir** ve bu seyrek olur; backend yalnızca değişimde push gönderir, cihaz widget'ı o anda yeniler. Böylece hem pil korunur hem widget güncel kalır. Yüksek öncelikli FCM mesajlarının kotası ve davranışı güncel belgeden doğrulanmalı.

## Tazelik
Her yanıtta `updatedAt` bulunur. Uygulama içi ve detay sahnesinde "canlı · şimdi / N sn önce" gösterilir. Veri 2× beklenen güncelleme süresinden eskiyse gösterge soluklaşır (%60 opaklık) ve "veri gecikiyor" yazılır.
