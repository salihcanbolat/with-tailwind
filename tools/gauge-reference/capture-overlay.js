// Plays the overlay of design/Prototip.dc.html (its own renderVals(), see prototip-logic.js) in Chromium and
// records every animated value of the flow, detail and share scenes over time.
//
//   node tools/gauge-reference/capture-overlay.js
//
// Writes gauge/src/test/resources/reference/overlay.json.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const base = { nabiz: 'Kapalı', acilis: 'Akış', stil: 'Kutusuz', boyut: 'Tek varlık', taraf: 'sağ', hiz: 1 };
const scenarios = [
  { name: 'flow-open', props: base, steps: [{ at: 0, go: 1 }] },
  { name: 'flow-detail', props: base, start: { stage: 1, from: 0 }, steps: [{ at: 0, go: 2, sel: 2 }] },
  { name: 'flow-back', props: base, start: { stage: 2, from: 1, sel: 2 }, steps: [{ at: 0, go: 1 }] },
  { name: 'flow-close', props: base, start: { stage: 1, from: 0 }, steps: [{ at: 0, go: 0 }] },
  { name: 'detail-close-all', props: base, start: { stage: 2, from: 1, sel: 1 }, steps: [{ at: 0, go: 1 }, { at: 440, go: 0 }] },
  { name: 'detail-range', props: base, start: { stage: 2, from: 1, sel: 0 }, steps: [{ at: 0, rng: 3 }] },
  { name: 'detail-swipe', props: base, start: { stage: 2, from: 1, sel: 0 }, steps: [{ at: 0, sel: 3 }, { at: 700, sel: 1 }] },
  { name: 'detail-share', props: base, start: { stage: 2, from: 1, sel: 3 }, steps: [{ at: 0, share: true }, { at: 900, share: false }] },
  { name: 'mini-left-open', props: { ...base, boyut: '1 yuva · 3 damla', taraf: 'sol' }, steps: [{ at: 0, go: 1 }, { at: 900, go: 2, sel: 1 }] },
  { name: 'mini2-boxed-open', props: { ...base, boyut: '1 yuva · 2 damla', stil: 'Kutulu' }, steps: [{ at: 0, go: 1 }] },
  { name: 'wide-boxed-detail', props: { ...base, boyut: '3×1', stil: 'Kutulu' }, steps: [{ at: 0, go: 1 }, { at: 800, go: 2, sel: 3 }, { at: 2200, go: 1 }] },
  { name: 'wide2-close', props: { ...base, boyut: '2×1' }, start: { stage: 1, from: 0 }, steps: [{ at: 0, go: 0 }] },
  { name: 'folder-open', props: { ...base, acilis: 'Klasör' }, steps: [{ at: 0, go: 1 }, { at: 900, go: 2, sel: 2 }] },
  { name: 'speed', props: { ...base, hiz: 1.5 }, steps: [{ at: 0, go: 1 }, { at: 1100, go: 2, sel: 1 }] },
];
const END = 2800, STEP = 20;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent('<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0"><div id="host"></div></body></html>');
  await page.addScriptTag({ path: path.join(__dirname, 'prototip-logic.js') });
  const results = [];
  for (const scenario of scenarios) {
    const samples = await page.evaluate(({ scenario, END, STEP }) => {
      const host = document.getElementById('host');
      host.innerHTML = '';
      const svg = (tag) => document.createElementNS('http://www.w3.org/2000/svg', tag);
      const el = (tag) => document.createElement(tag);
      const make = (id, node, parent = host) => { node.id = id; parent.appendChild(node); return node; };
      ['home', 'dim', 'box', 'hero', 'fdDim', 'shDim', 'shBox', 'rng', 'wLayer', 'hdr', 'clip'].forEach((id) => make(id, el('div')));
      for (let i = 0; i < 4; i++) { make('cell' + i, el('div')); make('label' + i, el('div')); make('dot' + i, el('span')); }
      for (const n of [0, 2, 3, 4, 5]) make('sec' + n, el('div'));
      for (let j = 0; j < 3; j++) { make('anno' + j, el('div')); make('word' + j, el('span')); }
      const canvas = make('svg', svg('svg'));
      for (let k = 0; k < 3; k++) { make('line' + k, svg('path'), canvas); make('adot' + k, svg('circle'), canvas); }
      make('chartLine', svg('path'), canvas); make('chartArea', svg('path'), canvas); make('endDot', svg('circle'), canvas);

      const logic = new window.PrototypeLogic({ ...scenario.props });
      if (scenario.start) logic.state = { ...scenario.start };
      const set = (id, css) => document.getElementById(id).setAttribute('style', css);
      let vals;
      const render = () => {
        vals = logic.renderVals();
        set('home', 'position: absolute; inset: 0; ' + vals.homeStyle);
        set('dim', 'position: absolute; inset: 0; background: rgba(0,0,0,0.3); ' + vals.dimStyle);
        set('box', 'position: absolute; ' + vals.boxStyle);
        set('wLayer', 'position: absolute; ' + vals.wLayer);
        set('hdr', 'position: absolute; ' + vals.hdrStyle);
        set('clip', 'position: absolute; width: 280px; height: ' + vals.cardH + 'px; ' + vals.cardClip);
        vals.assets.forEach((a, i) => { set('cell' + i, a.cellStyle); set('label' + i, a.labelStyle); });
        vals.dots.forEach((o, i) => set('dot' + i, 'display: block; height: 5px; transition: width 300ms cubic-bezier(0.32, 0.72, 0, 1), opacity 300ms ease; ' + o.style));
        set('hero', 'position: absolute; left: 0; top: 0; transform-origin: 0 0; ' + vals.heroStyle);
        set('fdDim', 'position: absolute; inset: 0; ' + vals.fdDim);
        for (const n of [0, 2, 3, 4, 5]) set('sec' + n, 'position: absolute; ' + vals['sec' + n]);
        vals.anno.forEach((n, j) => { set('anno' + j, 'position: absolute; left: 102px; ' + n.pos); set('word' + j, 'transition: color 300ms ease; ' + n.wordStyle); });
        for (let k = 0; k < 3; k++) { set('line' + k, vals['al' + k]); set('adot' + k, vals['ad' + k]); }
        set('chartLine', vals.lineStyle); set('chartArea', vals.areaStyle); set('endDot', vals.endDot);
        set('rng', 'position: absolute; ' + vals.rngMove);
        set('shDim', 'position: absolute; inset: 0; ' + vals.shDim);
        set('shBox', 'position: absolute; ' + vals.shBox);
      };
      render();
      document.getAnimations().forEach((a) => a.cancel());
      const started = new Map();
      const seek = (t) => document.getAnimations().forEach((a) => {
        if (!started.has(a)) started.set(a, t);
        a.pause();
        a.currentTime = t - started.get(a);
      });
      const cs = (id) => getComputedStyle(document.getElementById(id));
      const matrix = (id) => {
        const v = cs(id).transform;
        if (v === 'none') return [1, 0, 0, 1, 0, 0];
        const m = new DOMMatrix(v);
        return [m.a, m.b, m.c, m.d, m.e, m.f];
      };
      const nums = (text) => (text.match(/-?[\d.]+(e-?\d+)?/g) || []).map(Number);
      const blur = (filter) => { const m = /blur\(([\d.]+)px\)/.exec(filter); return m ? Number(m[1]) : 0; };
      const shadowAlpha = (filter) => { const m = /drop-shadow\(rgba\([\d.]+, [\d.]+, [\d.]+, ([\d.]+)\)/.exec(filter); if (m) return Number(m[1]); return /drop-shadow\(rgb\(/.test(filter) ? 1 : 0; };
      const color = (text) => nums(text).slice(0, 3);
      const curvePoints = (d) => { const n = nums(d); const out = []; for (let i = 7; i < n.length; i += 6) out.push(n[i]); return [n[1]].concat(out); };
      const chartTarget = () => ({
        points: curvePoints(vals.chartLine),
        color: /stroke: (#[0-9A-Fa-f]{6})/.exec(vals.lineStyle)[1],
        words: vals.anno.map((n) => /color: (#[0-9A-Fa-f]{6})/.exec(n.wordStyle)[1]),
      });
      const read = () => ({
        home: { blur: blur(cs('home').filter), scale: matrix('home')[0] },
        dim: Number(cs('dim').opacity),
        detailDim: Number(cs('fdDim').opacity),
        box: {
          alpha: Number(cs('box').opacity), scale: matrix('box')[0], origin: cs('box').transformOrigin,
          left: parseFloat(cs('box').left), top: parseFloat(cs('box').top), width: parseFloat(cs('box').width), height: parseFloat(cs('box').height),
          background: nums(cs('box').backgroundColor), shadow: nums(cs('box').boxShadow),
        },
        widgetLayer: Number(cs('wLayer').opacity),
        header: { alpha: Number(cs('hdr').opacity), y: matrix('hdr')[5] },
        clip: { circle: nums(cs('clip').clipPath), alpha: Number(cs('clip').opacity) },
        cells: [0, 1, 2, 3].map((i) => ({ m: matrix('cell' + i), alpha: Number(cs('cell' + i).opacity), blur: blur(cs('cell' + i).filter), shadow: shadowAlpha(cs('cell' + i).filter), label: Number(cs('label' + i).opacity) })),
        hero: { m: matrix('hero'), alpha: Number(cs('hero').opacity) },
        sections: [0, 2, 3, 4, 5].map((n) => ({ n, alpha: Number(cs('sec' + n).opacity), y: matrix('sec' + n)[5], blur: blur(cs('sec' + n).filter) })),
        annos: [0, 1, 2].map((j) => ({ alpha: Number(cs('anno' + j).opacity), x: matrix('anno' + j)[4], blur: blur(cs('anno' + j).filter), color: color(cs('word' + j).color) })),
        lines: [0, 1, 2].map((k) => ({ offset: parseFloat(cs('line' + k).strokeDashoffset), dot: Number(cs('adot' + k).opacity) })),
        chart: {
          offset: parseFloat(cs('chartLine').strokeDashoffset),
          color: color(cs('chartLine').stroke),
          points: curvePoints(cs('chartLine').d),
          area: Number(cs('chartArea').fillOpacity),
          end: Number(cs('endDot').opacity),
          endColor: color(cs('endDot').fill),
        },
        rng: matrix('rng')[4],
        dots: [0, 1, 2, 3].map((i) => ({ width: parseFloat(cs('dot' + i).width), alpha: Number(cs('dot' + i).opacity) })),
        share: { dim: Number(cs('shDim').opacity), alpha: Number(cs('shBox').opacity), y: matrix('shBox')[5], scale: matrix('shBox')[0] },
      });
      const out = { initial: chartTarget(), steps: [], samples: [] };
      let next = 0;
      for (let t = 0; t <= END; t += STEP) {
        while (next < scenario.steps.length && scenario.steps[next].at <= t) {
          const step = scenario.steps[next];
          seek(step.at);
          const st = logic.state || {};
          const patch = {};
          if (step.go !== undefined) Object.assign(patch, { stage: step.go, from: st.stage || 0, scrub: null });
          if (step.sel !== undefined) Object.assign(patch, { sel: step.sel, scrub: null });
          if (step.rng !== undefined) Object.assign(patch, { rng: step.rng, scrub: null });
          if (step.share !== undefined) Object.assign(patch, { share: step.share });
          logic.state = Object.assign({}, st, patch);
          render();
          seek(step.at);
          out.steps.push({ ...step, chart: chartTarget() });
          next++;
        }
        seek(t);
        out.samples.push({ t, ...read() });
      }
      return out;
    }, { scenario, END, STEP });
    results.push({ name: scenario.name, props: scenario.props, start: scenario.start || {}, ...samples });
  }
  await browser.close();
  const target = path.join(__dirname, '../../gauge/src/test/resources/reference/overlay.json');
  fs.writeFileSync(target, JSON.stringify({ generatedBy: 'tools/gauge-reference/capture-overlay.js', scenarios: results }));
  console.log(`Wrote ${results.length} scenarios to ${path.relative(process.cwd(), target)}`);
})();
