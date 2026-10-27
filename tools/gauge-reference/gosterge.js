// Rebuilds design/Gosterge.dc.html in a plain page so Chromium can play its CSS transitions and
// animations. renderVals() is copied from the design file unchanged (except for `this.props`),
// and apply() writes the same style strings the Claude Design runtime would, so the browser does all
// the animating exactly as in the prototype.
/* eslint-disable */
const KEYFRAMES = `
@keyframes gstEzA{0%{transform:scale(1,1)}22%{transform:scale(1.07,0.9)}50%{transform:scale(0.96,1.05)}76%{transform:scale(1.015,0.99)}100%{transform:scale(1,1)}}
@keyframes gstHaleA{0%{opacity:0;transform:scale(0.6)}25%{opacity:0.5}100%{opacity:0;transform:scale(1.7)}}
@keyframes gstCizA{0%{stroke-dashoffset:100}100%{stroke-dashoffset:0}}
@keyframes gstParlA{0%{opacity:0.26;transform:translate(0,0) rotate(-24deg)}35%{opacity:0.7;transform:translate(10%,12%) rotate(-24deg)}100%{opacity:0.26;transform:translate(0,0) rotate(-24deg)}}
@keyframes gstEzB{0%{transform:scale(1,1)}22%{transform:scale(1.07,0.9)}50%{transform:scale(0.96,1.05)}76%{transform:scale(1.015,0.99)}100%{transform:scale(1,1)}}
@keyframes gstHaleB{0%{opacity:0;transform:scale(0.6)}25%{opacity:0.5}100%{opacity:0;transform:scale(1.7)}}
@keyframes gstCizB{0%{stroke-dashoffset:100}100%{stroke-dashoffset:0}}
@keyframes gstParlB{0%{opacity:0.26;transform:translate(0,0) rotate(-24deg)}35%{opacity:0.7;transform:translate(10%,12%) rotate(-24deg)}100%{opacity:0.26;transform:translate(0,0) rotate(-24deg)}}
`;

