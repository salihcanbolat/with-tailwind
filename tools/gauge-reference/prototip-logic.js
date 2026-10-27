// Generated from design/Prototip.dc.html by tools/gauge-reference/README.md's recipe: the prototype's own
// renderVals(), with its random price timer switched off. Do not edit by hand.
/* eslint-disable */
class PrototypeLogic {
constructor(props) { this.props = props; this.state = {}; }
setState(o) { this.state = Object.assign({}, this.state, o); if (this.onChange) this.onChange(); }
renderVals() {
const st = this.state || {};
if (false) {
  this._dmTick = setInterval(() => {
    const mode = (this.props && this.props.nabiz) || 'Normal';
    if (mode === 'Kapalı') return;
    const k = mode === 'Hareketli' ? 2.2 : (mode === 'Sakin' ? 0.45 : 1);
    const cur = (this.state && this.state.tk) || [{ n: 0, d: 1 }, { n: 0, d: 1 }, { n: 0, d: 1 }, { n: 0, d: 1 }];
    const rates = [0.2, 0.11, 0.16, 0.42];
    const bias = [0.72, 0.5, 0.28, 0.5];
    let changed = false;
    const nx = cur.map((o, i) => {
      if (Math.random() < rates[i] * k) { changed = true; const dd = Math.random() < bias[i] ? 1 : -1; return { n: o.n + 1, d: dd, a: (o.a || 0) + dd * 360, t: Date.now() }; }
      return o;
    });
    this._dmN = (this._dmN || 0) + 1;
    const flipU = this._dmN % 38 === 0;
    if (changed || flipU) this.setState(flipU ? { tk: nx, usdUp: !(this.state && this.state.usdUp) } : { tk: nx });
  }, 400);
}
const stage = st.stage ?? 0;
const boyut = this.props.boyut ?? 'Tek varlık';
const mini = boyut === '1 yuva · 3 damla' || boyut === '1 yuva · 2 damla';
const mini2 = boyut === '1 yuva · 2 damla';
const single = boyut === '1×1';
const nW = mini2 ? 2 : ((mini || boyut === '3×1') ? 3 : (boyut === '2×1' ? 2 : 1));
const nSlot = mini ? 1 : nW;
const flow = (this.props.acilis ?? 'Akış') === 'Akış' && !single;
const bare = (this.props.stil ?? 'Kutusuz') === 'Kutusuz';
const sel = single ? 0 : (st.sel ?? 0);
const from = st.from ?? 0;
const rng = st.rng ?? 1;
const scrub = (typeof st.scrub === 'number') ? st.scrub : null;
const R = (this.props.taraf ?? 'sağ') !== 'sol';
const m = Number(this.props.hiz ?? 1);
const t = (ms) => Math.round(ms * m);
const ease = 'cubic-bezier(0.32, 0.72, 0, 1)';
const easeIn = 'cubic-bezier(0.4, 0, 0.2, 1)';
const spring = 'cubic-bezier(0.25, 1.12, 0.5, 1)';
const softSpring = 'cubic-bezier(0.25, 1.04, 0.5, 1)';

const G = '#34C759', RD = '#FF3B30', Y = '#FFCC00', O = '#FF9500';
const words = { Y: 'Yükseliş', D: 'Düşüş', S: 'Sabit', W: 'Dalgalı' };
const wcol = { Y: '#1E7B34', D: '#C4271C', S: '#7A5F00', W: '#B25E00' };
const dcol = { Y: G, D: RD, S: Y, W: O };
const tf1 = ['Son 1 saat', 'Dün', 'Son 1 hafta'];
const raw = [
  { name: 'Gram altın', short: 'Altın', sub: 'Altın · TRY', badge: 'Au', bb: '#F2C230', bf: '#5A4300', tip: 'up', c1: Y, c2: G, w: ['Y', 'S', 'Y'], tf: tf1 },
  { name: 'USD/TRY', short: 'USD', sub: 'Döviz', badge: '$', bb: '#2E7D4F', bf: '#FFFFFF', tip: st.usdUp ? 'up' : 'flat', c1: Y, c2: G, w: [st.usdUp ? 'Y' : 'S', 'S', 'Y'], tf: tf1 },
  { name: 'Gümüş', short: 'Gümüş', sub: 'Gümüş · TRY', badge: 'Ag', bb: '#C7CCD3', bf: '#3A4048', tip: 'down', c1: RD, c2: Y, w: ['D', 'D', 'S'], tf: ['Son 4 saat', 'Son 3 gün', 'Son 1 ay'] },
  { name: 'Bitcoin', short: 'BTC', sub: 'Kripto · USD', badge: '₿', bb: '#F7931A', bf: '#FFFFFF', tip: 'wave', c1: G, c2: RD, w: ['W', 'Y', 'D'], tf: ['Son 15 dk', 'Dün', 'Son 1 hafta'] }
];
const mainCol = { up: G, down: RD, flat: '#E0B000', wave: O };
const pills = {
  up: ['+[%] · ', '#E3F7E8', '#1E7B34'],
  down: ['−[%] · ', '#FDE7E6', '#C4271C'],
  flat: ['±[%] · ', '#FFF6D6', '#7A5F00'],
  wave: ['±[%] · ', '#FFF0DC', '#B25E00']
};

// Grafik: her zaman 24 nokta, böylece şekiller birbirine akıcı dönüşür
const CW = 260, CH = 54, NP = 24;
const gen = (tip, rg) => {
  const out = [];
  const amp = 1 + rg * 0.35, ph = rg * 1.7;
  for (let k = 0; k < NP; k++) {
    const u = k / (NP - 1);
    let v;
    if (tip === 'up') v = 0.8 - 0.58 * u + 0.07 * amp * Math.sin(u * 12 + ph);
    else if (tip === 'down') v = 0.2 + 0.58 * u + 0.07 * amp * Math.sin(u * 11 + 1 + ph);
    else if (tip === 'flat') v = 0.5 + 0.035 * amp * Math.sin(u * 9 + ph);
    else v = 0.5 + 0.32 * Math.sin(u * Math.PI * (4 + 2 * (rg % 2))) * (1 - 0.15 * u);
    v = Math.min(0.94, Math.max(0.06, v));
    out.push([u * CW, v * CH]);
  }
  return out;
};
const f1 = (n) => n.toFixed(1);
const toPath = (p) => {
  let s = 'M' + f1(p[0][0]) + ',' + f1(p[0][1]);
  for (let i = 0; i < p.length - 1; i++) {
    const p0 = p[i - 1] || p[i], p1 = p[i], p2 = p[i + 1], p3 = p[i + 2] || p2;
    s += ' C' + f1(p1[0] + (p2[0] - p0[0]) / 6) + ',' + f1(p1[1] + (p2[1] - p0[1]) / 6) + ' ' + f1(p2[0] - (p3[0] - p1[0]) / 6) + ',' + f1(p2[1] - (p3[1] - p1[1]) / 6) + ' ' + f1(p2[0]) + ',' + f1(p2[1]);
  }
  return s;
};

// Ekran geometrisi
const RE = 380, LE = 10;
const FL = R ? RE - 176 : LE;
const cellC = (i) => [FL + 10 + 78 * (i % 2) + 39, 122 + 66 + 85 * Math.floor(i / 2)];
const cStart = R ? 4 - nSlot : 0;
const colX = (c) => c * 97.5 + 48.75;
const wLeft = cStart * 97.5 + 10;
const wW = nSlot * 97.5 - 20, wH = bare ? 90 : 76;
const wS = mini ? (mini2 ? 32 : 26) : (bare ? 46 : (single ? 42 : 38));
const wY = mini ? (bare ? 154 : 168) : (bare ? 156 : 169);
const wC = (j) => mini ? [colX(cStart) + (mini2 ? (j - 0.5) * 26 : (j - 1) * 20), wY] : [colX(cStart + j), wY];
const wMid = [wLeft + wW / 2, 122 + wH / 2];
const fx0 = mini ? colX(cStart) : wMid[0], fy0 = wY;
const flowP = (i) => [R ? colX(3) - 70 * (1 - (i % 2)) : colX(0) + 70 * (i % 2), fy0 + 80 * Math.floor(i / 2)];
const posC = (i) => flow ? flowP(i) : cellC(i);
const CL = R ? RE - 280 : LE;
const heroS = single ? wS / 40 : 1;
const occ = [];
for (let k = 0; k < nSlot; k++) occ.push(cStart + k);
const slots = ['Takvim', 'Saat', 'Notlar', 'Hava', 'Dosyalar', 'Kişiler', 'Müzik', 'Ayarlar'].map((n, k) => {
  const rr = Math.floor(k / 4), cc = k % 4;
  const hide = rr === 0 && occ.indexOf(cc) >= 0;
  return { label: n, style: 'left: ' + (cc * 97.5 + 20.75) + 'px; top: ' + (128 + rr * 94) + 'px; opacity: ' + (hide ? 0 : 1) + ';' };
});
const sc0 = single ? wC(0) : posC(sel);
const rx = Math.round(sc0[0] - CL), ry = Math.round(sc0[1] - 122);

const go = (s, extra) => this.setState(Object.assign({ stage: s, from: (this.state && this.state.stage) || 0, scrub: null }, extra || {}));
const closeAll = () => {
  const cur = (this.state && this.state.stage) || 0;
  if (cur === 2 && !single) {
    go(1);
    setTimeout(() => go(0), t(440));
  } else {
    go(0);
  }
};

const nabizOn = (this.props.nabiz ?? 'Normal') !== 'Kapalı';
const tks = st.tk || [];
const tkS = (i) => {
  const o = tks[i] || { n: 0, d: 1 };
  const on = nabizOn && o.n > 0;
  const ab = o.n % 2 ? 'A' : 'B';
  const up = o.d > 0;
  const cc = up ? '#34C759' : '#FF3B30';
  return {
    beat: 'animation: none;',
    rip: 'display: none;',
    ang: o.a || 0,
    flash: '--dmcs: ' + (up ? 'rgba(52,199,89,0.3)' : 'rgba(255,59,48,0.26)') + '; animation: ' + (on ? 'dmFlas' + ab + ' ' + t(900) + 'ms ease-out' : 'none') + ';',
    arrow: o.n ? (up ? '▲' : '▼') : '',
    arrowSt: 'color: ' + (up ? '#1E7B34' : '#C4271C') + '; display: inline-block; animation: ' + (on ? 'dmPop' + ab + ' ' + t(420) + 'ms cubic-bezier(0.3, 1.4, 0.5, 1)' : 'none') + ';'
  };
};

const assets = raw.map((a, i) => {
  const c = cellC(i);
  let tx = 0, ty = 0, s = 1, op = 1, blur = 0, lop = 1, tr = '', ltr = '';
  const pc = posC(i);
  const bx = pc[0] - c[0], by = pc[1] - c[1];
  if (stage === 0) {
    lop = 0;
    if (i < nW) {
      const wp = wC(i);
      tx = wp[0] - c[0]; ty = wp[1] - c[1]; s = wS / 40; op = 1;
      tr = 'transform ' + t(540) + 'ms ' + ease + ', opacity ' + t(200) + 'ms ease ' + (from === 2 ? t(360) : 0) + 'ms';
    } else {
      tx = (wMid[0] - c[0]) * 0.45; ty = (wMid[1] - c[1]) * 0.45; s = 0.7; op = 0;
      tr = 'transform ' + t(420) + 'ms ' + easeIn + ', opacity ' + t(160) + 'ms ease';
    }
    ltr = 'opacity ' + t(90) + 'ms ease';
  } else if (stage === 1) {
    tx = bx; ty = by;
    if (from === 2) {
      const dl = i === sel ? t(360) : 0;
      tr = 'transform ' + t(520) + 'ms ' + ease + ', opacity ' + t(i === sel ? 120 : 300) + 'ms ease ' + dl + 'ms, filter ' + t(300) + 'ms ease';
      ltr = 'opacity ' + t(240) + 'ms ease';
    } else {
      const dl = i < 2 ? 0 : t(60 + i * 40);
      tr = 'transform ' + t(i < 2 ? 600 : 560) + 'ms ' + (i < 2 ? softSpring : spring) + ' ' + dl + 'ms, opacity ' + t(240) + 'ms ease ' + dl + 'ms, filter ' + t(300) + 'ms ease';
      ltr = 'opacity ' + t(260) + 'ms ease ' + t(200 + i * 40) + 'ms';
      if (flow) {
        const fdl = t(i * 45);
        tr = 'transform ' + t(560) + 'ms ' + softSpring + ' ' + fdl + 'ms, opacity ' + t(200) + 'ms ease ' + fdl + 'ms, filter ' + t(300) + 'ms ease';
      }
    }
  } else if (single) {
    lop = 0; ltr = 'opacity 1ms';
    if (i === 0) {
      const wp = wC(0);
      tx = wp[0] - c[0]; ty = wp[1] - c[1]; s = wS / 40; op = 0;
      tr = 'opacity ' + t(70) + 'ms ease';
    } else {
      tx = (wMid[0] - c[0]) * 0.45; ty = (wMid[1] - c[1]) * 0.45; s = 0.7; op = 0;
      tr = 'opacity 1ms';
    }
  } else {
    if (i === sel) {
      op = 0;
      tr = 'opacity ' + t(70) + 'ms ease';
    } else {
      const vx = pc[0] - sc0[0], vy = pc[1] - sc0[1];
      const len = Math.hypot(vx, vy) || 1;
      tx = bx + vx / len * 14; ty = by + vy / len * 14; s = 0.85; op = 0; blur = 2;
      tr = 'transform ' + t(560) + 'ms ' + ease + ', opacity ' + t(280) + 'ms ease, filter ' + t(280) + 'ms ease';
    }
    ltr = 'opacity ' + t(180) + 'ms ease';
  }
  return Object.assign({}, a, {
    badgeStyle: 'background: ' + a.bb + '; color: ' + a.bf + ';',
    stateWord: words[a.w[0]],
    beatStyle: tkS(i).beat,
    ripStyle: tkS(i).rip,
    flashStyle: tkS(i).flash,
    arrowStyle: tkS(i).arrowSt,
    arrow: tkS(i).arrow,
    small: (stage === 0 && mini) ? 'true' : 'false',
    donus: tkS(i).ang,
    pillPos: (() => {
      const vis = false;
      const dl = vis ? t(260 + i * 70) : 0;
      return 'top: ' + Math.round(pc[1] - 17) + 'px; ' + (R ? 'right: ' + Math.round(390 - (pc[0] - 30)) : 'left: ' + Math.round(pc[0] + 30)) + 'px; transform-origin: ' + (R ? 'right' : 'left') + ' center; opacity: ' + (vis ? 1 : 0) + '; transform: translateX(' + (vis ? 0 : (R ? 12 : -12)) + 'px) scale(' + (vis ? 1 : 0.92) + '); pointer-events: ' + (vis ? 'auto' : 'none') + '; transition: opacity ' + t(vis ? 260 : 120) + 'ms ease ' + dl + 'ms, transform ' + t(460) + 'ms ' + ease + ' ' + dl + 'ms;';
    })(),
    wPos: 'left: ' + (colX(cStart + i) - wLeft).toFixed(1) + 'px; top: ' + (bare ? 68 : 7) + 'px;',
    stateStyle: 'color: ' + (flow ? dcol[a.w[0]] : wcol[a.w[0]]) + ';',
    isSel: i === sel ? 'true' : 'false',
    open: () => go(2, { sel: i }),
    pick: () => this.setState({ sel: i, scrub: null }),
    cellStyle: 'transform-origin: 50% 28px; transform: translate(' + tx.toFixed(1) + 'px, ' + ty.toFixed(1) + 'px) scale(' + s.toFixed(3) + '); opacity: ' + op + '; filter: blur(' + blur + 'px) drop-shadow(0 2px 3px rgba(0,0,0,' + (((bare && stage === 0) || (flow && stage >= 1)) ? 0.35 : 0) + ')); transition: ' + tr + ';',
    labelStyle: 'opacity: ' + lop + '; transition: ' + ltr + ';' + (flow ? ' color: #FFFFFF; text-shadow: 0 1px 3px rgba(0,0,0,0.6);' : '')
  });
});
const d = assets[sel] || assets[0];
const p = pills[d.tip];
const col = mainCol[d.tip];
const pts = gen(d.tip, rng);
const linePath = toPath(pts);
const areaPath = linePath + ' L' + CW + ',' + CH + ' L0,' + CH + ' Z';
const last = pts[NP - 1];

let scrubY = 0, tipText = '', tipLeft = 0;
if (scrub !== null) {
  const fx = scrub / CW * (NP - 1);
  const i0 = Math.min(NP - 2, Math.floor(fx));
  const fr = fx - i0;
  scrubY = pts[i0][1] + (pts[i0 + 1][1] - pts[i0][1]) * fr;
  const u = scrub / CW;
  const gunler = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];
  const aylar = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];
  const when = rng === 0 ? String(Math.floor(u * 23.99)).padStart(2, '0') + ':00' : rng === 1 ? gunler[Math.floor(u * 6.99)] : rng === 2 ? (1 + Math.floor(u * 29.99)) + '. gün' : aylar[Math.floor(u * 11.99)];
  tipText = when + ' · ₺[FİYAT]';
  tipLeft = Math.max(0, Math.min(CW - 92, scrub - 46));
}

