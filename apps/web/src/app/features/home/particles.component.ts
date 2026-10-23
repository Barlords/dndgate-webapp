import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  inject,
  input,
  signal,
} from '@angular/core';

export type ParticleKind = 'dust' | 'embers';

/** A golden dust mote of the library: where it starts, how far it drifts, when it sets off. */
interface Mote {
  readonly left: string;
  readonly drift: string;
  readonly delay: string;
}

/** An ember of the tavern: it rises over `rise`, and its point sways `amplitude` every `sway`. */
interface Ember extends Mote {
  readonly amplitude: string;
  readonly sway: string;
  readonly rise: string;
}

// Values of the validated mockup: spread across the half, each particle with its own rhythm.
const MOTES: readonly Mote[] = [
  { left: '8%', drift: '24px', delay: '0s' },
  { left: '19%', drift: '-18px', delay: '1.4s' },
  { left: '31%', drift: '16px', delay: '2.8s' },
  { left: '42%', drift: '-26px', delay: '0.7s' },
  { left: '53%', drift: '20px', delay: '3.6s' },
  { left: '64%', drift: '-12px', delay: '2.1s' },
  { left: '75%', drift: '28px', delay: '4.3s' },
  { left: '86%', drift: '-20px', delay: '1s' },
  { left: '93%', drift: '10px', delay: '5s' },
];

const EMBERS: readonly Ember[] = [
  { left: '7%', drift: '6px', amplitude: '10px', sway: '0.9s', rise: '3.2s', delay: '0.2s' },
  { left: '15%', drift: '-4px', amplitude: '7px', sway: '0.7s', rise: '3.6s', delay: '1.1s' },
  { left: '24%', drift: '8px', amplitude: '12px', sway: '1.1s', rise: '3s', delay: '0.5s' },
  { left: '33%', drift: '-6px', amplitude: '9px', sway: '0.8s', rise: '3.8s', delay: '1.8s' },
  { left: '42%', drift: '4px', amplitude: '11px', sway: '1s', rise: '3.3s', delay: '0.9s' },
  { left: '51%', drift: '-8px', amplitude: '8px', sway: '0.75s', rise: '3.5s', delay: '2.4s' },
  { left: '60%', drift: '6px', amplitude: '13px', sway: '1.2s', rise: '3.1s', delay: '1.5s' },
  { left: '69%', drift: '-2px', amplitude: '9px', sway: '0.85s', rise: '3.7s', delay: '0.3s' },
  { left: '78%', drift: '10px', amplitude: '10px', sway: '0.95s', rise: '3.4s', delay: '2.9s' },
  { left: '87%', drift: '-6px', amplitude: '12px', sway: '1.05s', rise: '3.2s', delay: '0.7s' },
  { left: '94%', drift: '2px', amplitude: '7px', sway: '0.8s', rise: '3.9s', delay: '2s' },
  { left: '46%', drift: '0px', amplitude: '14px', sway: '1.3s', rise: '3.6s', delay: '3.2s' },
];

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)';

/**
 * Decorative particles rising from the bottom of a home half: slow drifting dust for the library,
 * quick swaying embers for the tavern. They only move while `active`, and are not rendered at all
 * when the visitor prefers reduced motion.
 */
@Component({
  selector: 'dg-particles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', '[class.is-active]': 'active()' },
  template: `
    @if (!reducedMotion()) {
      @if (kind() === 'dust') {
        @for (mote of motes; track $index) {
          <span
            class="mote"
            [style.left]="mote.left"
            [style.--dx]="mote.drift"
            [style.--d]="mote.delay"
          ></span>
        }
      } @else {
        @for (ember of embers; track $index) {
          <span
            class="ember"
            [style.left]="ember.left"
            [style.--dx]="ember.drift"
            [style.--amp]="ember.amplitude"
            [style.--sw]="ember.sway"
            [style.--dur]="ember.rise"
            [style.--d]="ember.delay"
            ><i></i
          ></span>
        }
      }
    }
  `,
  styleUrl: './particles.component.scss',
})
export class ParticlesComponent {
  readonly kind = input.required<ParticleKind>();
  readonly active = input(false);

  protected readonly motes = MOTES;
  protected readonly embers = EMBERS;
  protected readonly reducedMotion = signal(false);

  constructor() {
    const query = inject(DOCUMENT).defaultView?.matchMedia?.(REDUCED_MOTION);
    if (!query) {
      return;
    }
    this.reducedMotion.set(query.matches);
    const follow = (event: MediaQueryListEvent) => this.reducedMotion.set(event.matches);
    query.addEventListener('change', follow);
    inject(DestroyRef).onDestroy(() => query.removeEventListener('change', follow));
  }
}
