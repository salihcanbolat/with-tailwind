# Kararlar

Projede alınan kararların kaydı. Bir karar değişirse eskisinin üstü çizilmez, altına tarihli yeni satır eklenir.

## Teknik yığın (Faz 0, 2026-10-27)

| Alan | Karar | Sürüm |
|---|---|---|
| Dil | Kotlin | 2.4.21 |
| Derleme | Gradle (wrapper), tek sürüm kataloğu `gradle/libs.versions.toml` | 9.8.2 |
| JDK | Derleme JDK 21; `:engine` ve Android Java 17 bytecode üretir | 21 / 17 |
| Android | AGP (yerleşik Kotlin, `kotlin-android` eklentisi yok) | 9.4.0 |
| Android SDK | compileSdk 37, targetSdk 37, minSdk 26 | — |
| Arayüz | Jetpack Compose (BOM) | 2026.09.00 |
| Widget | Glance appwidget | 1.2.0 |
| Android diğer | activity-compose 1.13.0, core-ktx 1.19.1. Hilt, Room, DataStore, WorkManager, FCM ve Ktor client ilgili fazda eklenir. | — |
| Backend | Ktor sunucusu (Netty), logback | 3.6.0 / 1.6.6 |
| Veritabanı | PostgreSQL + TimescaleDB eklentisi; Redis v1'de isteğe bağlı (Faz 2'de kesinleşir) | — |
| Test | JUnit + kotlin-test; kapsam için Kover | 6.1.3 / 0.9.11 |
| Biçim | Spotless + ktlint (`ktlint_official`) | 8.10.4 / 1.8.0 |
| CI | GitHub Actions (`.github/workflows/ci.yml`) | — |
| Wear OS | Compose for Wear OS, Tiles (ProtoLayout), Complications, Data Layer (Faz 7) | — |
| Bilgisayar | Tauri (Faz 8) | — |

## Depo yapısı

```
engine/            saf Kotlin durum motoru (java.* kullanmaz, KMP'ye taşınabilir)
backend/           Ktor sunucusu
android/           ayrı Gradle derlemesi
  app/             uygulama
  widget/          Glance widget kütüphanesi
docs/  design/     ürün belgeleri ve tasarım dosyaları
```

İleride eklenecek modüller: `api-model/` (Faz 2, backend–uygulama veri sözleşmesi), `android/gauge/` (Faz 3, ortak damla çizimi), `android/wear/` (Faz 7), `desktop/` (Faz 8).

**Android neden ayrı derleme:** Android eklentisi ve kütüphaneleri Google'ın Maven deposundan gelir ve Android SDK gerektirir. Kök derleme (engine + backend) bunlara bağımlı olursa SDK'sız ortamda (sunucu, bu bulut oturumu) hiç yapılandırılamaz. `android/settings.gradle.kts` kök derlemeyi `includeBuild("..")` ile içerir; `com.damla:engine` bağımlılığı yerel `:engine` modülüne bağlanır. Android Studio'da `android/` klasörü açılır.

Komutlar:
- `./gradlew build` → biçim kontrolü, engine ve backend derleme + test, engine kapsam kontrolü
- `./gradlew spotlessApply` → tüm depoyu (Android dahil) biçimlendirir
- `./gradlew -p android assembleDebug` → Android (SDK gerekir)

## Geçici kimlikler
Paket kökü `com.damla`, applicationId `com.damla.app`. Mağazaya çıkmadan değiştirilebilir.

## Motor kuralları (Faz 1)
`docs/02`'nin uygulamada nasıl yorumlandığı:

1. **Asgari bekleme:** Gösterilen durum her zaman onaylı durumdur. Yeni durum aday olarak bekler; damlada 60 sn, halkada 5 dk boyunca kesintisiz sürerse onaylanır ve olay üretilir. Aday arada değişirse sayaç sıfırlanır.
2. **Histerezis** (h = 0,8):
   - UP'ta kalmak: net ≥ h·s. DOWN'da kalmak: net ≤ −h·s.
   - WAVE'de kalmak: |net| < s ve range ≥ h·d.
   - Bir duruma girmek tam eşik ister. UP/DOWN girişi WAVE'e göre önceliklidir.
3. **Piyasa kapalı:** CLOSED bekleme süresi beklemez. Şekil son açık şekil olarak korunur. Yalnızca `MarketClosed` olayı üretilir, `DropStateChanged` üretilmez. Açılışta histerezisin "önceki durumu" kapanış öncesi şekildir.
4. **Pencere:** Takvim zamanı; p0, T önceki son fiyattır (hafta sonu boşluğu dahil).
5. **Eksik veri ve p0 ≤ 0:** Ölçüm "yetersiz veri" döner. Önceki durum korunur ve olay üretilmez. Soluklaştırmayı tazelik katmanı yapar.
6. **Eşikler:** Belgedeki gibi varlık başına tek s/d çifti üç parçaya uygulanır. Motor API'si eşikleri parça başına alır; ileride parça başına eşik istenirse yalnızca yapılandırma değişir.
7. **Kayan nokta:** Eşik karşılaştırmalarında 1e-9 tolerans kullanılır. Örneğin p0 = 100, p1 = 100,1 için net = 0,0999999… çıkar ve tolerans olmadan 0,10'u geçemez.
8. **Çapraz kur:** Pay ÷ payda, 1 dk mum kapanışlarının oranından hesaplanır; hi/lo bu oran serisinden alınır. Biri kapalıysa CLOSED. Kategori payın kategorisidir. Para birimi normalizasyonu (ör. Bitcoin/Altın) backend işidir, Faz 2'de kesinleşir.

Uygulama sırasında netleşenler (Faz 1):

9. **Pencere hesabı:**
   - p0, pencere başlangıcında ya da ondan önce biten son 1 dk mumun kapanışıdır.
   - hi/lo, p0'ı ve pencereyle örtüşen mumları kapsar.
   - Pencere başlangıcından önce mum yoksa pencere oluşmaz ("yetersiz veri").
   - Pencerede hiç işlem yoksa p1 = p0 olur; sonuç FLAT çıkar, tazelik katmanı soluklaştırır.