class Component {
  constructor(props) { this.props = props; }
renderVals() {
const tip = this.props.tip ?? 'up';
const S = Number(this.props.size ?? 120);
const H = Math.round(S * 92 / 76);
const u = S / 76;
const m = Number(this.props.hiz ?? 1);
const kapali = this.props.kapali === true || this.props.kapali === 'true';
const gray = '#AEAEB2';
const mains = { up: '#34C759', down: '#FF3B30', flat: '#FFCC00', wave: '#FF9500' };
const labels = { up: 'Yükseliş', down: 'Düşüş', flat: 'Sabit', wave: 'Dalgalı ama yatay' };
const main = kapali ? gray : (mains[tip] || mains.up);
const a1 = kapali ? gray : (this.props.c1 ?? main);
const a2 = kapali ? gray : (this.props.c2 ?? main);
const t = (ms) => Math.round(ms * m);
const spring = 'cubic-bezier(0.25, 1.1, 0.5, 1)';
const rots = {
  up: 'rotate(0deg) scale(1)',
  down: 'translate(0px, ' + (-6 * u).toFixed(2) + 'px) rotate(180deg) scale(1)',
  wave: 'translate(0px, ' + (6 * u).toFixed(2) + 'px) rotate(90deg) scale(1)',
  flat: 'rotate(0deg) scale(0.55)'
};
const isFlat = tip === 'flat';
const donus = Number(this.props.donus ?? 0);
if (this._gt === undefined) this._gt = tip;
if (this._gt !== tip) { this._gt = tip; this._gc = (this._gc || 0) + 1; }
if (this._g1 !== undefined && this._g1 !== (this.props.c1 ?? '')) this._r1 = (this._r1 || 0) + 1;
this._g1 = this.props.c1 ?? '';
if (this._g2 !== undefined && this._g2 !== (this.props.c2 ?? '')) this._r2 = (this._r2 || 0) + 1;
this._g2 = this.props.c2 ?? '';
if (this._gd !== undefined && this._gd !== donus) this._gs = (this._gs || 0) + 1;
this._gd = donus;
const gc = this._gc || 0, k1 = gc + (this._r1 || 0), k2 = gc + (this._r2 || 0), gs = this._gs || 0;
const ab = (n) => (n % 2 ? 'A' : 'B');
const logo = String(this.props.logo ?? 'Au');
const bcs = { up: [0, 12], down: [0, -18], wave: [-12, 6], flat: [0, 6] };
const bc = bcs[tip] || bcs.up;
const lc = kapali ? '#FFFFFF' : (isFlat ? '#5A4300' : '#FFFFFF');
const fs = (logo.length > 1 ? 0.23 : 0.3) * S;
const isAu = logo === 'Au', isAg = logo === 'Ag', isUsd = logo === '$', isEur = logo === '€', isBtc = logo === '₿';
const hasBadge = isAu || isAg || isUsd || isEur || isBtc;
const bzv = Math.max(3, Math.round(S * 0.4));
const small = bzv < 14 || this.props.kucuk === true || this.props.kucuk === 'true';
const sMap = isUsd ? ['#3C3B6E', '#FFFFFF', '$', 17, 17.8] : isAg ? ['#CDD2DA', '#3B434D', 'Ag', 12.5, 16.4] : isEur ? ['#003399', '#FFCC00', '€', 16, 17.6] : ['#000', '#fff', '', 12, 16];
return {
  S, H, logo, hasBadge,
  squash: gc ? 'animation: gstEz' + ab(gc) + ' ' + t(720) + 'ms cubic-bezier(0.3, 0.7, 0.4, 1) ' + t(140) + 'ms both;' : '',
  haloStyle: 'left: ' + (S / 2 + bc[0] * u - 30 * u).toFixed(2) + 'px; top: ' + (S / 2 + bc[1] * u - 30 * u).toFixed(2) + 'px; width: ' + (60 * u).toFixed(2) + 'px; height: ' + (60 * u).toFixed(2) + 'px; background: ' + main + '; filter: blur(' + (9 * u).toFixed(2) + 'px);' + (gc ? ' animation: gstHale' + ab(gc) + ' ' + t(1300) + 'ms cubic-bezier(0.2, 0.7, 0.3, 1) both;' : ''),
  arc1Anim: 'stroke-dasharray: 100;' + (k1 ? ' animation: gstCiz' + ab(k1) + ' ' + t(720) + 'ms cubic-bezier(0.32, 0.72, 0, 1) ' + t(200) + 'ms both;' : ''),
  arc2Anim: 'stroke-dasharray: 100;' + (k2 ? ' animation: gstCiz' + ab(k2) + ' ' + t(720) + 'ms cubic-bezier(0.32, 0.72, 0, 1) ' + t(320) + 'ms both;' : ''),
  hl: 'left: ' + (S / 2 - 12 * u).toFixed(2) + 'px; top: ' + (S / 2 - 15 * u).toFixed(2) + 'px; width: ' + (8 * u).toFixed(2) + 'px; height: ' + (12 * u).toFixed(2) + 'px; filter: blur(' + Math.max(0.4, 1.1 * u).toFixed(2) + 'px); opacity: 0.26; transform: rotate(-24deg);' + (gs ? ' animation: gstParl' + ab(gs) + ' ' + t(1100) + 'ms cubic-bezier(0.45, 0.05, 0.2, 1);' : ''),
  isAu: isAu && !small, isAg: isAg && !small, isUsd: isUsd && !small, isEur: isEur && !small, isBtc,
  isAuS: isAu && small, isTxtS: small && (isUsd || isAg || isEur),
  sBg: sMap[0], sFg: sMap[1], sTxt: sMap[2], sFs: sMap[3], sY: sMap[4],
  bz: bzv,
  ring: 'box-shadow: 0 0 0 ' + Math.max(0.6, S * 0.014).toFixed(2) + 'px rgba(255,255,255,0.95);',
  swPosD: 'left: ' + (S / 2 - 20 * u).toFixed(2) + 'px; top: ' + (S / 2 - 8 * u).toFixed(2) + 'px; width: ' + (40 * u).toFixed(2) + 'px; height: ' + (40 * u).toFixed(2) + 'px;',
  swPosC: 'left: ' + (S / 2 - 20 * u).toFixed(2) + 'px; top: ' + (S / 2 - 14 * u).toFixed(2) + 'px; width: ' + (40 * u).toFixed(2) + 'px; height: ' + (40 * u).toFixed(2) + 'px;',
  swRot: 'transform: rotate(' + Number(this.props.donus ?? 0) + 'deg); transition: transform ' + t(1100) + 'ms cubic-bezier(0.45, 0.05, 0.2, 1);',
  logoTxt: hasBadge ? '' : logo,
  iz: Math.round(S * 0.3),
  icFill: 'fill: ' + lc + '; transition: fill ' + t(450) + 'ms ease;',
  icLine: 'stroke: ' + main + '; transition: stroke ' + t(450) + 'ms ease;',
  logoWrap: 'transform: translate(' + (bc[0] * u).toFixed(2) + 'px, ' + (bc[1] * u).toFixed(2) + 'px); opacity: ' + (logo ? 1 : 0) + '; transition: transform ' + t(700) + 'ms ' + spring + ';',
  logoText: 'color: ' + lc + '; font-size: ' + fs.toFixed(2) + 'px; font-weight: 800; line-height: 1; letter-spacing: ' + (logo.length > 1 ? '-0.04em' : '0') + '; transition: color ' + t(450) + 'ms ease;',
  label: kapali ? 'Piyasa kapalı' : labels[tip],
  arc1Style: 'stroke: ' + a1 + '; transition: stroke ' + t(450) + 'ms ease;',
  arc2Style: 'stroke: ' + a2 + '; transition: stroke ' + t(450) + 'ms ease ' + t(60) + 'ms;',
  fillStyle: 'fill: ' + main + '; transition: fill ' + t(450) + 'ms ease;',
  dropWrap: 'transform-origin: 50% 50%; transform: ' + (rots[tip] || rots.up) + '; opacity: ' + (isFlat ? 0 : 1) + '; transition: transform ' + t(700) + 'ms ' + spring + ', opacity ' + t(260) + 'ms ease;',
  circWrap: 'transform-origin: 50% 58%; transform: scale(' + (isFlat ? 1 : 0.5) + '); opacity: ' + (isFlat ? 1 : 0) + '; transition: transform ' + t(600) + 'ms ' + spring + ', opacity ' + t(260) + 'ms ease;'
};
}
}

