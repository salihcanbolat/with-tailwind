// The panel (design/Masaustu.dc.html): opens over the tray drops, 340 wide, glass; the mood strip, four
// assets, the mini strip switch and the phone link.
import { emit, listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { GaugeView } from './gauge';
import { bright, closed, type DesktopState, EVENTS, freshness, price, reduceMotion, shape, word, wordColor } from './model';
import { T } from './strings';
import { type Area, place, workArea } from './window';

/** Room around the panel for its shadow (0 24 60). */
const MARGIN = { side: 48, top: 40, bottom: 88 };
const WIDTH = 340;

const win = getCurrentWindow();
let state: DesktopState | null = null;
let open = false;
let hiddenAt = 0;
let pairing = false;
let pairError = false;
let anchor: { tray: Area; area: Area } | null = null;
const views = new Map<string, GaugeView>();

const panel = document.createElement('div');
panel.className = 'panel';
panel.setAttribute('role', 'dialog');
panel.setAttribute('aria-label', T.panelLabel);
document.body.append(panel);

// The header stays put, so the live dot's 2 s ring keeps its rhythm; only the freshness text changes.
const header = el('div', 'head');
const freshText = document.createTextNode('');
const fresh = el('span', 'fresh');
fresh.append(el('span', 'live'), freshText);
header.append(dropIcon(), text('span', 'title', T.appName), el('span', 'grow'), fresh);
const body = el('div', 'panel-body');
panel.append(header, body);
let fitted = { height: -1, x: 0, y: 0 };
document.body.style.padding = `${MARGIN.top}px ${MARGIN.side}px ${MARGIN.bottom}px`;
if (reduceMotion()) document.body.classList.add('still');

function render(entering: boolean): void {
  if (state == null) return;
  const s = state;
  freshText.textContent = freshness(s, Date.now());
  body.replaceChildren();

  // Mood: how many drops rise, rest, fall and wave (design: 5 tall bars, 3 apart).
  const counts = ['UP', 'FLAT', 'DOWN', 'WAVE'].map((k) => s.assets.filter((a) => shape(a.asset.drop) === k).length);
  const labels = [T.statesLower.UP, T.statesLower.FLAT, T.statesLower.DOWN, T.statesLower.WAVE];
  const mood = el('div', 'mood');
  const bars = el('div', 'bars');
  const words = el('div', 'counts');
  ['UP', 'FLAT', 'DOWN', 'WAVE'].forEach((k, i) => {
    if (counts[i] === 0) return;
    const bar = el('span', 'bar');
    bar.style.flexGrow = String(counts[i]);
    bar.style.background = bright(k, false);
    bars.append(bar);
    const count = el('span');
    count.append(text('b', '', String(counts[i])), document.createTextNode(` ${labels[i]}`));
    words.append(count);
  });
  mood.append(bars, words);
  body.append(mood);

  const rows = el('div', 'rows');
  s.assets.forEach((item, i) => {
    const { asset } = item;
    const row = el('div', 'row');
    if (entering && !reduceMotion()) row.style.animation = `msGel 420ms cubic-bezier(0.32, 0.72, 0, 1) ${80 + i * 45}ms both`;
    let view = views.get(asset.id);
    if (view == null) {
      view = new GaugeView(34, true);
      views.set(asset.id, view);
    }
    view.show(item);
    const names = el('span', 'names');
    names.append(text('span', 'name', item.name), text('span', 'price', price(asset)));
    const side = el('span', 'side');
    const stateWord = text('span', 'word', word(asset));
    stateWord.style.color = wordColor(asset);
    const parts = el('span', 'parts');
    [asset.drop, asset.upperRing, asset.lowerRing].forEach((part, j) => {
      const bar = el('span', 'part');
      bar.style.background = bright(shape(part), closed(asset));
      bar.title = `${T.parts[j]}: ${closed(asset) ? T.states.CLOSED : T.states[shape(part)]}`;
      parts.append(bar);
    });
    side.append(stateWord, parts);
    row.append(view.element, names, side);
    rows.append(row);
  });
  body.append(rows);

  const switchRow = el('div', 'switch-row');
  switchRow.append(text('span', 'grow', T.stripSwitch));
  const toggle = el('button', 'switch' + (s.stripOn ? ' on' : '')) as HTMLButtonElement;
  toggle.type = 'button';
  toggle.setAttribute('role', 'switch');
  toggle.setAttribute('aria-checked', String(s.stripOn));
  toggle.setAttribute('aria-label', T.stripSwitch);
  toggle.append(el('span', 'knob'));
  toggle.onclick = () => void emit(EVENTS.strip, !s.stripOn);
  switchRow.append(toggle);
  body.append(switchRow);

  body.append(pairing ? pairForm(s) : syncLine(s));
  void fit();
}

function syncLine(s: DesktopState): HTMLElement {
  const line = el('div', 'sync');
  if (s.paired) {
    line.append(checkIcon(), text('span', 'grow', T.synced));
    line.append(link(T.settings, () => setPairing(true)));
  } else {
    line.append(text('span', 'grow', T.defaultList));
    line.append(link(T.pair, () => setPairing(true)));
  }
  return line;
}

/** Not in the design: typing the phone's code, or forgetting the phone (docs/08, pairing proposal). */
function pairForm(s: DesktopState): HTMLElement {
  const form = el('div', 'pair');
  if (s.paired) {
    form.append(link(T.unpair, () => void emit(EVENTS.pair, null).then(() => setPairing(false))));
    return form;
  }
  form.append(text('span', 'hint', T.pairHint));
  const input = document.createElement('input');
  input.className = 'code';
  input.maxLength = 7;
  input.autocomplete = 'off';
  input.spellcheck = false;
  input.setAttribute('aria-label', T.pairCode);
  const button = text('button', 'pair-button', T.pairButton) as HTMLButtonElement;
  button.type = 'button';
  button.onclick = () => void emit(EVENTS.pair, input.value);
  input.onkeydown = (e) => {
    if (e.key === 'Enter') button.click();
  };
  const line = el('div', 'pair-line');
  line.append(input, button);
  form.append(line);
  if (pairError) form.append(text('span', 'error', T.pairFailed));
  setTimeout(() => input.focus(), 0);
  return form;
}

function setPairing(on: boolean): void {
  pairing = on;
  pairError = false;
  render(false);
}

/** Sizes the window to the panel and keeps it by the tray: above it on Windows, below the menu bar on macOS. */
async function fit(): Promise<void> {
  if (!open || anchor == null) return;
  const { tray, area } = anchor;
  const scale = await win.scaleFactor();
  const top = tray.y < area.y + area.height / 2;
  const height = panel.getBoundingClientRect().height;
  const right = Math.min(area.x + area.width - 8 * scale, Math.max(area.x + (WIDTH + 8) * scale, tray.x + tray.width));
  const x = right - (WIDTH + MARGIN.side) * scale;
  const y = top ? tray.y + tray.height + 6 * scale - MARGIN.top * scale : area.y + area.height - 10 * scale - (height + MARGIN.top) * scale;
  if (fitted.height === height && fitted.x === x && fitted.y === y) return;
  fitted = { height, x, y };
  await place(WIDTH + 2 * MARGIN.side, height + MARGIN.top + MARGIN.bottom, x, y);
}

async function show(tray: Area): Promise<void> {
  const area = await workArea();
  anchor = { tray, area };
  document.body.classList.toggle('from-top', tray.y < area.y + area.height / 2);
  open = true;
  render(true);
  await fit();
  await win.show();
  await win.setFocus();
  requestAnimationFrame(() => document.body.classList.add('open'));
  void emit('damla:panel-open', true);
}

async function hide(): Promise<void> {
  if (!open) return;
  open = false;
  hiddenAt = Date.now();
  pairing = false;
  document.body.classList.remove('open');
  void emit('damla:panel-open', false);
  setTimeout(() => {
    if (!open) void win.hide();
  }, 360);
}

void listen<DesktopState>(EVENTS.state, (event) => {
  state = event.payload;
  if (open) render(false);
});
void listen<boolean>('damla:paired', (event) => {
  if (event.payload) setPairing(false);
  else {
    pairError = true;
    render(false);
  }
});
void listen<Area>('damla:tray', (event) => {
  // A click on the tray first takes the focus away, which already closed the panel: that click closes it.
  if (open) void hide();
  else if (Date.now() - hiddenAt > 300) void show(event.payload);
});
void win.onFocusChanged(({ payload }) => {
  if (!payload) void hide();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') void hide();
});
setInterval(() => {
  if (open && state != null) freshText.textContent = freshness(state, Date.now());
}, 1000);
void emit(EVENTS.hello);

function el(tag: string, className = ''): HTMLElement {
  const node = document.createElement(tag);
  if (className) node.className = className;
  return node;
}

function text(tag: string, className: string, value: string): HTMLElement {
  const node = el(tag, className);
  node.textContent = value;
  return node;
}

function link(label: string, onClick: () => void): HTMLElement {
  const node = text('button', 'link', label) as HTMLButtonElement;
  node.type = 'button';
  node.onclick = onClick;
  return node;
}

function dropIcon(): SVGElement {
  return svg('<svg width="12" height="14" viewBox="0 0 24 24" fill="#F2F4F7" aria-hidden="true"><path d="M12 2.5c3.2 4.3 6.5 8 6.5 11.7a6.5 6.5 0 0 1-13 0c0-3.7 3.3-7.4 6.5-11.7z"/></svg>');
}

function checkIcon(): SVGElement {
  return svg('<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#30D158" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>');
}

function svg(markup: string): SVGElement {
  const holder = document.createElement('span');
  holder.innerHTML = markup;
  return holder.firstElementChild as SVGElement;
}