10. **Çapraz kur ayrıntısı:** Bir dakikada mumu olmayan tarafın son kapanışı ileri taşınır. İki tarafın da fiyatı oluşmadan önceki dakikalar atlanır. Aynı varlık iki tarafa seçilirse `CrossPair` hata verir.
11. **"1 ay":** 30 günlük kayan pencere (43.200 dk).
12. **Eşik doğrulaması:** s > 0 ve d > s zorunlu; ikisi de sonlu sayı olmalı. h (0, 1] aralığında, bekleme süreleri negatif olamaz.
13. **İlk değerlendirme:** Durum bekleme süresi beklemeden atanır ve olay üretilmez. Üç parçanın da en az bir kez verisi olana kadar `AssetStates` yoktur (null).
14. **Açılış:**
    - Durum anında gösterilir.
    - Önce `MarketOpened`, ardından şekli kapanış öncesinden farklı olan her parça için durum olayı üretilir.
    - Kapanış anında bekleyen aday düşer.
    - Açılışta veri yoksa korunan şekil geri gelir.
15. **`since`:** Bekleme sonrası onaylanan durumda, adayın ilk görüldüğü andır. Kapanışta kapanış anı, açılışta açılış anıdır.
16. **Olaylar:**
    - Her olayda onaylandığı an (`at`) bulunur; push yükündeki `at` buradan gelir.
    - Sıralama: piyasa olayı, damla, üst halka, alt halka.
    - Durum aynı kaldıkça olay üretilmez; net/range değerleri yine güncellenir.
17. **Saflık:** `GaugeEngine` durum tutmaz. Önceki `AssetMemory` girdi olarak verilir, yenisi çıktı olarak döner; backend onu olduğu gibi saklar.
18. **"Piyasa kapalıyken gri göster" kapalıysa:** Motor yine CLOSED üretir. Arayüz rengi `shapeState`'ten alır ve açıklamaya "piyasa kapalı" ekler.

## Tasarım–belge çelişkilerinde uygulanacaklar
Belge esastır. Aşağıdakiler soru listesindeki önerilerdir; "plana uygun ilerle" onayıyla uygulanıyor (2026-10-27):

