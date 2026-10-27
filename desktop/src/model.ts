// What every window shows, sent by the core window (core.ts). States come from the backend's engine; the
// desktop maps them to shapes and colours and never derives one itself (CLAUDE.md).
import type { AssetSnapshot } from './api';
import { T } from './strings';

export interface Shown {
  asset: AssetSnapshot;
  /** The user's own name (cross rates), else the catalogue's. */
  name: string;
  /** Light turns so far: +1 for each higher price, -1 for each lower one (docs/06, "Nabız"). */
  pulseTurns: number;
}

export interface DesktopState {
  assets: Shown[];
  paired: boolean;
  online: boolean;
  /** The newest price time, for "şimdi / N sn önce". */
  updatedAt: number | null;
  stripOn: boolean;
}

export interface TurnEvent {
  asset: AssetSnapshot;
  name: string;
  to: string;
  pulseTurns: number;
}

export const EVENTS = { state: 'damla:state', hello: 'damla:hello', turn: 'damla:turn', strip: 'damla:strip', pair: 'damla:pair' };

/** "Piyasa kapalıyken gri göster" is a phone setting the backend does not keep; the default is on. */
const GREY_WHEN_CLOSED = true;

export function closed(asset: AssetSnapshot): boolean {
  return asset.drop?.state === 'CLOSED' && GREY_WHEN_CLOSED;
}

export function shape(part: { shapeState: string } | null | undefined): string {
  return part?.shapeState ?? 'FLAT';
}

/** The bright tones of docs/01 for words and bars; grey while closed. */
export function bright(state: string, isClosed: boolean): string {
  if (isClosed) return '#AEAEB2';
  return ({ UP: '#30D158', DOWN: '#FF453A', FLAT: '#FFD60A', WAVE: '#FF9F0A' } as Record<string, string>)[state] ?? '#FFD60A';
}

export function word(asset: AssetSnapshot): string {
  return closed(asset) ? T.states.CLOSED : T.states[shape(asset.drop)];
}

export function wordLower(asset: AssetSnapshot): string {
  return closed(asset) ? T.statesLower.CLOSED : T.statesLower[shape(asset.drop)];
}

export function wordColor(asset: AssetSnapshot): string {
  return bright(shape(asset.drop), closed(asset));
}

/** "₺4.215,30": exchange and cross rates keep four decimals, everything else two (four below 1). */
export function price(asset: AssetSnapshot): string {
  if (asset.price == null) return '—';
  const digits = asset.kind === 'fx' || asset.kind === 'cross' || asset.price < 1 ? 4 : 2;
  const text = asset.price.toLocaleString('tr-TR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  const sign = ({ TRY: '₺', USD: '$', EUR: '€', GBP: '£' } as Record<string, string>)[asset.currency ?? ''];
  if (asset.currency == null) return text;
  return sign ? sign + text : `${text} ${asset.currency}`;
}

/** "şimdi", "12 sn önce", "3 dk önce"; late data says so (docs/03: twice the expected minute). */
export function freshness(state: DesktopState, now: number): string {
  if (!state.online) return T.offline;
  if (state.updatedAt == null) return T.waiting;
  const seconds = Math.max(0, Math.floor((now - state.updatedAt) / 1000));
  if (seconds >= 120) return T.late;
  if (seconds < 2) return T.now;
  if (seconds < 60) return T.secondsAgo(seconds);
  return T.minutesAgo(Math.floor(seconds / 60));
}

export function reduceMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
