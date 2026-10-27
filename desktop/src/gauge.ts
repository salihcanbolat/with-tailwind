// A drop on the page, drawn by the Kotlin gauge painter compiled to JavaScript (:gauge-js), frame by frame
// while it moves and not at all at rest.
import { DesktopGauge } from 'damla-gauge';
import { closed, reduceMotion, shape, type Shown, wordLower } from './model';
import { T } from './strings';

export class GaugeView {
  readonly element: HTMLElement;
  private gauge: DesktopGauge | null = null;
  private key = '';
  private badge = '';
  private frame = 0;

  constructor(
    private readonly size: number,
    private readonly logo: boolean,
  ) {
    this.element = document.createElement('span');
    this.element.className = 'gauge';
    this.element.setAttribute('role', 'img');
  }

  show(shown: Shown): void {
    const { asset } = shown;
    const args = [shape(asset.drop), shape(asset.upperRing), shape(asset.lowerRing), closed(asset), shown.pulseTurns] as const;
    const key = args.join('|');
    this.element.setAttribute('aria-label', T.gauge(shown.name, wordLower(asset)));
    if (this.gauge == null) {
      this.gauge = new DesktopGauge(...args, reduceMotion());
    } else if (key !== this.key) {
      this.gauge.update(...args, performance.now());
    }
    this.key = key;
    this.badge = this.logo ? asset.badge : '';
    this.draw();
  }

  private draw = (): void => {
    cancelAnimationFrame(this.frame);
    const gauge = this.gauge;
    if (gauge == null) return;
    const now = performance.now();
    this.element.innerHTML = gauge.svg(now, this.size, this.badge);
    if (!gauge.settled(now)) this.frame = requestAnimationFrame(this.draw);
  };
}