- **Durum kelimeleri:** Yükselişte / Düşüşte / Sabit / Dalgalı / Piyasa kapalı.
- **Metin renkleri:** Belgedeki parlak tonlar (#30D158, #FF453A, #FFD60A, #FF9F0A).
- **Akış açılışı:** Eğri `springOpen`; süre 560 ms ve 45 ms aralık prototipten.
- **Yeni fiyat efektleri:** Prototipteki fiyat parlaması, ▲/▼ pop ve grafik uç halkası eklenmez.
- **Detayda varlık kaydırma:** Durum değişimi efekti oynamaz.
- **Ayarlar:** "Fiyat gelince titreşim" anahtarının etiketi "Titreşim" olur.
- **Yüzde gösterimi:** FLAT/WAVE yüzdesinde "±" yerine gerçek işaretli değer gösterilir.
- **1 günlük aralık:** "Dün" yerine "son 1 gün" yazılır.
- **Akış'ın yaslanacağı kenar:** Widget'ın `sourceBounds` konumuna göre seçilir.
- **Nabız ayarı:** Gerçek uygulamada iki nabız arasındaki asgari süreyi belirler.
- **Tasarımdaki gibi uygulananlar:** Yansıma elipsinin konumu ve nabız ışık yayının durgun halde görünmesi.

## Platform kararları
- **Kalıcı bildirim:** Foreground service kullanılmaz. Bildirim FCM ile güncellenen ongoing bildirimdir. Kullanıcı kapatırsa `deleteIntent` ile algılanır ve ayar kapalı gösterilir. Görünüm DecoratedCustomViewStyle'dır; daraltılmış hali 48 dp'ye sığar.
- **FCM:** Widget ve halka güncellemeleri normal öncelikle gider. Yüksek öncelik yalnızca görünür bildirim üreten mesajlarda (alarm, durum bildirimi) kullanılır. `state_change` birleştirilemez (non-collapsible) gönderilir. WorkManager ile periyodik snapshot eşitlemesi güvenlik ağı olarak çalışır.
- **Widget:** Her güncelleme tam güncellemedir (`partiallyUpdateAppWidget` kullanılmaz). Gölgeli etiket için `AndroidRemoteViews` kullanılır. Widget içi animasyon Faz 4 denemesinde doğrulanır.
- **Wear OS:** Üç ayrı komplikasyon veri kaynağı (SMALL_IMAGE + ortam görüntüsü, MONOCHROMATIC_IMAGE yedeği). Uyarı ekranı standart bildirimdir; dokununca uygulamadaki uyarı ekranı açılır.
- **Kaplama:** Pencere arkası bulanıklığı yalnızca Android 12+ ve destekleyen cihazlarda kullanılır. Ana ekran küçültülmez.

## Backend (Faz 2, 2026-10-27)
- **Veritabanı (değişiklik):** TimescaleDB eklentisi kullanılmıyor, düz PostgreSQL yeterli.
  - Gerekçe: Barındırma henüz belli değil ve birçok yönetilen PostgreSQL hizmeti bu eklentiyi sunmuyor.
  - 1 dk mumlar tek tabloda 400 gün saklanır. Geçmiş grafikleri SQL ile dilimlenir.
  - Motor için son 31 gün bellekte tutulur.
- **Redis (değişiklik):** v1'de yok. Tek sunucu örneğinde son durum bellekte tutulur; motor hafızası (`group_states`) Postgres'e yazılır. Hafıza olay anında ve 30 sn'de bir kaydedilir. Yatay ölçeklemeye geçilince Redis eklenir.
- **Binance:**
  - Yalnızca piyasa verisine ayrılmış uç noktalar kullanılır: `data-api.binance.vision` ve `data-stream.binance.vision`.
  - Canlı veri `kline_1m` akışından gelir; geçmiş `GET /api/v3/klines` ile çekilir.
  - Bağlantı 23 saatte bir ve `serverShutdown` olayında yenilenir.
  - 429/418 yanıtında `Retry-After` kadar beklenir.
  - Biçimler resmi belgeden (GitHub: binance-spot-api-docs) doğrulandı.
- **Trunçgil (2026-10-28):**
  - `finans.truncgil.com/v4/today.json` kullanılır. Örnekler GitHub üzerinden alındı (`samples/truncgil-1` dalı).
  - Dosya dakikada bir yeniden yazılıyor. `Update_Date` İstanbul saati, fiyatlar sayı.
  - v3 (`today.json`) 5 dakikada bir güncelleniyor ve fiyatlar "49,8443" gibi metin; kullanılmadı.
  - 20 sn'de bir ETag ile sorulur; dosya değişmediyse 304 döner, hiçbir şey indirilmez.
  - Yalnızca `Update_Date` ilerleyen dosya fiyat üretir. Fiyatın zamanı `Update_Date`'tir. Böylece donmuş bir dosya kaynağı taze göstermez, 3 dk sonra yedeğe geçilir.
  - Eşleme: USD, EUR, GBP, GRA (gram altın), CEYREKALTIN, GUMUS.
  - **Fiyat:** Satış fiyatı kullanılır. Türkiye'deki uygulamaların öne çıkardığı fiyat bu. Durum hesabı da aynı fiyattan yapılır.
  - **Ons altın:** Trunçgil'de `ONS` her örnekte 0. Ons altının şimdilik kaynağı yok.
  - Çeyrek altın üç dakika boyunca değişmedi; gram altın ve gümüş her dakika değişti. Çeyreğin ne sıklıkla güncellendiği izlenecek.
  - Geçmiş vermez. Grafik ve uzun aralıklar, sunucu çalıştıkça biriken mumlardan beslenir.
- **Kaynak seçimi:**
  - Kaynaklar `DAMLA_SOURCES` sırasıyla tercih edilir. Varsayılan `binance,truncgil`.
  - Birincil kaynak sessiz kalırsa (Binance 30 sn, Trunçgil 3 dk, test 10 sn) yedeğe geçilir; birincil kaynak geri gelince ona dönülür.
  - Hiçbir kaynak veri vermiyorsa son seçim korunur, gecikmeyi istemci tazelikten görür.
- **Kripto fiyat birimi:** USDT çiftleri tasarımdaki gibi "USD" olarak etiketlenir ("Kripto · USD").
- **Çapraz kur para birimi:** Taraflar farklı birimdeyse ikisi de USD/TRY ile TRY'ye çevrilir. USD/TRY de bileşen sayılır; kapalıysa çapraz kur CLOSED olur (docs/02 kuralı). Türk lirası sabit 1 fiyatlı bir taraftır.
- **Grup anahtarı:** `hedef|damla|üst halka|alt halka|s|d`. Üç parça birlikte değerlendirildiği için üç aralık da anahtarda yer alır.
- **Değerlendirme:** Tüm gruplar 2 sn'de bir değerlendirilir.
- **Varsayılan ayarlar ve sınırlar:**
  - Varsayılan aralıklar 15 dk / 4 sa / 1 gün (docs/01 örnekleri).
  - Aralıklar en fazla 1 ay olabilir.
  - Bir cihaz en fazla 50 varlık izleyebilir.
- **Cihaz:**
  - Cihaz kimliği anonimdir (8–64 karakter) ve kimlik doğrulama yoktur (v1).
  - Bilinen risk: Çok sayıda farklı ayarla grup sayısı şişirilebilir. Hız sınırı ve kimlik doğrulama, barındırma netleşince eklenecek.
- **Push:**
  - Biçim: FCM HTTP v1. Doğrulama resmi Firebase Admin SDK (Java) kaynağından yapıldı.
  - Bütün mesajlar şimdilik normal öncelikli ve 1 saat ömürlü.
  - Piyasa açılış ve kapanışı `refresh` mesajı olarak gider.
  - `UNREGISTERED` dönen token silinir.
- **Piyasa takvimi:**
  - FX ve maden: New York saatiyle Pazar 17:00 – Cuma 17:00 (küresel döviz piyasası alışkanlığı). Tatil listesi boş.
  - Kaynakların gerçek davranışıyla doğrulanmalı; örneğin Trunçgil hafta sonu güncelliyor mu?
- **API eklemeleri (docs/03'e ek):**
  - Snapshot'ta `source` alanı.
  - Çapraz kur için `kind: "cross"`; çapraz kurda `currency` null.
  - Snapshot'ta isteğe bağlı `device` parametresi.
  - `/v1/history`: `range` değerleri 1d/1w/1mo/1y; `points` varsayılan 24 (2–500).
- **Test kaynağı:** `DAMLA_SOURCES=test` sentetik fiyat üretir (100 civarı). Gerçek veri değildir ve `source: "test"` olarak etiketlenir.
- **EVDS:** Anahtar gelince eklenecek.

## Gösterge bileşeni (Faz 3, 2026-10-27)
- **Yapı:**
  - Hareket modeli (`GaugeMotion`) ve çizim mantığı (`GaugePainter`) platformdan bağımsız, saf Kotlin `:gauge` modülünde.
  - Android'deki `Damla` bileşeni bunları Compose tuvaline aktaran ince bir katman. Widget, bildirim ve saat bitmap'leri de aynı ressamı kullanacak.
- **Prototiple doğrulama** (`tools/gauge-reference`):
  - Prototipin kendi DOM'u ve `renderVals()` betiği Chromium'da oynatılıyor.
  - 22 senaryoda 116.241 animasyon değeri kaydedildi; dönüşüm matrisleri, opaklıklar, renkler, kesik çizgi kaymaları ve zamanlama dahil. `PrototypeReferenceTest` her derlemede bu değerleri karşılaştırıyor.
  - Piksel karşılaştırmasında 42 durumda ortalama fark 1–3/255.
- **CSS anlamı:**
  - Eğri her keyframe aralığına ayrı uygulanır.
  - Geçiş yarıda değişirse o anki değerden devam edilir.
  - CSS'in geri dönüşte süreyi kısaltma kuralı (reversing shortening) uygulanmadı. Durum değişimleri en az 60 sn arayla geldiği için pratikte oluşmaz.
  - 2026-10-27 (Faz 4): Kural artık uygulanıyor. Kaplamada açılıp hemen kapanan öğelerde gerekli oldu; göstergede de aynı kod kullanılıyor.
- **Bulanıklık:** Işık yayılımı ve yansıma, bulanıklaştırılmış dairenin tam profilini izleyen radyal gradyanla çiziliyor. Böylece API 26'dan itibaren aynı görünüyor ve RenderEffect gerekmiyor.
- **Prototip davranışları, olduğu gibi uygulananlar:**
  - **Yansıma rozetin üstünde:** Yansıma `position: absolute` olduğu için CSS onu rozetin üstüne boyar.
  - **Halkaların ölçeği:** Halkalar kendi S×H kutularında `meet` ile ölçeklenir.
  - **Gecikmedeki nokta:** Yeniden çizim gecikmesi sırasında halkanın sağ ucunda, renginde bir nokta görünür (sıfır uzunluklu çizginin yuvarlak ucu).
- **Prototipten bilinçli sapmalar:**
  - **Halkanın yeniden çizimi (belge esas):** Damla şekli ve halka rengi aynı anda değişince prototip o halkayı yeniden çizmiyor; animasyon adları A/B dönüşümlü seçildiği için. docs/04'e göre ikisinden biri değişince halka yeniden çizilir; böyle uygulandı.
  - **Piksel yaslama:** Tarayıcı, tek sayılı rozet çaplarında rozeti tam piksele yaslıyor (en fazla 0,5 px). Bu taklit edilmedi.
- **Yazı tipi:** Metin logoları sistem sans-serif yazı tipiyle, 800 kalınlıkta çiziliyor. Android 9 öncesinde kalın (bold) kullanılıyor.
- **Animasyonları azalt:** Varsayılan olarak sistemin animasyon ayarından (`ValueAnimator.areAnimatorsEnabled`) okunuyor. Açıkken geçişler anlık olur; esneme, ışık, halka çizimi ve parıltı oynamaz.
- **Önizleme ekranı:** Faz 5'te uygulama ekranları geldiği için açılıştan kalktı; kodu geliştirme aracı olarak duruyor.

## Widget ve kaplama (Faz 4, 2026-10-27)
- **Yapı (değişiklik):** Hilt ve Room kullanılmadı.
  - Uygulamanın birkaç uzun ömürlü nesnesi `DamlaServices` içinde tutuluyor.
  - Cihazdaki her şey (kimlik, izleme listesi, ayarlar, widget ayarları, son snapshot) DataStore'da duruyor.
  - Uygulama büyüdükçe ihtiyaç doğarsa eklenir.
- **Widget yerleşimi:** Damlalar prototipin sabit bloğuna oturur (`WidgetArrangement`).
  - Blok yüksekliği kutusuzda 90, kutuluda 76 dp; blok widget'ın içinde dikey ortalanır.
  - Damla merkezi bloğun üstünden 34 dp (kutusuz) / 47 dp (kutulu); mini widget'ta 32 / 46 dp.
  - Etiket üstü 68 dp (kutusuz, damlanın altında) / 7 dp (kutulu, damlanın üstünde).
  - Geniş widget eşit sütunlara bölünür: 130 dp'den 2, 200 dp'den itibaren 3 sütun. Tek hücrede "Tek varlık" seçilmediyse mini düzen gösterilir.
  - Kaplama aynı hesabı kullanır, böylece animasyon damlanın tam üstünden başlar.
- **Kutulu kart (değişiklik, 2026-10-28):** Beyaz kart artık widget'ın tamamını kaplamıyor; prototipteki gibi blok kadar yüksek (76 dp) ve hücre kenarlarından 10 dp içeride, widget'ın ortasında. Kaplama da bu dikdörtgenden başlar. Kart hücreyi doldurunca Akış'ın ilk satırının beyaz yazıları kartın üstüne düşüyordu (karar: kullanıcı).
- **Ölçüler (değişiklik):** Tek varlık kutulu düzende damla 38 dp, köşe 22 dp. Önceki 42 dp ve 20 dp prototipin seçeneklerde olmayan "1×1" modundan alınmıştı.
- **Widget gölgesi (düzeltme):** `drop-shadow(0 2px 3px)` Chromium'da standart sapması 3 px olan bulanıklıkla çiziliyor (ölçüldü). `box-shadow` ve `text-shadow`'da bulanıklık değeri standart sapmanın iki katıdır. Önceki çizim daha keskindi.
- **Kaplamanın konumu:**
  - Widget'ın ekrandaki yeri `Intent.getSourceBounds` ile alınır. Taraf, widget'ın ortasının ekranın hangi yarısında olduğuna göre seçilir.
  - Glance'in dokunma eylemleri değiştirilemez (immutable) PendingIntent kurar. Android bunlara başlatıcının eklediği konumu koymaz (`PendingIntentRecord`, Android 14). Bu yüzden widget'ın dokunması, Glance içeriğini saran düz bir görünüme değiştirilebilir PendingIntent ile bağlanır. Intent yalnızca kaplama etkinliğini adlandırır.
  - Akış widget'ın en dış sütununa yaslanır.
  - Klasör, widget'ın o taraftaki kenarına hizalanır.
  - Detay sahnesi ekranın o tarafındaki kenarına yaslanır.
  - Widget ekranın altındaysa sahneler ekranda kalacak şekilde yukarı alınır. Akış'ın ikinci satırı altta yer yoksa üste açılır.
- **Detay açılışı (belge esas):** docs/06'daki dairesel açılım uygulandı: 24 → 560 px, 600 ms `easeStandard`; kapanış 440 ms `easeExit`.
  - Prototipin betiğinde bu değerler `cardClip` olarak var, ama işaretlemeye bağlanmamış.
  - Bölümlerin sırayla belirmesi de prototipteki gibi korunuyor. Açık sorudaki "dairesel mi, bölüm bölüm mü" ikisi birlikte uygulandı.
- **Prototiple doğrulama:**
  - 14 senaryoda 279.600 değer karşılaştırılıyor: hücreler, kahraman damla, bölümler, etiketler, grafik, aralık seçici, noktalar, paylaşım kartı, klasör kutusu, başlık ve dairesel açılım.
  - Kalan farklar Chromium'un yerleşimi 1/64 px'e, renk saydamlığını 8 bite yuvarlamasından geliyor.
  - JS `toFixed` yuvarlaması ve renklerde önceden çarpılmış saydamlıkla ara değer birebir uygulandı.
- **Platform sınırları:**
  - Ana ekranın küçülmesi (0,985) uygulanamaz, çünkü başka uygulamanın penceresi ölçeklenemez.
  - Ana ekran bulanıklığı Android 12+'da pencere arkası bulanıklığıyla yapılır; yarıçap 6 px'lik CSS bulanıklığının karşılığıdır. Daha eski sürümlerde ya da cihaz kapattıysa yalnızca karartma kalır.
  - Klasörün cam bulanıklığı (`backdrop-filter`) ayrıca çizilmez; arkadaki pencere zaten bulanık.
  - Hücre bulanıklığı ve damla gölgesi Android 12+'da RenderEffect ile çizilir. Eski sürümlerde bu iki efekt yoktur.
  - Kutusuz widget'ta gerçek widget kaplamanın altında kalır. Damlalar kalkarken eski yerlerinde karartılmış ve bulanık izleri görünür. Cihazda değerlendirilecek.
- **Metin renkleri:**
  - Koyu zeminde (Akış, detay, paylaşım kartı) docs/01'in parlak tonları kullanılır.
  - Açık cam klasörde prototipin koyu tonları kullanılır (#1E7B34, #C4271C, #7A5F00, #B25E00), çünkü parlak tonlar açık zeminde okunmuyor.
  - Piyasa kapalıyken metin koyu zeminde #AEAEB2, açık zeminde #8E8E93.
- **Grafik:**
  - FLAT rengi tasarım dosyasındaki #E0B000.
  - Noktalar `/v1/history`'den gelir (24 dilim); en düşük fiyat yüksekliğin %94'üne, en yüksek %6'sına oturur.
  - Parmağın altındaki noktada saat (1 gün), gün ve saat (1 hafta), tarih (1 ay) ya da ay ve yıl (1 yıl) ile fiyat yazar.
- **Yazı boyutu:** Kaplama sabit bir sahne olduğu için yazılar dp ile verilir ve sistem yazı boyutuyla büyümez. Uygulama ekranları sistem yazı boyutuna uyar.
- **Tazelik:**
  - "canlı · şimdi" 2 sn'ye kadar, sonra "N sn önce", 1 dk'dan sonra "N dk önce" yazar.
  - 2 dk'dan eski veride "veri gecikiyor" yazar ve gösterge %60 opaklığa iner. Fiyatlar dakikada en az bir kez beklendiği için bu süre beklenenin iki katı. Geçici; widget soluklaşma süresi kararlaşınca güncellenecek.
  - Piyasa kapalıyken "piyasa kapalı" yazar.
- **Nabız:**
  - Yalnızca göstergenin ışık dönüşü oynar; prototipteki fiyat parlaması, ▲/▼ ve grafik uç halkası eklenmedi (önceki karar).
  - İki nabız arası en az: Sakin 4 sn, Normal 1,8 sn, Hareketli 0,8 sn. Prototipin ortalama nabız sıklığından türetildi; geçici değerler.
- **Fiyat ve yüzde biçimi:** "₺4.215,30", "+%0,12".
  - Döviz ve çapraz kurlar 4, diğerleri 2 ondalıkla gösterilir; 1'in altındaki fiyatlar 4 ondalıkla.
- **Paylaşım kartı:**
  - Kart ekranın ortasına yerleşir; prototipte 844 px'lik ekranda üstten 150 px'teydi.
  - Paylaş düğmesi kartı gölgesiz PNG olarak paylaşım sayfasına verir.
- **Detayda varlık değiştirme:** Kaydırınca yeni varlığın göstergesi doğrudan gösterilir, durum değişimi efekti oynamaz.
- **Düğmeler:** "Alarm kur" yeni alarm ekranını, "Uygulamada aç" varlık ayarlarını, klasör ayarı widget ayarlarını açar.

## Uygulama ekranları ve alarmlar (Faz 5, 2026-10-27)
- **Ekranlar:** Panel, Varlık ayarları, Varlık ekle, Çapraz kur, Widget ayarları, Alarmlar, Yeni alarm ve Ayarlar Compose ile yazıldı. Sekmeler Panel / Alarmlar / Ayarlar.
  - Ekranlar arası geçişte efekt yok; tasarımda tanımlı değil. Yeni efekt eklemeden önce sorulacak.
  - Yazılar sp ile verilir, sistem yazı boyutuna uyar. Ölçüler tasarımdaki px değerlerinin dp karşılığıdır.
  - Panel açıkken anlık görüntü dakikada bir yenilenir; uygulamaya her dönüşte cihaz kaydı da gönderilir.
- **Kaydetme:** Her değişiklik önce cihaza kaydedilir, 600 ms sonra backend'e gönderilir. İnternet yokken ekranlar çalışır, sonraki eşitleme gönderir.
- **Widget ayarı:** Tüm Damla widget'ları tek bir ayarı paylaşır (stil, boyut, açılış, sıra). Widget başına ayrı ayar sonraki sürüme kaldı.
- **Yeni varlık aralıkları:** Varsayılan Damla 1 saat, Üst halka 1 gün, Alt halka 1 hafta (tasarımdaki başlangıç seçimi).
- **Tema:** Seçim kaydedilir ama yalnızca koyu tema tasarlandığı için uygulama koyu kalır (docs/07, docs/09).
- **Alarmlar:**
  - Backend alarmı değerlendirir ve yalnızca hangi alarmın, hangi koşullarla çaldığını gönderir. Bildirim cümlesini cihaz, kendi kayıtlı alarmından Türkçe yazar. Böylece metin tek yerde (`:alarms`) üretilir ve test edilir.
  - Alarm kartındaki kısa satır ("Fiyat ₺4.300'ün üzerine çıkınca") ve tür adı ("Fiyat alarmı", karışık koşullarda "Birleşik alarm") da aynı modülde üretilir.
  - "Sesli uyarı" kapalıysa bildirim sessiz kanaldan gelir. Kanallar: "Alarmlar" ve "Sessiz alarmlar".
  - "Sessiz saatlerde bekle": 23.00–08.00 arası gelen bildirim sabah 08.00'de gösterilir. Aynı gece aynı alarm yeniden çalarsa yalnızca sonuncusu bekler. Saatler geçici; tasarımda tanımlı değil.
  - Bildirime dokununca Alarmlar sekmesi açılır; çalan her alarm Geçmiş'e yazılır (en çok 50).
  - Bildirim izni (Android 13+) ilk alarm kaydedilirken istenir.
- **Tasarlanmamış durumlar (öneri, onaya sunulur):**
  - **İlk açılış:** Kısa tanıtım, üç adımlık widget ekleme rehberi ve başlatıcı destekliyorsa "Widget'ı ana ekrana ekle" düğmesi (`requestPinAppWidget`). Widget'tan gelen kullanıcıya gösterilmez.
  - **İnternet yok:** Turuncu çerçeveli şerit, "Tekrar dene" bağlantısı; son veriler gösterilmeye devam eder.
  - **Veri gelmedi:** Satırda "Veri bekleniyor", gösterge yeri boş kalır (durum uydurulmaz).
  - **Veri gecikiyor:** Satırda "veri gecikiyor", gösterge %60 opaklıkta.
  - **Kaynak hatası:** Backend yanıt veriyor ama piyasası açık tüm varlıklar gecikmişse şerit "Veri kaynağına ulaşılamıyor" der.
  - **Takip listesi boş / alarm yok:** Gri yuvarlak damla, kısa cümle ve "Varlık ekle" ya da yeni alarm düğmesiyle boş kart.

## Kalıcı bildirim ve dokunsal dil (Faz 6, 2026-10-27)
- **Kalıcı bildirim:** `DecoratedCustomViewStyle` ile özel içerik; foreground service yok (Platform kararları).
  - Başlığı sistem çizer: Damla simgesi, "Damla", tazelik ("canlı", "veri gecikiyor", "piyasa kapalı", "duraklatıldı") ve son güncelleme saati. Tasarımdaki canlı nokta, 460 ms açılma geçişi ve 180° dönen ok sistemin kendi davranışıdır; uygulama bunları değiştiremez.
  - Daraltılmış: 3 varlık yan yana (30 dp damla, kısa ad, renkli durum kelimesi). Genişletilmiş: her varlık bir satır (40 dp damla, ad, fiyat · durum, üç parçanın 22×6 renkli çizgisi).
  - "Uygulamayı aç / Düzenle / Duraklat" düğmeleri bildirim eylemleridir; sistemin düğme stilini alır.
  - Ad ve fiyat metinleri sistem bildirim temasının renklerini kullanır, açık ve koyu panelde okunur. Durum kelimeleri parlak tonlardadır; açık panelde sarının kontrastı düşüktür (bilinen sınır).
  - Durum çubuğu simgesi ilk varlığın damla şeklidir: yukarı, aşağı, yuvarlak, yatay.
  - Kilit ekranında içerik gizliyse yalnızca logosuz şekiller görünür.
  - Cihazdaki anlık görüntü değişince yeniden çizilir (push, 15 dk'lık eşitleme, açık uygulamanın dakikalık yenilemesi).
  - "Duraklat" bildirimi o anki haliyle dondurur; başlıkta "duraklatıldı" yazar.
  - Android 14+ kullanıcıya kalıcı bildirimi kaydırıp kapatma hakkı verir; kapatılınca ayar da kapanır.
- **Ayarlar'da "Kalıcı bildirim" bölümü (tasarlanmamış, öneri):**
  - "Bildirim çubuğunda göster" anahtarı; varsayılan kapalı. Açılırken bildirim izni istenir.
  - "Varlıklar" satırı alttan açılan listeyle en fazla üç varlık seçtirir; seçim sırası bildirimdeki sıradır. Hiçbiri seçilmezse widget'ın ilk üç varlığı görünür.
- **Çelişki:** Tasarımda "Düzenle" widget ayarlarını (`KlasorIcerik`) açıyor; belge varlıkların Ayarlar'dan seçildiğini söylüyor. Belge esas alındı: "Düzenle" Ayarlar'ı açar.
- **Dokunsal dil:**
  - Desenler `docs/05`'teki milisaniye ve genlik değerleriyle `:gauge` modülündedir (`Haptic`); saat de aynılarını kullanacak.
  - Genlik kontrolü olmayan cihazlarda yedek ritim: yükseliş kısa-uzun (20/60 ms), düşüş uzun-kısa (60/20 ms); diğerleri aynı süreler.
  - Titreşen olaylar: damla durum değişimi (halkalar titremez), piyasa kapanışı ve alarmlar. Durum alarmı hedef durumun ritmiyle, fiyat ve yüzde alarmı üç kısa vuruşla, dalga alarmı tek uzun vuruşla titrer. "Sesli uyarı" kapalı alarm titremez.
  - Hiçbiri çalmaz: Titreşim kapalıyken, 23.00–08.00 arası ve Rahatsız Etme açıkken. Aynı anda gelen olaylar için 1,5 sn içinde tek ritim çalar.
  - Android, arka plandaki uygulamanın titreşimini yalnızca bildirim, zil ya da alarm kullanımıyla çalar (resmi `VibratorManager` belgesi). Titreşimler bildirim kullanımıyla çalınır; alarm kanalları kendi titreşimini kapatır, ritim uygulamadan gelir.
  - Piyasa kapanışını cihazın bilebilmesi için backend'in `refresh` mesajına `asset` ve `market` (`open` / `closed`) alanları eklendi (`docs/03` sözleşmesine ek).

## Wear OS (Faz 7, 2026-10-27)
- **Modül:** `android/wear`, telefonla aynı `applicationId` (Data Layer API bunu ister). Telefonun cihaz deposunu, API istemcisini ve gösterge çizimini paylaşır. Bağımsız çalışabilir (`standalone`): telefon yokken varsayılan liste ya da telefonun en son gönderdiği liste kullanılır.
- **Sürümler** resmi sürüm sayfalarından: Tiles 1.6.2, ProtoLayout 1.4.2, Watchface (komplikasyon) 1.3.0, Wearable SDK 20.0.1, wear-phone-interactions 1.1.1, wear-remote-interactions 1.2.0.
- **Saatin üç varlığı:** Kalıcı bildirimin seçimi; seçim yoksa widget'ın ilk üçü. Saatte bu üçlü cihaz deposunun widget sırası alanında tutulur (saatte widget yok).
- **Veri akışı:**
  - Telefon, gösterdiği durumu (liste, ayarlar, anlık görüntü, üç varlık) her değişimde bir Data Layer veri öğesi olarak yazar; Google Play hizmetleri saate ulaşınca teslim eder.
  - Damla durum değişimi ve alarmlar saate mesaj olarak gider (yalnızca Damla uygulaması kurulu, erişilebilir saatlere).
  - Saat, telefon yokken 15 dakikada bir ve uygulama açılınca anlık görüntüyü doğrudan backend'den alır (Wi-Fi/LTE). Olaylar (titreşim, uyarı) için telefon gerekir; saatin kendi push kaydı sonraki iş.
- **Kadran komplikasyonları:** "1., 2., 3. varlık" adlı üç ayrı kaynak. SMALL_IMAGE göstergenin kendisini, ortam (ambient) modunda beyaz şekli verir; yalnızca MONOCHROMATIC_IMAGE alan kadranlara şekil gider. İlk varlık 48, diğerleri 36 dp çizilir. Dokununca saat uygulaması o varlıkta açılır. Kadran düzenleyicisindeki önizleme logosuz, verisiz bir damladır.
- **Kutucuk:** Tasarımdaki 300 px'lik yerleşim ekran genişliğine ölçeklenir. Göstergeler PNG olarak gömülür. Tazelik etiketi dakikada bir yenilenir; bir piyasa kapalıysa "kısmen kapalı" yazar. Durum kelimesinin 600 kalınlığı ProtoLayout'ta olmadığı için 500 (medium) kullanıldı.
- **Uygulama:** Tek varlık; taç ya da ekranın sağına/soluna dokunma varlığı değiştirir. Açılış 420 ms, nokta ve renk geçişleri tasarımdaki sürelerle.
- **Uyarı:** Standart bildirim (Platform kararları), olayın ritmiyle titrer; dokununca uyarı ekranı açılır. "Telefonda aç" telefonda varlığı ya da Alarmlar'ı açar (`damla://asset/{id}`, `damla://alarms`). Arka plandaki renkli ışık Android 12 öncesi saatlerde (API 30) bulanıklaşmaz.
- **Çift bildirim önlemi:** Telefondaki alarm bildirimi etiketlidir ve saat uygulaması bu etiketi saate köprülemez; kalıcı bildirim hiç köprülenmez.
- **Kabul:** Gerçek saat ve telefonla denenmesi gerekiyor (Data Layer eşleşmesi aynı imzayı ister).

## Bilgisayar eşlikçisi (Faz 8, 2026-10-27)
- **Teknoloji:** Tauri 2.12 (crate 2.12.5, CLI ve API 2.12.4), ön yüz TypeScript + Vite. Kod `desktop/` altında.
- **Damla çizimi:** Masaüstü için ayrı çizici yazılmadı. `:gauge` modülündeki Kotlin çizici `:gauge-js` modülüyle JavaScript'e derlenir; böylece damlayı her yüzeyde aynı kod çizer. 42 referans vakanın 42'sinde JS çizimi JVM çizimiyle aynı; CI her derlemede bunu denetler.
- **Eşitleme (tasarlanmamış, öneri):** Hesap sistemi henüz yok.
  - Telefon Ayarlar → "Bilgisayarla eşitle" ile 10 dakika geçerli, tek kullanımlık 6 karakterlik bir kod alır.
  - Masaüstü panelde "Telefonla eşitle" ile kodu yazar. Kod telefonun cihaz kimliğine çözülür; masaüstü bundan sonra telefonun takip listesini, widget sırasını ve özel adları okur.
  - Eşitlenmeden önce tasarımdaki varsayılan liste gösterilir.
  - Backend'e eklenenler: `GET /v1/devices/{id}` (push token'ı olmadan), `POST /v1/devices/{id}/pairing`, `POST /v1/pairings/{code}`, cihaz ayarlarında `order` (widget sırası). Masaüstü web görünümünden çağırabilsin diye API'ye CORS eklendi (API'de çerez ya da kimlik bilgisi yok).
- **Görünenler:**
  - Tepside her varlık için bir simge, en çok 3; her biri logosuz damla, ipucu "Altın, yükselişte". Windows tepsi simgeleri kare olduğu için tasarımdaki tek düğmedeki üç damla üç ayrı simgeye bölündü.
  - Panel 4 varlık gösterir (widget'ın Akış'ı gibi); tepsi ve şerit ilk üçü.
  - Fiyat nabzı "Nabız: Normal" değeriyle sınırlı (1,8 sn); telefonun nabız ayarı backend'de tutulmadığı için. "Piyasa kapalıyken gri göster" de aynı nedenle varsayılan (açık).
- **Tasarımdan sapmalar:**
  - Saydam pencere arkasındaki masaüstünü bulanıklaştıramaz; `backdrop-filter` bulanıklığı yok. Tasarımdaki yarı saydam tonlar ve gölgeler aynen kullanıldı.
  - Bildirimdeki "Telefonda ve saatte de bildirildi" satırı yalnızca telefonla eşitliyken görünür (o zaman telefon aynı push'u almıştır). Saatin gerçekten bildirdiği masaüstünden doğrulanamaz.
  - Durum değişimi, art arda iki anlık görüntü (dakikada bir) karşılaştırılarak bulunur; telefondaki push'tan en çok bir dakika sonra gelebilir.
- **Uygulama simgesi (tasarlanmamış):** Paylaşılan çiziciyle çizilmiş, logosuz yükseliş damlası.
- **Derleme:** CI Windows'ta uygulamayı (`tauri build --no-bundle`) derler. macOS derlemesi bir Mac'te `npx tauri build` ile yapılır; CI'da macOS işi yok (maliyet).
- **Gerçek cihazda denenmesi gerekenler:** Bildirim penceresinin odak çalmadığı, tepsi tıklamasında panelin konumu, şeridin kenara yaslanması.

## Günün özeti (2026-10-28)
- **Ne sayılır:** "Bugün N damlan şekil değiştirdi" cümlesindeki N, son özetten bu yana damlası (kısa aralık) en az bir kez şekil değiştiren varlık sayısı. Halka değişimleri ve piyasa açılış/kapanışları sayılmaz.
- **Ne zaman:** Telefonun saat diliminde seçilen saatte (varsayılan 20:00), günde bir kez. Kapsadığı süre bir önceki özetin saatinden bu yana 24 saat. Sunucu o saatte kapalıysa iki saat içinde gönderilir, sonra o günün özeti atlanır.
- **Sessiz gün:** Hiç değişim yoksa bildirim gitmez.
- **Kayıt:** Her damla değişimi o andaki halka durumlarıyla birlikte saklanır (Postgres'te `drop_moments`, 2 gün). Gönderilen günler `digests_sent` tablosunda tutulur; sunucu yeniden başlasa da aynı özet iki kez gitmez. Özet gönderilmeden önce işaretlenir: bir özet kaybolabilir ama iki kez gelmez.
- **Mesaj:** FCM veri mesajı, yüksek öncelik (görünür bildirim üretir), 2 saat ömürlü. En son 16 an taşınır (FCM'in 4 KB sınırı).
- **Yapay zeka (2026-10-28, kullanıcı kararı: şimdilik kapalı):** Kod duruyor ama varsayılan kapalı; özet cümlesini telefon kendi şablonundan kurar. Açmak için sunucuya `DAMLA_DIGEST_AI=on` ve `ANTHROPIC_API_KEY` verilir. Açıldığında: özet cümlesini Claude yazar (`claude-opus-5-5`, düşük çaba, Anthropic'in reddedilen istekler için önerdiği yedek modelle). Yalnızca o günün anları verilir; fiyat, yüzde, tutar, gerekçe, öneri ve tahmin yasak. Cevapta para birimi işareti, yüzde ya da anlarda geçmeyen bir sayı varsa cümle atılır ve telefon kendi şablonunu kullanır ("Gram altın düşüşte, USD/TRY yükselişte."). `ANTHROPIC_API_KEY` yoksa da şablon kullanılır.
- **Başlık ve şablon telefonda:** Bildirim başlığı ve yedek cümle `strings.xml`'den kurulur; sunucu yalnızca sayıyı, anları ve varsa Claude'un cümlesini gönderir.
- **Telefonda (tasarımda yok, öneri):**
  - Ayarlar'da "Günün özeti" bölümü: "Akşam özeti" anahtarı (varsayılan açık) ve saat seçimi 18:00 / 20:00 / 22:00. Üçü de sessiz saatlerin (23.00–08.00) dışında. Değişince sunucuya hemen bildirilir.
  - Bildirim kendi kanalında ("Günün özeti", varsayılan önem), titreşim deseni yok. Sessiz saatlerde gelirse gösterilmez, yalnızca saklanır. Yeni özet dünkünün yerine geçer.
  - Bildirime dokununca "Günün özeti" ekranı açılır: tarih, "Bugün N damlan şekil değiştirdi", cümle ve anların listesi (saat, damla, ad, "Sabit → Yükselişte").
  - Tekrar: Ekran açılınca (600 ms, `dur.open`) anlar sırayla oynar. Her damla eski şeklinden yenisine göstergenin kendi durum değişimi geçişiyle döner (docs/04); iki an arası 1300 ms, yani geçişin en uzun parçası olan ışık yayılımı kadar. Yeni bir efekt eklenmedi. "Tekrar oynat" baştan oynatır. Animasyonları azalt açıksa yeni şekiller hemen gösterilir.

## Açık konular (ilgili fazdan önce sorulacak)
- **Faz 2:**
  - Ons altın için kaynak (Trunçgil'de 0 geliyor).
  - Sunucu yeni açıldığında döviz ve maden için geçmiş yok; uzun aralıkların ve grafiğin dolması günler sürer. Geçmiş kaynağı (EVDS anahtarı ya da başka bir kaynak) gerekiyor.
  - Yedek kaynaklar: kripto ve döviz/maden için hangi bağımsız kaynaklar kullanılacak?
  - Backend'in barındırılacağı yer ve Firebase projesi (gerçek cihaza push kriteri buna bağlı).
  - Kaynakların ticari kullanım koşulları.
  - Widget'ın kaç dakika sonra soluklaşacağı.
  - Grafik geçmişinin kaynağı (1 yıllık serbest piyasa altını).
- **Faz 3–4:**
  - Nabız ayarının süre değerleri (şimdilik geçici değerler var).
  - Akış'ta hangi varlıkların gösterileceği (şimdilik widget sırasındaki ilk dört varlık).
  - Detayda varlık kaydırınca çapraz geçiş yapılıp yapılmayacağı (şimdilik doğrudan geçiş).
- **Faz 5:**
  - Sessiz saatlerin aralığı (şimdilik 23.00–08.00).
  - Tasarlanmamış durumlar için önerilerin onayı (yukarıda).
- **Faz 6:**
  - "Piyasa kapandı" titreşimi şimdilik genel Titreşim anahtarına bağlı; `docs/05` ayrı bir anahtar ima ediyor. Ayrı anahtar istenirse eklenir.
  - Desenlerin ayırt edilebilirliği gerçek cihazda denenmeli (kabul kriteri).
- **Faz 7:**
  - Saatin telefon yokken de uyarı alması için kendi push kaydı gerekip gerekmediği.
- **Faz 8:**
  - Hesap sistemi gelince kodla eşitlemenin yerini ne alacak.
  - Masaüstünde alarm bildirimi istenip istenmediği ("alarmlar ortak" yazıyor ama masaüstü alarm göstermiyor).
- **Genel:**
  - Canlı canvas'taki pakette olmayan artboard'ların durumu.
  - Repo adı.
  - applicationId.

## Ortam notu
Bu bulut oturumunda `dl.google.com` ağ politikasınca engelli, bu yüzden Android modülleri burada derlenemez; yalnızca CI derler. Ortam ayarlarındaki ağ erişimi listesine `dl.google.com` eklenirse burada da derlenebilir.

Bilinen uyarı: Kover 0.9.11, Gradle 11'de kaldırılacak `Configuration.setVisible` API'sini kullanıyor. Bizim kodumuzdan değil; Kover güncellenince kalkar.
