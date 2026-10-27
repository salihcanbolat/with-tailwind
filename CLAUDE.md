# Damla — proje hafızası

Damla; döviz, altın, gümüş ve kripto varlıkların **yönünü** tek bakışta gösteren, Android ana ekran widget'ı merkezli bir takip uygulamasıdır. Hedef kitle: gün boyu telefon ve bilgisayar başında olan yatırımcılar, borsacılar, kriptocular. Ürünün farkı **görsel dil (damla göstergesi) ve premium hareket tasarımıdır**; bu ikisinden taviz verilmez.

## Önce oku
1. `docs/01-urun.md` — ürün, terminoloji, kapsam
2. `docs/02-gosterge-motoru.md` — durum hesaplama kuralları (TEK DOĞRU KAYNAK)
3. `docs/03-veri-ve-backend.md` — veri kaynakları, API sözleşmesi, push
4. `docs/04-gosterge-cizimi.md` — damlanın geometrisi ve animasyonları (piksel/ms düzeyinde)
5. `docs/05-hareket-ve-dokunsal.md` — hareket token'ları, titreşim desenleri
6. `docs/06-widget-ve-akis.md` — widget, Akış açılımı, detay sahnesi
7. `docs/07-ekranlar.md` — uygulama içi ekranlar
8. `docs/08-yuzeyler.md` — kalıcı bildirim, Wear OS, bilgisayar eşlikçisi
9. `docs/09-yol-haritasi.md` — fazlar ve kabul kriterleri
10. `design/README.md` — tasarım dosyalarını nasıl okuyacağın

## Sabit kurallar
- **Terminoloji değişmez:** Damla (kısa aralık), Üst halka (orta aralık), Alt halka (uzun aralık). Kodda: `drop`, `upperRing`, `lowerRing`.
- **Durumlar:** `UP` (yeşil, yukarı damla), `DOWN` (kırmızı, aşağı damla), `FLAT` (sarı, yuvarlak), `WAVE` (turuncu, sivri ucu sağa yatay damla), `CLOSED` (gri, yalnızca piyasa kapalıyken).
- Durum hesaplama yalnızca `docs/02` kurallarıyla, tek bir saf modülde yapılır; UI hiçbir zaman kendi başına durum türetmez.
- Tasarım dosyalarındaki sayısal değerler (renk, ölçü, süre, eğri) **birebir** kullanılır. Bir değer belgeyle tasarım dosyası arasında çelişirse belgeyi esas al ve çelişkiyi bana bildir.
- Arayüz metinleri **Türkçe**; kod, değişken, commit mesajları **İngilizce**. Metinler `strings.xml` içinde, sabit yazılmaz.
- Tasarımlardaki `[FİYAT]`, `[%]`, `[DEĞER]`, `[KAYNAK]` yer tutucudur; gerçek veriyle doldurulur, uydurma sayı yazılmaz.
- Animasyonlar: abartı yok. Her hareketin bir anlamı var (bkz. `docs/05`). Yeni efekt eklemeden önce sor.
- Erişilebilirlik: her damlanın içerik açıklaması ("Altın, yükselişte") olur; sistemde "animasyonları azalt" açıksa nabız ve vurgu efektleri kapanır, durum yine şekil+renkle okunur.
- Her fazın sonunda dur, yapılanı özetle, kabul kriterlerini kontrol et ve onay iste.
- Emin olmadığın platform kısıtlarını (Android widget, bildirim, foreground service, Wear OS politikaları) güncel resmi belgeden doğrula; varsayımla ilerleme.

## Teknik yığın (önerilen, Faz 0'da onaylanacak)
- Android: Kotlin, Jetpack Compose, Glance (widget), Hilt, Room, DataStore, WorkManager, Firebase Cloud Messaging, Ktor client
- Ortak hesaplama motoru: saf Kotlin modülü `:engine` (backend ve uygulama aynı kodu kullanır)
- Backend: Kotlin + Ktor, PostgreSQL (fiyat geçmişi), Redis (son durum/önbellek)
- Wear OS: Compose for Wear OS, Tiles (ProtoLayout), Complications, Data Layer API
- Bilgisayar: Tauri (Windows görev çubuğu + macOS menü çubuğu) — sonraki faz
- iOS: v1 kapsamı dışında