const BADGES = {
  Au: '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#E9B530"></rect><path d="M4.5,16.8 L19.5,16.8 L16.4,8 L7.6,8 Z" fill="#8A6400"></path><path d="M8.6,10.7 L15.4,10.7" stroke="#F7D776" stroke-width="1.4" stroke-linecap="round"></path></svg>',
  Ag: '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#CDD2DA"></rect><path d="M5,8.5 L5,15.5 A7,3 0 0 0 19,15.5 L19,8.5 Z" fill="#4E5661"></path><ellipse cx="12" cy="8.5" rx="7" ry="3" fill="#7A8491" stroke="#CDD2DA" stroke-width="1"></ellipse><path d="M5,12 A7,3 0 0 0 19,12" fill="none" stroke="#CDD2DA" stroke-width="1"></path></svg>',
  $: '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#FFFFFF"></rect><rect x="0" y="0.00" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="3.70" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="7.40" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="11.10" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="14.80" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="18.50" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="22.20" width="24" height="1.85" fill="#B22234"></rect><rect x="0" y="0" width="11.5" height="11.1" fill="#3C3B6E"></rect><circle cx="3" cy="3" r="0.9" fill="#FFFFFF"></circle><circle cx="7" cy="3" r="0.9" fill="#FFFFFF"></circle><circle cx="5" cy="5.6" r="0.9" fill="#FFFFFF"></circle><circle cx="9" cy="5.6" r="0.9" fill="#FFFFFF"></circle><circle cx="3" cy="8.2" r="0.9" fill="#FFFFFF"></circle><circle cx="7" cy="8.2" r="0.9" fill="#FFFFFF"></circle></svg>',
  '€': '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#003399"></rect><circle cx="18.60" cy="12.00" r="1.15" fill="#FFCC00"></circle><circle cx="17.72" cy="15.30" r="1.15" fill="#FFCC00"></circle><circle cx="15.30" cy="17.72" r="1.15" fill="#FFCC00"></circle><circle cx="12.00" cy="18.60" r="1.15" fill="#FFCC00"></circle><circle cx="8.70" cy="17.72" r="1.15" fill="#FFCC00"></circle><circle cx="6.28" cy="15.30" r="1.15" fill="#FFCC00"></circle><circle cx="5.40" cy="12.00" r="1.15" fill="#FFCC00"></circle><circle cx="6.28" cy="8.70" r="1.15" fill="#FFCC00"></circle><circle cx="8.70" cy="6.28" r="1.15" fill="#FFCC00"></circle><circle cx="12.00" cy="5.40" r="1.15" fill="#FFCC00"></circle><circle cx="15.30" cy="6.28" r="1.15" fill="#FFCC00"></circle><circle cx="17.72" cy="8.70" r="1.15" fill="#FFCC00"></circle></svg>',
  '₿': '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#F7931A"></rect><text x="12" y="17.6" text-anchor="middle" font-size="16" font-weight="800" fill="#FFFFFF" transform="rotate(14 12 12)" style="font-family: -apple-system, system-ui, sans-serif">₿</text></svg>',
  AuS: '<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="#E9B530"></rect><rect x="4.5" y="8.5" width="15" height="7.5" rx="1.5" fill="#7A5800"></rect></svg>',
};
const smallText = (v) => `<svg width="100%" height="100%" viewBox="0 0 24 24" style="display: block"><rect width="24" height="24" fill="${v.sBg}"></rect><text x="12" y="${v.sY}" text-anchor="middle" font-size="${v.sFs}" font-weight="800" fill="${v.sFg}" style="font-family: -apple-system, system-ui, sans-serif; letter-spacing: -0.5px">${v.sTxt}</text></svg>`;

