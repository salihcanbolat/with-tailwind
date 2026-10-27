# 08 · Diğer yüzeyler

## Kalıcı bildirim (`design/Bildirim.dc.html`)
- Kaydırılıp silinmeyen, "Devam eden" bölümünde duran bildirim. Kullanıcı Ayarlar'dan açar/kapatır, hangi 3 varlığın görüneceğini seçer.
- **Daraltılmış:** 3 varlık yan yana (30 px damla + kısa ad + renkli durum kelimesi). Başlıkta Damla simgesi, "canlı · N sn önce", genişletme oku.
- **Genişletilmiş:** her varlık bir satır (40 px damla, ad, [FİYAT] · durum, üç parçanın renkli kısa çizgileri) + düğmeler: Uygulamayı aç, Düzenle, Duraklat/Sürdür.
- Geçiş: yükseklik 460 ms `easeStandard`, ok 180° döner.
- **Durum çubuğu simgesi:** Android küçük simgeleri tek renk (beyaz) zorunlu. Simge, öncelikli varlığın damla **şeklini** gösterir (4 vektör: yukarı, aşağı, yuvarlak, yatay). Renk olmadan da okunur.
- Kilit ekranında daraltılmış görünüm; gizlilik ayarı açıksa yalnızca şekiller, adlar gizli.
- Uygulama: `NotificationCompat.DecoratedCustomViewStyle` + daraltılmış/genişletilmiş özel RemoteViews. Güncelleme FCM push ile gelir. Sürekli bildirim için foreground service gerekip gerekmediği ve hangi servis türünün uygun olduğu **güncel Android politikalarından doğrulanmalı**; mümkünse servis olmadan, push ile güncellenen ongoing bildirim tercih edilir.

## Wear OS (`design/Saat.dc.html`)
Prototip, solda saat, sağda olay gönderme paneli ve "Nasıl çalışır" akışıyla mantığı canlı gösterir.
- **Kadran komplikasyonu:** ortada öncelikli varlık (48), yanlarda iki varlık (36). Dokununca saat uygulamasında o varlık açılır. `ComplicationDataSourceService`; desteklenen tür(ler) (SMALL_IMAGE / PHOTO_IMAGE vb.) kadranlara göre doğrulanacak.
- **Kutucuk (Tile):** üç varlık + durum kelimeleri + tazelik etiketi. ProtoLayout.
- **Uygulama:** tek varlık büyük damla (100), ad, durum, üç parçanın çizgileri, sayfa noktaları; döner taç (rotary input) ile varlık değişir.
- **Uyarı ekranı:** durum değişimi veya alarm geldiğinde öne gelir; arkada durum renginde yumuşak ışık, damla, varlık adı, mesaj; Tamam / Telefonda aç.
- **Veri akışı:** 1) backend hesaplar → 2) telefon olayı alır → 3) saate Data Layer API ile iletilir; telefon yoksa saat Wi-Fi/LTE ile backend'den doğrudan alır → 4) kadran, kutucuk, uygulama güncellenir. Titreşim yalnızca önemli olaylarda (bkz. 05).
- Piyasa kapandığında döviz/maden gri olur, kripto renkli kalır.

## Bilgisayar eşlikçisi (`design/Masaustu.dc.html`) — sonraki faz
- Windows görev çubuğu bildirim alanında / macOS menü çubuğunda 3 minik damla (16 px, logosuz).
- Tıklayınca cam panel (340 px): Damla başlığı + tazelik, piyasa havası şeridi, 4 varlık satırı (34 px damla, ad, [FİYAT], durum, üç parça), "Ekranda mini şerit" anahtarı, "Telefonla eşitlendi · alarmlar ortak".
- **Mini şerit:** her zaman üstte duran cam kapsül (3 damla + ad + durum). Sürüklenir, bırakılınca en yakın yatay kenara 420 ms `easeStandard` ile yaslanır.
- Durum değişiminde köşeden kayan masaüstü bildirimi: "USD/TRY yükselişe döndü · Telefonda ve saatte de bildirildi".
- Hesap/eşitleme: telefonla aynı hesap; takip listesi, ayarlar ve alarmlar backend'de tutulur.
- Önerilen teknoloji: Tauri (küçük boyut, Windows + macOS). Faz başlamadan onaylanır.
