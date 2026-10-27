// Puts the Kotlin painter (SVG from `./gradlew :gauge:renderReference`) next to the prototype in Chromium
// for every case of render-cases.json and measures how many pixels differ.
//
//   ./gradlew :gauge:renderReference && node tools/gauge-reference/compare.js
//
// Writes gauge/build/reference-render/report.html and summary.json.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '../..');
const out = path.join(root, 'gauge/build/reference-render');
const { cases } = JSON.parse(fs.readFileSync(path.join(__dirname, 'render-cases.json'), 'utf8'));
const BACKGROUND = '#1C1C1E';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  const results = [];
  for (const c of cases) {
    const S = c.from.size;
    const H = Math.round(S * 92 / 76);
    const pad = S;
    const W = S + 2 * pad, HH = H + 2 * pad;
    await page.setViewportSize({ width: W, height: HH });

    // The prototype, played to time t.
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:${BACKGROUND};font-family:-apple-system, system-ui, sans-serif"><div id="host" style="position:absolute;left:${pad}px;top:${pad}px"></div></body></html>`);
    await page.addScriptTag({ path: path.join(__dirname, 'gosterge.js') });
    await page.evaluate((c) => {
      const apply = window.mountGosterge(document.getElementById('host'), c.from);
      document.getAnimations().forEach((a) => a.cancel());
      const started = new Map();
      const seek = (t) => document.getAnimations().forEach((a) => {
        if (!started.has(a)) started.set(a, t);
        a.pause();
        a.currentTime = t - started.get(a);
      });
      let props = { ...c.from };
      for (const step of c.steps) {
        seek(step.at);
        props = { ...props, ...step.props };
        apply(props);
        // Registers the transitions and animations this change started at the change's time.
        seek(step.at);
      }
      seek(c.t);
    }, c);
    const prototype = await page.screenshot({ clip: { x: 0, y: 0, width: W, height: HH } });

    // The painter's SVG.
    const svg = fs.readFileSync(path.join(out, `${c.name}.svg`), 'utf8');
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:${BACKGROUND}">${svg}</body></html>`);
    const painter = await page.screenshot({ clip: { x: 0, y: 0, width: W, height: HH } });

    const diff = await page.evaluate(async ({ a, b }) => {
      const load = (src) => new Promise((resolve) => { const img = new Image(); img.onload = () => resolve(img); img.src = src; });
      const [ia, ib] = await Promise.all([load(a), load(b)]);
      const canvas = document.createElement('canvas');
      canvas.width = ia.width; canvas.height = ia.height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(ia, 0, 0); const pa = ctx.getImageData(0, 0, ia.width, ia.height).data;
      ctx.clearRect(0, 0, ia.width, ia.height);
      ctx.drawImage(ib, 0, 0); const pb = ctx.getImageData(0, 0, ia.width, ia.height).data;
      const heat = ctx.createImageData(ia.width, ia.height);
      let sum = 0, max = 0, over = 0, painted = 0;
      for (let i = 0; i < pa.length; i += 4) {
        const d = Math.max(Math.abs(pa[i] - pb[i]), Math.abs(pa[i + 1] - pb[i + 1]), Math.abs(pa[i + 2] - pb[i + 2]));
        const isBackground = pa[i] === 28 && pa[i + 1] === 28 && pa[i + 2] === 30 && pb[i] === 28 && pb[i + 1] === 28 && pb[i + 2] === 30;
        if (!isBackground) { painted++; sum += d; if (d > 24) over++; }
        max = Math.max(max, d);
        heat.data[i] = Math.min(255, d * 4); heat.data[i + 1] = 0; heat.data[i + 2] = 0; heat.data[i + 3] = 255;
      }
      ctx.putImageData(heat, 0, 0);
      return { mean: painted ? sum / painted : 0, max, over, painted, heat: canvas.toDataURL() };
    }, { a: 'data:image/png;base64,' + prototype.toString('base64'), b: 'data:image/png;base64,' + painter.toString('base64') });
    results.push({ name: c.name, size: S, ...diff, prototype: prototype.toString('base64'), painter: painter.toString('base64') });
    console.log(`${c.name.padEnd(28)} mean ${diff.mean.toFixed(2).padStart(6)}  >24: ${String(diff.over).padStart(5)} of ${diff.painted}  max ${diff.max}`);
  }
  await browser.close();
  const rows = results.map((r) => `<tr><td>${r.name}<br><small>mean ${r.mean.toFixed(2)} · ${(100 * r.over / Math.max(1, r.painted)).toFixed(2)}% > 24 · max ${r.max}</small></td>
    <td><img src="data:image/png;base64,${r.prototype}"></td><td><img src="data:image/png;base64,${r.painter}"></td><td><img src="${r.heat}"></td></tr>`).join('\n');
  fs.writeFileSync(path.join(out, 'report.html'), `<!doctype html><meta charset="utf-8"><title>Damla gauge vs prototype</title>
<style>body{font:13px system-ui;background:#111;color:#ddd}td{vertical-align:top;padding:6px}img{image-rendering:pixelated;max-width:360px}</style>
<table><tr><th>case</th><th>prototype</th><th>painter</th><th>difference ×4</th></tr>${rows}</table>`);
  fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify(results.map(({ prototype, painter, heat, ...r }) => r), null, 1));
})();
