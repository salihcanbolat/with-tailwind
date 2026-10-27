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

## Açık konular (ilgili fazdan önce sorulacak)
- **Faz 2:**
  - Backend'in barındırılacağı yer ve Firebase projesi.
  - Kaynakların ticari kullanım koşulları.
  - Widget'ın kaç dakika sonra soluklaşacağı.
  - Grafik geçmişinin kaynağı (1 yıllık serbest piyasa altını).
- **Faz 3–4:**
  - Grafikte FLAT rengi: #E0B000 mi, #FFCC00 mı?
  - Detay açılışı: dairesel açılım mı, bölüm bölüm açılış mı?
  - Nabız ayarının süre değerleri.
  - Akış'ta hangi varlıkların gösterileceği.
  - Detayda varlık kaydırınca çapraz geçiş yapılıp yapılmayacağı.
- **Genel:**
  - Canlı canvas'taki pakette olmayan artboard'ların durumu.
  - Repo adı.
  - applicationId.

## Ortam notu
Bu bulut oturumunda `dl.google.com` ağ politikasınca engelli, bu yüzden Android modülleri burada derlenemez; yalnızca CI derler. Ortam ayarlarındaki ağ erişimi listesine `dl.google.com` eklenirse burada da derlenebilir.

Bilinen uyarı: Kover 0.9.11, Gradle 11'de kaldırılacak `Configuration.setVisible` API'sini kullanıyor. Bizim kodumuzdan değil; Kover güncellenince kalkar.
