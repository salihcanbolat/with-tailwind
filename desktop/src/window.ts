// Placing the transparent windows on screen. Sizes are in CSS pixels; the OS wants physical ones.
import { currentMonitor, getCurrentWindow, PhysicalPosition, PhysicalSize } from '@tauri-apps/api/window';

export interface Area {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The screen without the taskbar or menu bar, in physical pixels. */
export async function workArea(): Promise<Area> {
  const monitor = await currentMonitor();
  if (monitor == null) return { x: 0, y: 0, width: screen.availWidth * devicePixelRatio, height: screen.availHeight * devicePixelRatio };
  const area = (monitor as unknown as { workArea?: { position: PhysicalPosition; size: PhysicalSize } }).workArea;
  const position = area?.position ?? monitor.position;
  const size = area?.size ?? monitor.size;
  return { x: position.x, y: position.y, width: size.width, height: size.height };
}

/** Resizes the window to [width] x [height] CSS pixels and puts its top left at (x, y) physical pixels. */
export async function place(width: number, height: number, x: number, y: number): Promise<void> {
  const window = getCurrentWindow();
  const scale = await window.scaleFactor();
  await window.setSize(new PhysicalSize(Math.round(width * scale), Math.round(height * scale)));
  await window.setPosition(new PhysicalPosition(Math.round(x), Math.round(y)));
}

export const isMac = navigator.userAgent.includes('Mac');

/** The designs' curve `easeStandard` (docs/05). */
export function easeStandard(t: number): number {
  return cubicBezier(0.32, 0.72, 0, 1, t);
}

function cubicBezier(x1: number, y1: number, x2: number, y2: number, t: number): number {
  // Solve x(s) = t for s by Newton's method, then return y(s).
  const bx = (s: number) => 3 * x1 * s * (1 - s) ** 2 + 3 * x2 * s * s * (1 - s) + s ** 3;
  const by = (s: number) => 3 * y1 * s * (1 - s) ** 2 + 3 * y2 * s * s * (1 - s) + s ** 3;
  let s = t;
  for (let i = 0; i < 8; i++) {
    const dx = 3 * x1 * (1 - s) ** 2 + 6 * (x2 - x1) * s * (1 - s) + 3 * (1 - x2) * s * s;
    if (Math.abs(dx) < 1e-6) break;
    s -= (bx(s) - t) / dx;
    s = Math.min(1, Math.max(0, s));
  }
  return by(s);
}
