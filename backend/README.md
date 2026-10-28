# Backend

Ktor sunucusu: fiyatları toplar, 1 dakikalık mumlara çevirir, `:engine` ile durumları hesaplar, cihazlara FCM ile bildirir ve `docs/03`'teki REST API'yi sunar.

## Çalıştırma

Sentetik test verisiyle (internet ve veritabanı gerekmez):

```bash
DAMLA_SOURCES=test ./gradlew :backend:run
curl "localhost:8080/v1/snapshot?assets=usdtry,btcusdt,xau_gram/xag_gram"
```

`test` kaynağı **gerçek piyasa verisi değildir**. Bütün varlıklar 100 civarında başlar ve sabit dalgalarla oynar; amaç, damla ile iki halkanın her durumdan geçmesini sağlamaktır. Yanıtta `"source": "test"` görünür.

Gerçek veriyle (Binance ve Trunçgil) ve PostgreSQL ile; Docker ve internet gerekir:

```bash
scripts/backend-dogrula.sh
```

Betik sırayla şunları yapar:
- veritabanını başlatır (`docker compose up -d db`);
- sunucuyu derleyip çalıştırır;
- 31 günlük geçmişin inmesini bekler;
- bir dakika arayla üç snapshot ve bir günlük geçmiş yazdırır.

**Yedeğe geçişi denemek için:**
1. `DAMLA_SOURCES=binance,test scripts/backend-dogrula.sh` ile çalıştırın.
2. Çalışırken interneti bir dakika kapatın. `source` alanı önce `test`'e, bağlantı gelince yeniden `binance`'e döner.
3. İş bitince `docker compose down -v` ile veritabanını silin, çünkü yedek fiyatlar sentetiktir.

## Ortam değişkenleri

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `PORT` | 8080 | |
| `DAMLA_SOURCES` | `binance,truncgil` | Virgülle ayrılmış kaynaklar; bir varlığı destekleyenlerden ilk yazılan birincil kaynaktır. Seçenekler: `binance` (kripto), `truncgil` (döviz, gram ve çeyrek altın, gümüş), `test`. |
| `DATABASE_URL` | yok | Örnek: `jdbc:postgresql://localhost:5432/damla`. Verilmezse her şey bellekte tutulur. |
| `DATABASE_USER`, `DATABASE_PASSWORD` | yok | |
| `FCM_PROJECT_ID` | yok | Firebase proje kimliği. |
| `GOOGLE_APPLICATION_CREDENTIALS` | yok | Servis hesabı JSON dosyasının yolu. Bu ve `FCM_PROJECT_ID` yoksa push gönderilmez, yalnızca günlüğe yazılır. |
| `DAMLA_DIGEST_AI` | kapalı | `on` olursa ve `ANTHROPIC_API_KEY` verilmişse Günün özeti cümlesini Claude yazar. Şimdilik kapalı tutuluyor; özet yine gider, cümleyi telefon kendi şablonundan kurar. |
| `ANTHROPIC_API_KEY` | yok | Yalnızca `DAMLA_DIGEST_AI=on` iken kullanılır. |

## API

| Uç nokta | Ne döner |
|---|---|
| `GET /health` | `ok` |
| `GET /v1/assets` | Eklenebilir varlıklar ve çapraz kur tarafları (Türk lirası dahil) |
| `GET /v1/snapshot?assets=usdtry,xau_gram/xag_gram[&device=ID]` | Fiyat, tazelik, kaynak, piyasa durumu ve üç parçanın durumu. `device` verilirse o cihazın aralık ve eşikleri kullanılır. |
| `GET /v1/history?asset=ID&range=1d\|1w\|1mo\|1y[&points=24]` | Aralığın her dilimi için son kapanış fiyatı |
| `PUT /v1/devices/{id}` | Gövde: `{"fcmToken": "...", "assets": [{"id": "usdtry", "drop": "15m", "upperRing": "4h", "lowerRing": "1d", "stable": 0.1, "wave": 0.3}]}`. Boş bırakılan alanlar varsayılan değeri alır. Yanıt 204, geçersiz istekte 400 ve gerekçeler. |

Çapraz kur kimliği `pay/payda` biçimindedir. Örnekler: `xau_gram/xag_gram`, `btcusdt/xau_gram`, `xag_gram/try`.

## Testler

```bash
./gradlew :backend:test
```

Veritabanı testleri `DAMLA_TEST_DATABASE_URL` tanımlıysa o veritabanına bağlanır. Tanımlı değilse Testcontainers ile bir PostgreSQL başlatır; bunun için Docker gerekir. CI bir PostgreSQL servis konteyneri kullanır.

## Kaynak örnekleri

Geliştirme ortamının erişemediği kaynakların gerçek yanıtları GitHub üzerinden alınır. `samples/` ile başlayan bir dal gönderildiğinde `.github/workflows/source-samples.yml` Trunçgil'in yanıtlarını bir dakika arayla dört kez indirir ve aynı dala `samples/` klasörüne yazar. Derleme yapılmaz.

```bash
git push origin HEAD:samples/truncgil-2
```

`src/test/resources/truncgil/` altındaki dosyalar 2026-10-28 örneklerinden (`samples/truncgil-1` dalı) kısaltılmıştır.