const FDW = 260;
const OX = R ? RE - FDW : LE, OY = 132;
const HLx = 44, HLy = 100, HS2 = 80, u2 = HS2 / 76;
const FK = 0.86;
const HCx = R ? OX + FDW - (FDW - HLx) * FK : OX + HLx * FK, HCy = OY + HLy * FK;
const hs0 = (single ? wS : 40) / HS2;
const annoLocal = [[20, 0], [22.9, 32.8], [24, 41.6]];
const annoTops = [48, 90, 132];
const annoPts = annoLocal.map((q) => [HLx + q[0] * u2, HLy + (q[1] - 8) * u2]);
const annoPath = (k) => 'M' + f1(annoPts[k][0]) + ',' + f1(annoPts[k][1]) + ' L92,' + (annoTops[k] + 15) + ' L98,' + (annoTops[k] + 15);
const annoLine = (k) => 'stroke-dasharray: 140; stroke-dashoffset: ' + (d2 ? 0 : 140) + '; transition: stroke-dashoffset ' + t(d2 ? 520 : 140) + 'ms ' + ease + ' ' + (d2 ? t(380 + k * 70) : 0) + 'ms;';
const annoDot = (k) => 'opacity: ' + (d2 ? 1 : 0) + '; transition: opacity ' + t(d2 ? 260 : 100) + 'ms ease ' + (d2 ? t(360 + k * 70) : 0) + 'ms;';
const brightW = { Y: '#4CD964', D: '#FF6B6B', S: '#FFD60A', W: '#FFB340' };
const brightT = { up: '#4CD964', down: '#FF6B6B', flat: '#FFD60A', wave: '#FFB340' };
const goBack = () => (single ? go(0) : go(1));
const open = stage >= 1;
const fo = open && !single && !flow;
const d2 = stage === 2;
const w = fo ? 176 : wW, h = fo ? 216 : wH, r = fo ? 22 : (single ? 20 : 22);
const left = fo ? (R ? RE - w : LE) : wLeft;
const anchor = R ? 'right: 0;' : 'left: 0;';
const boxOx = Math.round(sc0[0] - left), boxOy = Math.round(sc0[1] - 122);
const cardH = single ? 400 : 446;
const sec = (n) => d2
  ? 'opacity: 1; transform: translateY(0px); filter: blur(0px); transition: opacity ' + t(340) + 'ms ease ' + t(190 + n * 40) + 'ms, transform ' + t(540) + 'ms ' + ease + ' ' + t(190 + n * 40) + 'ms, filter ' + t(380) + 'ms ease ' + t(190 + n * 40) + 'ms;'
  : 'opacity: 0; transform: translateY(8px); filter: blur(4px); transition: opacity ' + t(110) + 'ms ease, transform ' + t(200) + 'ms ease, filter ' + t(150) + 'ms ease;';
