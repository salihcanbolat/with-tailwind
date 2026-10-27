// Plays prototype scenarios in Chromium and records every animated value over time, so the Kotlin
// motion model can be tested against the prototype itself.
//
//   node tools/gauge-reference/capture.js
//
// Writes gauge/src/test/resources/reference/motion.json. Needs Playwright with Chromium.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const UP = '#34C759', DOWN = '#FF3B30', FLAT = '#FFCC00', WAVE = '#FF9500';
const SIZE = 120;
const base = { size: SIZE, tip: 'up', c1: UP, c2: DOWN, kapali: false, hiz: 1, logo: 'Au', donus: 0 };

const scenarios = [];
const shapes = ['up', 'down', 'flat', 'wave'];
for (const from of shapes) for (const to of shapes) {
  if (from !== to) scenarios.push({ name: `shape-${from}-${to}`, from: { ...base, tip: from }, steps: [{ at: 0, props: { tip: to } }] });
}
scenarios.push({ name: 'close', from: { ...base, tip: 'wave' }, steps: [{ at: 0, props: { kapali: true } }] });
scenarios.push({ name: 'reopen', from: { ...base, tip: 'flat', kapali: true }, steps: [{ at: 0, props: { kapali: false } }] });
scenarios.push({ name: 'upper-ring', from: base, steps: [{ at: 0, props: { c1: WAVE } }] });
scenarios.push({ name: 'lower-ring', from: base, steps: [{ at: 0, props: { c2: FLAT } }] });
scenarios.push({ name: 'pulse-up', from: base, steps: [{ at: 0, props: { donus: 360 } }] });
scenarios.push({ name: 'pulse-down', from: { ...base, donus: 720 }, steps: [{ at: 0, props: { donus: 360 } }] });
scenarios.push({ name: 'speed-half', from: { ...base, hiz: 0.5 }, steps: [{ at: 0, props: { tip: 'down' } }] });
scenarios.push({ name: 'speed-slow', from: { ...base, hiz: 2.5 }, steps: [{ at: 0, props: { tip: 'wave' } }] });
scenarios.push({ name: 'interrupted', from: base, steps: [{ at: 0, props: { tip: 'down' } }, { at: 300, props: { tip: 'wave', c1: FLAT } }] });
scenarios.push({ name: 'pulse-during-change', from: base, steps: [{ at: 0, props: { tip: 'flat' } }, { at: 200, props: { donus: -360 } }] });

const END = 3200, STEP = 25;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent('<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0"><div id="host"></div></body></html>');
  await page.addScriptTag({ path: path.join(__dirname, 'gosterge.js') });
  const results = [];
  for (const scenario of scenarios) {
    const samples = await page.evaluate(({ scenario, END, STEP }) => {
      const host = document.getElementById('host');
      host.innerHTML = '';
      const apply = window.mountGosterge(host, scenario.from);
      document.getAnimations().forEach((a) => a.cancel());
      let props = { ...scenario.from };
      // Every animation remembers the scenario time it started at, so all of them can be seeked together.
      const started = new Map();
      const seek = (t) => document.getAnimations().forEach((a) => {
        if (!started.has(a)) started.set(a, t);
        a.pause();
        a.currentTime = t - started.get(a);
      });
      const matrix = (el) => {
        const value = getComputedStyle(el).transform;
        if (value === 'none') return [1, 0, 0, 1, 0, 0];
        const m = new DOMMatrix(value);
        return [m.a, m.b, m.c, m.d, m.e, m.f];
      };
      const color = (text) => text.match(/[\d.]+/g).slice(0, 3).map(Number);
      const $ = (id) => document.getElementById(id);
      const read = () => ({
        dropWrap: { m: matrix($('dropWrap')), o: Number(getComputedStyle($('dropWrap')).opacity) },
        circWrap: { m: matrix($('circWrap')), o: Number(getComputedStyle($('circWrap')).opacity) },
        dropSquash: matrix($('dropSquash')),
        circSquash: matrix($('circSquash')),
        fill: color(getComputedStyle($('dropPath')).fill),
        circFill: color(getComputedStyle($('circPath')).fill),
        halo: {
          o: Number(getComputedStyle($('halo')).opacity),
          m: matrix($('halo')),
          color: color(getComputedStyle($('halo')).backgroundColor),
          left: parseFloat(getComputedStyle($('halo')).left),
          top: parseFloat(getComputedStyle($('halo')).top),
        },
        arc1: { color: color(getComputedStyle($('arc1')).stroke), offset: parseFloat(getComputedStyle($('arc1')).strokeDashoffset) },
        arc2: { color: color(getComputedStyle($('arc2')).stroke), offset: parseFloat(getComputedStyle($('arc2')).strokeDashoffset) },
        logoWrap: matrix($('logoWrap')),
        logoColor: color(getComputedStyle($('logoText')).color),
        hl: { o: Number(getComputedStyle($('hl')).opacity), m: matrix($('hl')) },
        swRot: matrix($('swRotD')),
      });
      const out = [];
      let next = 0;
      for (let t = 0; t <= END; t += STEP) {
        while (next < scenario.steps.length && scenario.steps[next].at <= t) {
          seek(scenario.steps[next].at);
          props = { ...props, ...scenario.steps[next].props };
          apply(props);
          next++;
        }
        seek(t);
        out.push({ t, ...read() });
      }
      return out;
    }, { scenario, END, STEP });
    results.push({ name: scenario.name, size: SIZE, from: scenario.from, steps: scenario.steps, samples });
  }
  await browser.close();
  const target = path.join(__dirname, '../../gauge/src/test/resources/reference/motion.json');
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, JSON.stringify({ generatedBy: 'tools/gauge-reference/capture.js', scenarios: results }));
  console.log(`Wrote ${results.length} scenarios to ${path.relative(process.cwd(), target)}`);
})();
