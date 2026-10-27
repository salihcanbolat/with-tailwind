// The backend's v1 API (docs/03), as the desktop uses it.

export interface PartSnapshot {
  interval: string;
  state: string;
  shapeState: string;
  net: number;
  range: number;
  since: string;
}

export interface AssetSnapshot {
  id: string;
  kind: string;
  name: string;
  short: string;
  badge: string;
  price: number | null;
  currency: string | null;
  updatedAt: string | null;
  source: string | null;
  marketOpen: boolean;
  drop: PartSnapshot | null;
  upperRing: PartSnapshot | null;
  lowerRing: PartSnapshot | null;
}

export interface SnapshotResponse {
  serverTime: string;
  assets: AssetSnapshot[];
}

export interface AssetSettings {
  id: string;
  name?: string | null;
}

export interface DeviceSettings {
  assets: AssetSettings[];
  order: string[];
}

export interface PriceEvent {
  asset: string;
  price: number;
  direction: number;
  at: string;
}

export const API_BASE: string = (import.meta.env.VITE_DAMLA_API as string | undefined) ?? 'http://localhost:8080';

async function get<T>(path: string): Promise<T> {
  const response = await fetch(API_BASE + path);
  if (!response.ok) throw new Error(`${response.status} ${path}`);
  return (await response.json()) as T;
}

export const api = {
  snapshot: (ids: string[], device: string | null) =>
    get<SnapshotResponse>(`/v1/snapshot?assets=${encodeURIComponent(ids.join(','))}${device ? `&device=${encodeURIComponent(device)}` : ''}`),

  device: (id: string) => get<DeviceSettings>(`/v1/devices/${encodeURIComponent(id)}`),

  /** The phone behind a pairing code, or null for an unknown or expired one. */
  async pair(code: string): Promise<string | null> {
    const response = await fetch(`${API_BASE}/v1/pairings/${encodeURIComponent(code)}`, { method: 'POST' });
    if (!response.ok) return null;
    return ((await response.json()) as { deviceId: string }).deviceId;
  },

  /** Live prices for [ids]; the browser reconnects on its own after a drop. */
  stream(ids: string[], onPrice: (event: PriceEvent) => void): EventSource {
    const source = new EventSource(`${API_BASE}/v1/stream?assets=${encodeURIComponent(ids.join(','))}`);
    source.addEventListener('price', (message) => onPrice(JSON.parse((message as MessageEvent<string>).data) as PriceEvent));
    return source;
  },
};