const ranges = ['1 gün', '1 hafta', '1 ay', '1 yıl'].map((lb, i) => ({
  label: lb,
  on: i === rng ? 'true' : 'false',
  style: 'font-weight: ' + (i === rng ? 600 : 400) + '; color: ' + (i === rng ? '#1C1C1E' : '#FFFFFF') + ';',
  pick: () => this.setState({ rng: i, scrub: null })
}));

return {
  hiz: m,
  endRip: (() => { const o = tks[sel] || { n: 0, d: 1 }; const on = nabizOn && o.n > 0; return 'transform-box: fill-box; transform-origin: center; animation: ' + (on ? 'dmUc' + (o.n % 2 ? 'A' : 'B') + ' ' + t(1000) + 'ms ease-out' : 'none') + ';'; })(),
  cardLeft: CL,
  closeOrb: (() => {
    const cv = false;
    const dl = cv ? t(220) : 0;
    return 'left: ' + Math.round(fx0 - 20) + 'px; top: ' + Math.round(fy0 - 20) + 'px; opacity: ' + (cv ? 1 : 0) + '; transform: rotate(' + (cv ? 0 : -90) + 'deg) scale(' + (cv ? 1 : 0.5) + '); pointer-events: ' + (cv ? 'auto' : 'none') + '; transition: opacity ' + t(220) + 'ms ease ' + dl + 'ms, transform ' + t(520) + 'ms ' + softSpring + ' ' + dl + 'ms;';
  })(),
  cardH,
  wW, wH, slots,
  wCols: single ? 1 : 2,
  wPad: bare ? (single ? '4px 2px 3px' : '4px 8px 3px') : (single ? '5px 4px' : '7px 8px 6px'),
  wAlign: bare ? 'end' : 'start',
  wText: bare ? 'color: #FFFFFF; font-size: 12px; font-weight: 400; text-shadow: 0 1px 3px rgba(0,0,0,0.55);' : '',
  wBadge: (bare || mini) ? 'display: none;' : '',
  showSwitcher: !single,
  backLabel: single ? 'Kapat' : 'Damla',
  homeStyle: 'filter: blur(' + (stage ? 6 : 0) + 'px); transform: scale(' + (stage ? 0.985 : 1) + '); transition: filter ' + t(520) + 'ms ease, transform ' + t(600) + 'ms ' + ease + ';',
  dimStyle: 'opacity: ' + (stage ? 1 : 0) + '; pointer-events: ' + (stage ? 'auto' : 'none') + '; transition: opacity ' + t(420) + 'ms ease; cursor: default;',
  boxStyle: 'opacity: ' + (d2 ? 0 : 1) + '; overflow: ' + (flow ? 'visible' : 'hidden') + '; left: ' + left + 'px; top: 122px; width: ' + w + 'px; height: ' + h + 'px; border-radius: ' + r + 'px; background: ' + (fo ? 'rgba(250,250,252,0.8)' : (bare ? 'rgba(250,250,252,0)' : '#FFFFFF')) + '; backdrop-filter: ' + (fo ? 'blur(24px) saturate(1.8)' : 'none') + '; -webkit-backdrop-filter: ' + (fo ? 'blur(24px) saturate(1.8)' : 'none') + '; box-shadow: ' + (fo ? '0 16px 44px rgba(0,0,0,0.32), inset 0 0 0 0.5px rgba(255,255,255,0.7)' : (bare ? '0 0 0 rgba(0,0,0,0), inset 0 0 0 0.5px rgba(255,255,255,0)' : '0 2px 10px rgba(0,0,0,0.15), inset 0 0 0 0.5px rgba(255,255,255,0)')) + '; transform-origin: ' + boxOx + 'px ' + boxOy + 'px; transform: scale(' + (d2 ? 0.985 : 1) + '); transition: left ' + t(540) + 'ms ' + ease + ', width ' + t(540) + 'ms ' + ease + ', height ' + t(540) + 'ms ' + ease + ', border-radius ' + t(540) + 'ms ' + ease + ', background ' + t(300) + 'ms ease, box-shadow ' + t(540) + 'ms ease, transform ' + t(600) + 'ms ' + ease + ', opacity ' + t(260) + 'ms ease;',
  wLayer: anchor + ' opacity: ' + (stage === 0 ? 1 : 0) + '; pointer-events: ' + (stage === 0 ? 'auto' : 'none') + '; transition: opacity ' + (stage === 0 ? t(220) + 'ms ease ' + t(360) + 'ms' : t(100) + 'ms ease') + ';',
  fLayer: anchor + ' pointer-events: ' + (stage === 1 ? 'auto' : 'none') + ';',
  hdrStyle: 'pointer-events: ' + (flow ? 'none' : 'auto') + '; opacity: ' + (stage === 1 && !flow ? 1 : 0) + '; transform: translateY(' + (stage === 1 && !flow ? 0 : -5) + 'px); transition: opacity ' + t(stage === 1 ? 260 : 110) + 'ms ease ' + (stage === 1 && from === 0 ? t(180) : 0) + 'ms, transform ' + t(420) + 'ms ' + ease + ';',
  hdrDir: R ? 'row' : 'row-reverse',
  titleJustify: R ? 'flex-start' : 'flex-end',
  closeIcon: R ? 'M6 6l6 6-6 6M13 6l6 6-6 6' : 'M18 6l-6 6 6 6M11 6l-6 6 6 6',
  cardWrap: 'pointer-events: ' + (d2 ? 'auto' : 'none') + ';',
  cardClip: d2
    ? 'clip-path: circle(560px at ' + rx + 'px ' + ry + 'px); opacity: 1; transition: clip-path ' + t(600) + 'ms ' + ease + ', opacity 1ms;'
    : 'clip-path: circle(24px at ' + rx + 'px ' + ry + 'px); opacity: 0; transition: clip-path ' + t(440) + 'ms ' + easeIn + ', opacity ' + t(110) + 'ms ease ' + t(350) + 'ms;',
  heroStyle: d2
    ? 'opacity: 1; transform: translate(' + f1(HCx - 40 * FK) + 'px, ' + f1(HCy - 48.5 * FK) + 'px) scale(' + FK + '); transition: transform ' + t(640) + 'ms ' + ease + ', opacity 1ms;'
    : 'opacity: 0; transform: translate(' + f1(sc0[0] - 40 * hs0) + 'px, ' + f1(sc0[1] - 48.5 * hs0) + 'px) scale(' + hs0.toFixed(3) + '); transition: transform ' + t(440) + 'ms ' + ease + ', opacity ' + t(90) + 'ms ease ' + t(380) + 'ms;',
  fdDim: 'opacity: ' + (d2 ? 1 : 0) + '; transition: opacity ' + t(420) + 'ms ease;',
  fdPE: d2 ? 'auto' : 'none',
  fresh: (() => { const o = tks[sel] || {}; if (!o.t) return 'canlı'; const sec = Math.floor((Date.now() - o.t) / 1000); return sec < 2 ? 'canlı · şimdi' : 'canlı · ' + sec + ' sn önce'; })(),
  openShare: () => this.setState({ share: true }),
  closeShare: () => this.setState({ share: false }),
  shDim: 'opacity: ' + (st.share && d2 ? 1 : 0) + '; pointer-events: ' + (st.share && d2 ? 'auto' : 'none') + '; transition: opacity ' + t(320) + 'ms ease;',
  shBox: 'opacity: ' + (st.share && d2 ? 1 : 0) + '; transform: translateY(' + (st.share && d2 ? 0 : 24) + 'px) scale(' + (st.share && d2 ? 1 : 0.92) + '); pointer-events: ' + (st.share && d2 ? 'auto' : 'none') + '; transition: opacity ' + t(320) + 'ms ease, transform ' + t(560) + 'ms ' + ease + ';',
  shGlow: 'background: ' + col + ';',
  shWord: 'color: ' + brightW[d.w[0]] + ';',
  shWordText: words[d.w[0]] + ' · ' + d.tf[0].toLowerCase(),
  fdBox: 'left: ' + OX + 'px; top: ' + OY + 'px; transform: scale(' + FK + '); transform-origin: ' + (R ? 'top right' : 'top left') + ';',
  an0: annoPath(0), an1: annoPath(1), an2: annoPath(2),
  al0: annoLine(0), al1: annoLine(1), al2: annoLine(2),
  ax0: f1(annoPts[0][0]), ay0: f1(annoPts[0][1]), ax1: f1(annoPts[1][0]), ay1: f1(annoPts[1][1]), ax2: f1(annoPts[2][0]), ay2: f1(annoPts[2][1]),
  ad0: annoDot(0), ad1: annoDot(1), ad2: annoDot(2),
  anno: d.w.map((k, j) => ({
    label: ['Damla', 'Üst halka', 'Alt halka'][j] + ' · ' + d.tf[j].toLowerCase(),
    short: ['Damla', 'Üst halka', 'Alt halka'][j],
    word: words[k],
    wordStyle: 'color: ' + brightW[k] + ';',
    pos: 'top: ' + annoTops[j] + 'px; opacity: ' + (d2 ? 1 : 0) + '; transform: translateX(' + (d2 ? 0 : -8) + 'px); filter: blur(' + (d2 ? 0 : 3) + 'px); transition: opacity ' + t(d2 ? 340 : 110) + 'ms ease ' + (d2 ? t(460 + j * 70) : 0) + 'ms, transform ' + t(520) + 'ms ' + ease + ' ' + (d2 ? t(460 + j * 70) : 0) + 'ms, filter ' + t(360) + 'ms ease ' + (d2 ? t(460 + j * 70) : 0) + 'ms;'
  })),
  arrowBright: (() => { const o = tks[sel] || { n: 0, d: 1 }; const on = nabizOn && o.n > 0; return 'color: ' + (o.d > 0 ? '#4CD964' : '#FF6B6B') + '; display: inline-block; animation: ' + (on ? 'dmPop' + (o.n % 2 ? 'A' : 'B') + ' ' + t(420) + 'ms cubic-bezier(0.3, 1.4, 0.5, 1)' : 'none') + ';'; })(),
  pillBright: 'color: ' + brightT[d.tip] + ';',
  dots: assets.map((a, i) => ({ name: a.name, pick: () => this.setState({ sel: i, scrub: null }), style: 'width: ' + (i === sel ? 18 : 6) + 'px; opacity: ' + (i === sel ? 1 : 0.45) + ';' })),
  swDown: (e) => { this._sx = e && typeof e.clientX === 'number' ? e.clientX : null; },
  swUp: (e) => {
    const sx = this._sx; this._sx = null;
    if (sx === null || sx === undefined || !e) { goBack(); return; }
    const dx = e.clientX - sx;
    const cur = (this.state && typeof this.state.sel === 'number') ? this.state.sel : 0;
    if (Math.abs(dx) > 40 && !single) this.setState({ sel: (cur + (dx < 0 ? 1 : -1) + 4) % 4, scrub: null });
    else if (Math.abs(dx) < 10) goBack();
  },
  haloTop: single ? 50 : 96,
  haloWrap: 'opacity: ' + (d2 ? 1 : 0) + '; transition: opacity ' + t(d2 ? 600 : 120) + 'ms ease ' + (d2 ? t(520) : 0) + 'ms;',
  haloColor: 'background: ' + col + '; transition: background 400ms ease;',
  pillMove: 'transform: translateX(' + (sel * 63.25).toFixed(2) + 'px); transition: transform ' + t(460) + 'ms ' + softSpring + ';',
  rngMove: 'transform: translateX(' + (rng * 64.5).toFixed(2) + 'px); transition: transform ' + t(420) + 'ms ' + softSpring + ';',
  ranges,
  sec0: sec(0), sec1: sec(1), sec2: sec(2), sec3: sec(3), sec4: sec(4), sec5: sec(5), sec6: sec(6),
  assets,
  widgetAssets: mini ? [{ short: 'Damla', badge: '', badgeStyle: '', wPos: 'left: ' + (colX(cStart) - wLeft).toFixed(1) + 'px; top: ' + (bare ? 68 : 7) + 'px;' }] : assets.slice(0, nW),
  d,
  pillText: p[0] + d.tf[0].toLowerCase(),
  pillStyle: 'background: ' + p[1] + '; color: ' + p[2] + ';',
  rows: d.w.map((k, j) => ({
    label: ['Damla', 'Üst halka', 'Alt halka'][j] + ' · ' + d.tf[j].toLowerCase(),
    word: words[k],
    style: 'color: ' + wcol[k] + ';',
    dot: 'background: ' + dcol[k] + ';',
    sep: j ? 'border-top: 0.5px solid #E5E5EA; padding-top: 5px;' : ''
  })),
  chartBase: 'M0,' + f1(pts[0][1]) + ' L' + CW + ',' + f1(pts[0][1]),
  chartLine: linePath,
  chartArea: areaPath,
  lineStyle: 'd: path("' + linePath + '"); stroke: ' + col + '; stroke-dasharray: 600; stroke-dashoffset: ' + (d2 ? 0 : 600) + '; transition: d ' + t(560) + 'ms ' + ease + ', stroke-dashoffset ' + t(1000) + 'ms ' + ease + ' ' + (d2 ? t(380) : 0) + 'ms, stroke ' + t(400) + 'ms ease;',
  areaStyle: 'd: path("' + areaPath + '"); fill: ' + col + '; fill-opacity: ' + (d2 ? 0.2 : 0) + '; transition: d ' + t(560) + 'ms ' + ease + ', fill-opacity ' + t(600) + 'ms ease ' + (d2 ? t(760) : 0) + 'ms, fill ' + t(400) + 'ms ease;',
  endX: f1(last[0]), endY: f1(last[1]),
  endDot: 'fill: ' + col + '; opacity: ' + (d2 ? 1 : 0) + '; transition: fill 400ms ease, opacity ' + t(300) + 'ms ease ' + (d2 ? t(1200) : 0) + 'ms;',
  noScrub: scrub === null,
  hasScrub: scrub !== null,
  scrubX: scrub === null ? 0 : f1(scrub),
  scrubY: f1(scrubY),
  scrubStroke: 'stroke: ' + col + ';',
  tipText,
  tipPos: 'left: ' + f1(tipLeft) + 'px;',
  scrubMove: (e) => {
    const el = e && e.currentTarget;
    if (!el || !el.getBoundingClientRect) return;
    const rc = el.getBoundingClientRect();
    if (!rc.width) return;
    const frc = Math.min(1, Math.max(0, (e.clientX - rc.left) / rc.width));
    this.setState({ scrub: frc * CW });
  },
  scrubEnd: () => this.setState({ scrub: null }),
  openFolder: () => (single ? go(2, { sel: 0 }) : go(1)),
  back: goBack,
  closeAll,
  dimClick: () => { const cur = (this.state && this.state.stage) || 0; if (cur === 2 && !single) go(1); else go(0); }
};
}
}

window.PrototypeLogic = PrototypeLogic;
