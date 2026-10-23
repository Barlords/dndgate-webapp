import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ParticleKind, ParticlesComponent } from './particles.component';

export type HomeSide = 'library' | 'tavern';

/**
 * One half of the home page: a full-height link whose illumination (gold frame drawn around it,
 * rotating seal, zoom, particles) plays while it is `active`. A mouse hover or a keyboard focus
 * activates it; on a touch screen, which has no hover, the first tap activates it and the second
 * follows the link.
 */
@Component({
  selector: 'dg-home-half',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ParticlesComponent],
  host: {
    '[class]': "'home-half--' + side()",
    '[class.is-active]': 'active()',
    '[class.is-dimmed]': 'dimmed()',
    // `title` is an input here, not a tooltip: drop the attribute a static binding leaves behind.
    '[attr.title]': 'null',
  },
  template: `
    <a
      class="home-half__link"
      [routerLink]="link()"
      (pointerenter)="hover($event, true)"
      (pointerleave)="hover($event, false)"
      (pointerdown)="pointerDown($event)"
      (focus)="focus($event)"
      (blur)="activeChange.emit(false)"
    >
      <span class="home-half__image"></span>
      <span class="home-half__shade"></span>
      <span class="home-half__seal" aria-hidden="true">
        <svg viewBox="0 0 100 100" fill="none" stroke="currentColor" stroke-width="1">
          <circle cx="50" cy="50" r="46" />
          <circle cx="50" cy="50" r="38" stroke-dasharray="3 4" />
          <polygon points="50,8 86,71 14,71" />
          <polygon points="50,92 14,29 86,29" />
        </svg>
      </span>
      <!-- Drawn in CSS pixels (no viewBox): pathLength then maps the dashes onto the true perimeter. -->
      <svg class="home-half__frame" aria-hidden="true">
        <rect width="100%" height="100%" pathLength="1400" />
      </svg>
      <dg-particles [kind]="particles()" [active]="active()" />
      <div class="home-half__label">
        <h2 class="home-half__title">{{ title() }}</h2>
        <span class="home-half__subtitle">{{ subtitle() }}</span>
        <span class="home-half__cta">{{ cta() }}</span>
        @if (hint()) {
          <span class="dg-visually-hidden">({{ hint() }})</span>
        }
      </div>
    </a>
  `,
  styleUrl: './home-half.component.scss',
})
export class HomeHalfComponent {
  readonly side = input.required<HomeSide>();
  readonly link = input.required<string>();
  readonly title = input.required<string>();
  readonly subtitle = input.required<string>();
  readonly cta = input.required<string>();
  /** Extra words read by assistive technologies only, e.g. that the link asks to sign in. */
  readonly hint = input('');
  readonly active = input(false);
  readonly dimmed = input(false);
  readonly activeChange = output<boolean>();

  protected readonly particles = computed<ParticleKind>(() =>
    this.side() === 'library' ? 'dust' : 'embers',
  );

  /** Whether the current press is a tap that should only light the half up. */
  private tapToActivate = false;

  constructor() {
    // Captured on the host, so it runs before the RouterLink listening on the link itself.
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    host.addEventListener('click', (event) => this.tap(event), { capture: true });
  }

  protected hover(event: PointerEvent, entering: boolean): void {
    // A finger "hovers" right before its tap: the tap decides, see `pointerDown`.
    if (event.pointerType !== 'touch') {
      this.activeChange.emit(entering);
    }
  }

  protected pointerDown(event: PointerEvent): void {
    // Read before the focus and the emulated mouse events of the tap can activate the half.
    this.tapToActivate = event.pointerType === 'touch' && !this.active();
  }

  protected focus(event: FocusEvent): void {
    // Only a keyboard focus lights the half up: a click focuses the link while following it.
    if ((event.target as HTMLElement).matches(':focus-visible')) {
      this.activeChange.emit(true);
    }
  }

  /**
   * First tap on a touch screen: light the half up instead of following the link. Stopping the
   * click before it reaches the link keeps both the browser and the RouterLink from navigating.
   */
  private tap(event: MouseEvent): void {
    if (this.tapToActivate) {
      this.tapToActivate = false;
      event.preventDefault();
      event.stopPropagation();
      this.activeChange.emit(true);
    }
  }
}
