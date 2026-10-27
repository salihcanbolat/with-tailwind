// The mini strip (design/Masaustu.dc.html): an always-on-top glass capsule with three drops. It can be
// dragged anywhere; let go, it settles against the nearer side edge (420 ms easeStandard, docs/08).
import { emit, listen } from '@tauri-apps/api/event';
import { getCurrentWindow, PhysicalPosition } from '@tauri-apps/api/window';
import { GaugeView } from './gauge';
import { type DesktopState, EVENTS, reduceMotion, word, wordColor } from './model';
import { T } from './strings';
import { easeStandard, place, workArea } from './window';

/** Room around the capsule for its shadow (0 16 40). */
const MARGIN = { side: 40, top: 28, bottom: 60 };
const EDGE = 20;
const CLEAR = 14;

const win = getCurrentWindow();
const views = new Map<string, GaugeView>();
let visible = false;
let placed = false;
let drag: { screenX: number; screenY: number; x: number; y: number } | null = null;

const strip = document.createElement('div');
strip.className = 'strip';
strip.setAttribute('role', 'group');
strip.setAttribute('aria-label', T.stripLabel);
document.body.append(strip);
document.body.style.padding = `${MARGIN.top}px ${MARGIN.side}px ${MARGIN.bottom}px`;
if (reduceMotion()) document.body.classList.add('still');

function render(state: DesktopState): void {
  strip.replaceChildren();
  const grip = document.createElement('span');
  grip.className = 'grip';
  grip.setAttribute('aria-hidden', 'true');
  for (let i = 0; i < 6; i++) grip.append(document.createElement('span'));
  strip.append(grip);
  for (const item of state.assets.slice(0, 3)) {
    let view = views.get(item.asset.id);
    if (view == null) {
      view = new GaugeView(28, true);
      views.set(item.asset.id, view);
    }
    view.show(item);
    const cell = document.createElement('span');
    cell.className = 'cell';
    const texts = document.createElement('span');
    texts.className = 'texts';
    const short = document.createElement('span');
    short.className = 'short';
    short.textContent = item.asset.short;
    const stateWord = document.createElement('span');
    stateWord.className = 'word';
    stateWord.textContent = word(item.asset);
    stateWord.style.color = wordColor(item.asset);
    texts.append(short, stateWord);
    cell.append(view.element, texts);
    strip.append(cell);
  }
}

let sized = { width: -1, height: -1 };

async function size(): Promise<void> {
  const box = strip.getBoundingClientRect();
  if (placed) {
    // Only when the capsule's size changes (new words); otherwise prices would resize it every second.
    if (sized.width === box.width && sized.height === box.height) return;
    sized = { width: box.width, height: box.height };
    const position = await win.outerPosition();
    await place(box.width + 2 * MARGIN.side, box.height + MARGIN.top + MARGIN.bottom, position.x, position.y);
    return;
  }
  sized = { width: box.width, height: box.height };
  // First time: where it was last left, else the bottom left corner like the design.
  const area = await workArea();
  const scale = await win.scaleFactor();
  const saved = localStorage.getItem('damla.strip.position');
  const [x, y] = saved
    ? (JSON.parse(saved) as [number, number])
    : [area.x + (EDGE - MARGIN.side) * scale, area.y + area.height - (EDGE + box.height + MARGIN.top) * scale];
  await place(box.width + 2 * MARGIN.side, box.height + MARGIN.top + MARGIN.bottom, x, y);
  placed = true;
}

async function setVisible(on: boolean): Promise<void> {
  if (on === visible) return;
  visible = on;
  if (on) {
    await size();
    await win.show();
    requestAnimationFrame(() => document.body.classList.add('on'));
  } else {
    document.body.classList.remove('on');
    setTimeout(() => {
      if (!visible) void win.hide();
    }, 300);
  }
}

strip.addEventListener('pointerdown', async (e) => {
  strip.setPointerCapture(e.pointerId);
  const position = await win.outerPosition();
  drag = { screenX: e.screenX, screenY: e.screenY, x: position.x, y: position.y };
  document.body.classList.add('dragging');
});

strip.addEventListener('pointermove', (e) => {
  if (drag == null) return;
  const x = drag.x + (e.screenX - drag.screenX) * devicePixelRatio;
  const y = drag.y + (e.screenY - drag.screenY) * devicePixelRatio;
  void win.setPosition(new PhysicalPosition(Math.round(x), Math.round(y)));
});

const release = async (): Promise<void> => {
  if (drag == null) return;
  drag = null;
  document.body.classList.remove('dragging');
  await settle();
};
strip.addEventListener('pointerup', () => void release());
strip.addEventListener('pointercancel', () => void release());

/** To the nearer side edge, 20 in, and 14 clear of the menu bar or taskbar (design: 44 and 14 on macOS, 14 and 62 on Windows, measured from the screen edge). */
async function settle(): Promise<void> {
  const area = await workArea();
  const scale = await win.scaleFactor();
  const from = await win.outerPosition();
  const box = strip.getBoundingClientRect();
  const width = box.width * scale;
  const left = from.x + MARGIN.side * scale;
  const toLeft = left + width / 2 < area.x + area.width / 2;
  const targetLeft = toLeft ? area.x + EDGE * scale : area.x + area.width - EDGE * scale - width;
  const topLimit = area.y + CLEAR * scale;
  const bottomLimit = area.y + area.height - CLEAR * scale - box.height * scale;
  const top = Math.min(bottomLimit, Math.max(topLimit, from.y + MARGIN.top * scale));
  const to = { x: targetLeft - MARGIN.side * scale, y: top - MARGIN.top * scale };
  localStorage.setItem('damla.strip.position', JSON.stringify([to.x, to.y]));
  if (reduceMotion()) {
    await win.setPosition(new PhysicalPosition(Math.round(to.x), Math.round(to.y)));
    return;
  }
  const start = performance.now();
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / 420);
    const k = easeStandard(t);
    void win.setPosition(new PhysicalPosition(Math.round(from.x + (to.x - from.x) * k), Math.round(from.y + (to.y - from.y) * k)));
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

void listen<DesktopState>(EVENTS.state, (event) => {
  render(event.payload);
  if (visible) void size();
  void setVisible(event.payload.stripOn && event.payload.assets.length > 0);
});
void emit(EVENTS.hello);
