// Renders tools/gauge-reference/render-cases.json with the JavaScript build of the gauge (:gauge-js) and
// checks every drawing equals the JVM one (`./gradlew :gauge:renderReference`). Usage:
// node tools/gauge-reference/compare-js.mjs <gauge-js library dir> <render-cases.json> <JVM svg dir>
import fs from 'node:fs';
import path from 'node:path';
const [lib, casesFile, jvmDir] = process.argv.slice(2);
const { DesktopGauge } = await import(path.resolve(lib, 'damla-gauge-js.mjs'));
const shapes = { '#34C759': 'UP', '#FF3B30': 'DOWN', '#FFCC00': 'FLAT', '#FF9500': 'WAVE' };
const badges = { '$': 'usd', 'Ag': 'ag', 'Au': 'au', 'GBP': 'GBP', 'Ξ': 'eth', '€': 'eur', '₿': 'btc' };
const cases = JSON.parse(fs.readFileSync(casesFile, 'utf8')).cases;
let same = 0, diff = [];
for (const c of cases) {
  let p = { ...c.from };
  const args = (q) => [q.tip.toUpperCase(), shapes[q.c1], shapes[q.c2], String(q.kapali) === 'true', Math.trunc(Number(q.donus) / 360)];
  const g = new DesktopGauge(...args(p), false);
  for (const s of c.steps) { p = { ...p, ...s.props }; g.update(...args(p), Number(s.at)); }
  const size = Number(p.size);
  const svg = g.svg(Number(c.t), size, badges[p.logo]);
  const jvm = fs.readFileSync(path.join(jvmDir, c.name + '.svg'), 'utf8');
  // The JVM reference pads the canvas by one size on every side; compare the drawing itself.
  const body = (x) => x.replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
  // Numbers compared by value: the JVM writes 8.0E-4 where JavaScript writes 0.0008.
  const tail = (x) => x.replace(/(<\/g>)+$/, '').replace(/-?\d+(\.\d+)?(E-?\d+)?/g, (n) => String(Number(n)));
  const jvmBody = tail(body(jvm).replace(/<g transform="translate\(\d+ \d+\)">/, ''));
  if (tail(body(svg)) === jvmBody) same++; else diff.push(c.name);
}
console.log(`${same}/${cases.length} drawings equal the JVM ones`, diff.slice(0, 10).join(" "));
if (diff.length) process.exit(1);
