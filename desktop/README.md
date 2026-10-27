# Damla — bilgisayar eşlikçisi

Windows görev çubuğunda ve macOS menü çubuğunda üç minik damla, tıklayınca açılan panel, ekranda sürüklenebilir mini şerit ve durum değişiminde köşeden kayan bildirim (`docs/08`, `design/Masaustu.dc.html`). Tauri 2 ile yazıldı.

## Nasıl çalışır

- Damlayı telefon, saat ve widget'la **aynı kod** çizer: `:gauge` modülündeki Kotlin çizici `:gauge-js` ile JavaScript'e derlenir. `tools/gauge-reference/compare-js.mjs`, JS çizimlerinin JVM çizimleriyle birebir aynı olduğunu CI'da denetler.
- Gizli `core` penceresi backend'i izler: anlık görüntü dakikada bir, canlı fiyatlar SSE ile gelir. Tepsi simgelerini çizer, diğer pencerelere durum gönderir.
- Telefonla eşitleme: telefonda **Ayarlar → Bilgisayarla eşitle** 10 dakika geçerli bir kod gösterir. Panelde "Telefonla eşitle"ye tıklayıp kod yazılınca masaüstü telefonun takip listesini, widget sırasını ve özel adları kullanır. Eşitlenmeden önce varsayılan liste gösterilir.

## Geliştirme

Gerekenler: Node 22, Rust (stable), Java 21. Linux'ta ayrıca `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`, `libayatana-appindicator3-dev`, `librsvg2-dev`.

```bash
cd desktop
npm ci
npm run gauge                                   # Kotlin çiziciyi JS'e derler
VITE_DAMLA_API=http://localhost:8080 npx tauri dev
```

Yerel backend için: `DAMLA_SOURCES=test ./gradlew :backend:run` (sentetik test verisi, `source: "test"`).

Kurulum dosyası: `VITE_DAMLA_API=https://… npx tauri build`. macOS uygulamaları düz `http://` adreslere bağlanamaz (App Transport Security), üretimde backend `https://` olmalı.
