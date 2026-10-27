// The hidden core window: follows the backend, keeps the tray drops current and tells the other windows
// what to show (docs/08, "Bilgisayar eşlikçisi").
import { invoke } from '@tauri-apps/api/core';
import { emit, listen } from '@tauri-apps/api/event';
import { DesktopGauge, gaugeHeight } from 'damla-gauge';
import { api, type AssetSnapshot, type PriceEvent } from './api';
import { closed, type DesktopState, EVENTS, reduceMotion, shape, type Shown, type TurnEvent, wordLower } from './model';
import { T } from './strings';

/** The designs' assets, in their order, until a phone is paired. */
const DEFAULT_ORDER = ['xau_gram', 'usdtry', 'xag_gram', 'btcusdt'];
/** The panel lists four (the widget's flow); the tray and the strip show the first three. */
const PANEL_ASSETS = 4;
const TRAY_ASSETS = 3;
const SNAPSHOT_EVERY = 60_000;
const DEVICE_EVERY = 5 * 60_000;
/** "Nabız: Normal" (docs/kararlar.md): at most one light turn per asset in this time. */
const PULSE_GAP = 1_800;
const TRAY_PX = 32;
const TRAY_FRAME = 66;

const storage = {
  get: (key: string) => {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set: (key: string, value: string | null) => {
    try {
      if (value == null) localStorage.removeItem(key);
      else localStorage.setItem(key, value);
    } catch {
      // Storage unavailable: the setting lasts until the app closes.
    }
  },
};

let device = storage.get('damla.device');
let ids = DEFAULT_ORDER;
let names = new Map<string, string>();
const turns = new Map<string, number>();
const lastPulse = new Map<string, number>();
let previous = new Map<string, string>();
let stream: EventSource | null = null;
let panelOpen = false;

const state: DesktopState = {
  assets: [],
  paired: device != null,
  online: true,
  updatedAt: null,
  stripOn: storage.get('damla.strip') !== 'off',
};

function broadcast(): void {
  void emit(EVENTS.state, state);
  void drawTray();
}

/** The phone's watch list in its widget order, or the default list. */
async function loadList(): Promise<void> {
  if (device == null) {
    ids = DEFAULT_ORDER;
    names = new Map();
  } else {
    try {
      const settings = await api.device(device);
      const watched = settings.assets.map((a) => a.id);
      const ordered = [...settings.order.filter((id) => watched.includes(id)), ...watched.filter((id) => !settings.order.includes(id))];
      ids = ordered.slice(0, PANEL_ASSETS);
      names = new Map(settings.assets.filter((a) => a.name).map((a) => [a.id, a.name as string]));
    } catch {
      // Keep the last list; the next round tries again.
    }
  }
  restartStream();
}

async function refresh(): Promise<void> {
  if (ids.length === 0) return;
  try {
    const snapshot = await api.snapshot(ids, device);
    state.online = true;
    apply(snapshot.assets);
  } catch {
    state.online = false;
    broadcast();
  }
}

function apply(assets: AssetSnapshot[]): void {
  const turned: TurnEvent[] = [];
  const next = new Map<string, string>();
  for (const asset of assets) {
    const now = asset.drop?.state;
    if (now == null) continue;
    next.set(asset.id, now);
    const before = previous.get(asset.id);
    // A turn of the drop between open-market states is news (docs/08); closing and opening are not.
    if (before != null && before !== now && before !== 'CLOSED' && now !== 'CLOSED') {
      turned.push({ asset, name: nameOf(asset), to: shape(asset.drop), pulseTurns: turns.get(asset.id) ?? 0 });
    }
  }
  previous = next;
  state.assets = assets.filter((a) => a.drop && a.upperRing && a.lowerRing).map((asset) => shown(asset));
  const times = assets.map((a) => (a.updatedAt ? Date.parse(a.updatedAt) : 0)).filter((t) => t > 0);
  state.updatedAt = times.length ? Math.max(...times) : null;
  broadcast();
  for (const event of turned) void showTurn(event);
}

function shown(asset: AssetSnapshot): Shown {
  return { asset, name: nameOf(asset), pulseTurns: turns.get(asset.id) ?? 0 };
}

function nameOf(asset: AssetSnapshot): string {
  return names.get(asset.id) ?? asset.name;
}

async function showTurn(event: TurnEvent): Promise<void> {
  await invoke('show_toast');
  await emit(EVENTS.turn, { ...event, panelOpen, paired: state.paired });
}

/** New prices turn the drop's light (docs/06, "Nabız") and keep "şimdi" honest. */
function onPrice(event: PriceEvent): void {
  const shownAsset = state.assets.find((s) => s.asset.id === event.asset);
  if (shownAsset == null) return;
  const now = Date.now();
  shownAsset.asset = { ...shownAsset.asset, price: event.price, updatedAt: event.at };
  state.updatedAt = Date.parse(event.at);
  if (event.direction !== 0 && now - (lastPulse.get(event.asset) ?? 0) >= PULSE_GAP) {
    lastPulse.set(event.asset, now);
    const count = (turns.get(event.asset) ?? 0) + (event.direction > 0 ? 1 : -1);
    turns.set(event.asset, count);
    shownAsset.pulseTurns = count;
  }
  broadcast();
}

function restartStream(): void {
  stream?.close();
  stream = api.stream(ids, onPrice);
}

// The tray: one icon per asset, each its drop without a logo (design: 16 px drops in the tray).
const trayGauges: { gauge: DesktopGauge; key: string; id: string }[] = [];
let trayTimer = 0;
let trayDrawing = false;

async function drawTray(): Promise<void> {
  const shownAssets = state.assets.slice(0, TRAY_ASSETS);
  const now = performance.now();
  shownAssets.forEach((item, i) => {
    const { asset } = item;
    const args = [shape(asset.drop), shape(asset.upperRing), shape(asset.lowerRing), closed(asset), item.pulseTurns] as const;
    const key = args.join('|');
    const slot = trayGauges[i];
    if (slot == null || slot.id !== asset.id) {
      trayGauges[i] = { gauge: new DesktopGauge(...args, reduceMotion()), key, id: asset.id };
    } else if (slot.key !== key) {
      slot.gauge.update(...args, now);
      slot.key = key;
    }
  });
  trayGauges.length = shownAssets.length;
  await invoke('clear_trays', { from: shownAssets.length });
  if (!trayDrawing) void trayFrame();
}

async function trayFrame(): Promise<void> {
  trayDrawing = true;
  window.clearTimeout(trayTimer);
  const now = performance.now();
  const shownAssets = state.assets.slice(0, TRAY_ASSETS);
  let moving = false;
  for (let i = 0; i < trayGauges.length; i++) {
    const { gauge } = trayGauges[i];
    const item = shownAssets[i];
    if (item == null) continue;
    const rgba = await rasterize(gauge.svg(now, traySize(), ''));
    await invoke('set_tray', { index: i, rgba, width: TRAY_PX, height: TRAY_PX, tooltip: T.gauge(item.name, wordLower(item.asset)) });
    if (!gauge.settled(now)) moving = true;
  }
  trayDrawing = moving;
  if (moving) trayTimer = window.setTimeout(() => void trayFrame(), TRAY_FRAME);
}

/** The widest drop whose height still fits the square tray icon. */
function traySize(): number {
  let size = Math.floor((TRAY_PX * 76) / 92);
  while (gaugeHeight(size) > TRAY_PX) size--;
  return size;
}

async function rasterize(svg: string): Promise<number[]> {
  const image = new Image();
  image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = TRAY_PX;
  canvas.height = TRAY_PX;
  const context = canvas.getContext('2d') as CanvasRenderingContext2D;
  context.drawImage(image, Math.round((TRAY_PX - image.width) / 2), Math.round((TRAY_PX - image.height) / 2));
  return Array.from(context.getImageData(0, 0, TRAY_PX, TRAY_PX).data);
}

async function start(): Promise<void> {
  await listen(EVENTS.hello, () => broadcast());
  await listen<boolean>(EVENTS.strip, async (event) => {
    state.stripOn = event.payload;
    storage.set('damla.strip', event.payload ? 'on' : 'off');
    broadcast();
  });
  await listen<boolean>('damla:panel-open', (event) => {
    panelOpen = event.payload;
  });
  await listen<string | null>(EVENTS.pair, async (event) => {
    if (event.payload == null) {
      device = null;
    } else {
      const paired = await api.pair(event.payload.trim());
      await emit('damla:paired', paired != null);
      if (paired == null) return;
      device = paired;
    }
    storage.set('damla.device', device);
    state.paired = device != null;
    previous = new Map();
    await loadList();
    await refresh();
  });
  await loadList();
  await refresh();
  window.setInterval(() => void refresh(), SNAPSHOT_EVERY);
  window.setInterval(() => void loadList().then(refresh), DEVICE_EVERY);
}

void start();
