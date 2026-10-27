# 09 · Yol haritası

Her faz sonunda: dur, özetle, kabul kriterlerini işaretle, onay iste.

## Faz 0 — Kurulum
Monorepo: `engine/` (saf Kotlin), `backend/` (Ktor), `android/app`, `android/widget`, `android/wear` (sonra), `desktop/` (sonra). Gradle sürüm kataloğu, lint/format (ktlint), CI (test + build).
**Kabul:** boş modüller derleniyor, CI yeşil, yığın kararları `docs/kararlar.md`'ye yazıldı.

## Faz 1 — Gösterge motoru
`docs/02`'nin tamamı + histerezis + olay üretimi + çapraz kur.
**Kabul:** test tablosu ve sınır durumları geçiyor; %100 dal kapsamı.

## Faz 2 — Backend
Kaynak adaptörleri (önce Binance + Trunçgil, EVDS geçmiş için), 1 dk mumlar, motor çalıştırıcı, `/v1/snapshot`, `/v1/history`, `/v1/assets`, cihaz kaydı, FCM gönderici, piyasa takvimi. Yerel geliştirme için docker-compose (Postgres + Redis).
**Kabul:** gerçek veriyle snapshot dönüyor; bir durum değişiminde test cihazına push geliyor; kaynak kesildiğinde yedeğe geçiliyor.

## Faz 3 — Gösterge bileşeni (Android)
`Damla` Compose bileşeni (`docs/04`) + **önizleme ekranı**: tüm durumlar, boyutlar (16–240), nabız, durum değişimi tetikleyicisi, hız çarpanı.
**Kabul:** prototiple yan yana karşılaştırıldığında şekil, renk, zamanlama ve eğriler aynı; 60 fps; animasyonları azalt modunda doğru davranış.

## Faz 4 — Widget + kaplama
Glance widget (Mini/Geniş/Tek, Kutusuz/Kutulu), push ile güncelleme, kaplama Activity: Akış 2×2, detay sahnesi, grafik, paylaşım kartı.
**Kabul:** `design/Prototip.dc.html` akışı birebir; widget konumundan açılma; boş alana dokunarak geri.

## Faz 5 — Uygulama ekranları
`docs/07`'deki tüm ekranlar + tasarlanmamış durumlar için öneri.
**Kabul:** tüm akışlar çalışıyor; taslak/kaydet akışı; alarm özet cümlesi testli.

## Faz 6 — Kalıcı bildirim + dokunsal dil
**Kabul:** daraltılmış/genişletilmiş görünüm, durum çubuğu şekil simgesi, desenler gerçek cihazda ayırt edilebilir.

## Faz 7 — Wear OS
Komplikasyon, kutucuk, uygulama, uyarı ekranı, Data Layer.

## Faz 8 — Bilgisayar eşlikçisi
Tauri; görev çubuğu/menü çubuğu, panel, mini şerit, bildirim.

## Sonraki dönem
Yapay zeka destekli **Günün özeti** (akşam tek bildirim: "bugün 3 damlan şekil değiştirdi" + o anların kısa tekrarı), iOS, açık tema ekranları, renk körlüğü modu.
