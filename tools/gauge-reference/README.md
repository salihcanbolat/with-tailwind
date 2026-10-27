# Gösterge referans araçları

Damla göstergesinin prototiple (`design/Gosterge.dc.html`) birebir aynı davrandığını, prototipi Chromium'da gerçekten oynatarak doğrulayan araçlar.

- `gosterge.js`: Prototipin işaretlemesini ve `renderVals()` betiğini düz bir sayfada yeniden kurar. Animasyonların hepsini tarayıcının kendisi oynatır.
- `capture.js`: 22 senaryoyu (bütün şekil geçişleri, kapanış, halka değişimi, nabız, hız çarpanı, yarıda kesilen geçiş) 25 ms aralıklarla örnekler ve `gauge/src/test/resources/reference/motion.json` dosyasına yazar. `PrototypeReferenceTest` hareket modelini bu değerlerle karşılaştırır; her derlemede çalışır.
- `render-cases.json` ve `compare.js`: Kotlin ressamının SVG çıktısını ve prototipi aynı anlarda ekran görüntüsü olarak alır, piksel farkını ölçer. `gauge/build/reference-render/report.html` dosyasına yan yana bir rapor yazar.

```bash
node tools/gauge-reference/capture.js                                   # referans değerleri yeniden üretir
./gradlew :gauge:renderReference && node tools/gauge-reference/compare.js
```

Playwright ve Chromium gerekir.
