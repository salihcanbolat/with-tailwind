// The desktop notification of a drop turn (design/Masaustu.dc.html): slides in from the corner by the
// tray, stays 5.2 s, leaves (opacity 300 ms; slide 520 ms easeStandard).
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { GaugeView } from './gauge';
import { bright, EVENTS, reduceMotion, type TurnEvent } from './model';
import { T } from './strings';
import { isMac, place, workArea } from './window';

const MARGIN = { side: 40, top: 24, bottom: 56 };
const WIDTH = 340;
const SHOWN_FOR = 5_200;

const win = getCurrentWindow();
const toast = document.createElement('div');
toast.className = 'toast';
toast.setAttribute('role', 'status');
toast.setAttribute('aria-live', 'polite');
document.body.append(toast);
document.body.style.padding = `${MARGIN.top}px ${MARGIN.side}px ${MARGIN.bottom}px`;
if (reduceMotion()) document.body.classList.add('still');
let timer = 0;

async function show(event: TurnEvent & { panelOpen: boolean; paired: boolean }): Promise<void> {
  const view = new GaugeView(38, true);
  view.show({ asset: event.asset, name: event.name, pulseTurns: event.pulseTurns });
  const texts = document.createElement('span');
  texts.className = 'texts';
  const from = document.createElement('span');
  from.className = 'from';
  from.textContent = T.toastFrom;
  const line = document.createElement('span');
  line.className = 'line';
  const message = document.createElement('span');
  message.textContent = T.turned[event.to];
  message.style.color = bright(event.to, false);
  line.append(document.createTextNode(`${event.name} `), message);
  const also = document.createElement('span');
  also.className = 'from';
  also.textContent = T.toastAlso;
  // The phone (and its watch) got the same push only when this computer follows that phone.
  texts.append(from, line);
  if (event.paired) texts.append(also);
  toast.replaceChildren(view.element, texts);

  // By the tray: bottom right above the taskbar (12 up, 16 in) or top right under the menu bar (10 down);
  // beside the panel when it is open (design: right 452).
  const area = await workArea();
  const scale = await win.scaleFactor();
  const height = toast.getBoundingClientRect().height;
  const right = (event.panelOpen ? 452 : 16) * scale;
  const x = area.x + area.width - right - (WIDTH + MARGIN.side) * scale;
  const y = isMac ? area.y + 10 * scale - MARGIN.top * scale : area.y + area.height - 12 * scale - (height + MARGIN.top) * scale;
  await place(WIDTH + 2 * MARGIN.side, height + MARGIN.top + MARGIN.bottom, x, y);
  document.body.classList.remove('on');
  await win.show();
  requestAnimationFrame(() => document.body.classList.add('on'));
  window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    document.body.classList.remove('on');
    window.setTimeout(() => void win.hide(), 520);
  }, SHOWN_FOR);
}

void listen<TurnEvent & { panelOpen: boolean; paired: boolean }>(EVENTS.turn, (event) => void show(event.payload));