const LIGHT_ARC = '<svg width="100%" height="100%" viewBox="-20 -20 40 40" style="display: block; overflow: visible"><path d="M-9.25,-16.02 A18.5 18.5 0 0 1 11.89,-14.17" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" stroke-opacity="0.66"></path><circle cx="16.77" cy="-7.82" r="1.3" fill="#FFFFFF" fill-opacity="0.5"></circle></svg>';

/** Builds the markup once and returns apply(props) that re-renders it like the runtime does. */
function mount(container, initialProps) {
  const style = document.createElement('style');
  style.textContent = KEYFRAMES;
  document.head.appendChild(style);
  const comp = new Component({ ...initialProps });
  const S = Number(initialProps.size ?? 120);
  const H = Math.round(S * 92 / 76);
  container.innerHTML = `
<div id="root" role="img" style="position: relative; width: ${S}px; height: ${H}px; flex-shrink: 0">
<div id="halo" aria-hidden="true"></div>
<svg width="${S}" height="${H}" viewBox="-38 -38 76 92" aria-hidden="true" style="position: absolute; left: 0; top: 0; overflow: visible">
<path id="arc1" d="M-22.9,32.8 A40 40 0 0 0 22.9,32.8" pathLength="100" fill="none" stroke-width="5.5" stroke-linecap="round"></path>
<path id="arc2" d="M-24,41.6 A48 48 0 0 0 24,41.6" pathLength="100" fill="none" stroke-width="5.5" stroke-linecap="round"></path>
</svg>
<div id="dropWrap" aria-hidden="true">
<div id="dropSquash"><svg width="${S}" height="${S}" viewBox="-38 -38 76 76" style="display: block; overflow: visible"><path id="dropPath" d="M0,-34 C3,-24 11.4,-12.2 19.05,1 A22 22 0 1 1 -19.05,1 C-11.4,-12.2 -3,-24 0,-34 Z"></path></svg></div>
<div id="swPosD"><div id="swRotD">${LIGHT_ARC}</div></div>
</div>
<div id="circWrap" aria-hidden="true">
<div id="circSquash"><svg width="${S}" height="${S}" viewBox="-38 -38 76 76" style="display: block; overflow: visible"><circle id="circPath" cx="0" cy="6" r="25"></circle></svg></div>
<div id="swPosC"><div id="swRotC">${LIGHT_ARC}</div></div>
</div>
<div id="logoWrap" aria-hidden="true"><span id="hl" aria-hidden="true"></span><span id="logoText"></span><span id="badge"></span></div>
</div>`;
  const $ = (id) => document.getElementById(id);
  const set = (id, css) => $(id).setAttribute('style', css);
  function apply(props) {
    comp.props = { ...props };
    const v = comp.renderVals();
    set('halo', 'position: absolute; border-radius: 50%; opacity: 0; pointer-events: none; ' + v.haloStyle);
    set('arc1', v.arc1Style + ' ' + v.arc1Anim);
    set('arc2', v.arc2Style + ' ' + v.arc2Anim);
    set('dropWrap', `position: absolute; left: 0; top: 0; width: ${S}px; height: ${S}px; ` + v.dropWrap);
    set('dropSquash', 'width: 100%; height: 100%; transform-origin: 50% 66%; ' + v.squash);
    set('dropPath', v.fillStyle);
    set('swPosD', 'position: absolute; ' + v.swPosD);
    set('swRotD', 'width: 100%; height: 100%; ' + v.swRot);
    set('circWrap', `position: absolute; left: 0; top: 0; width: ${S}px; height: ${S}px; ` + v.circWrap);
    set('circSquash', 'width: 100%; height: 100%; transform-origin: 50% 58%; ' + v.squash);
    set('circPath', v.fillStyle);
    set('swPosC', 'position: absolute; ' + v.swPosC);
    set('swRotC', 'width: 100%; height: 100%; ' + v.swRot);
    set('logoWrap', `position: absolute; left: 0; top: 0; width: ${S}px; height: ${S}px; display: flex; align-items: center; justify-content: center; pointer-events: none; ` + v.logoWrap);
    set('hl', 'position: absolute; border-radius: 50%; background: #FFFFFF; pointer-events: none; ' + v.hl);
    set('logoText', v.logoText);
    $('logoText').textContent = v.logoTxt;
    const badge = $('badge');
    if (v.hasBadge) {
      badge.setAttribute('style', `display: block; width: ${v.bz}px; height: ${v.bz}px; border-radius: 50%; overflow: hidden; ` + v.ring);
      badge.innerHTML = v.isAuS ? BADGES.AuS : v.isTxtS ? smallText(v) : BADGES[v.logo];
    } else {
      badge.setAttribute('style', 'display: none');
      badge.innerHTML = '';
    }
    return v;
  }
  apply(initialProps);
  return apply;
}
window.mountGosterge = mount;
