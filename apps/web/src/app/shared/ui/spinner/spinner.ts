import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18n } from '../../../core/i18n/i18n.service';

/**
 * Busy indicator: a ring of gold runes turning around a small lozenge. With reduced motion it only
 * glows. `label` is announced to assistive technologies (the dictionary's « loading » without it);
 * pass an empty label when the surrounding control already says it is busy.
 */
@Component({
  selector: 'dg-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': 'said() ? "status" : null',
  },
  template: `
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle class="ring" cx="12" cy="12" r="9.5" />
      <circle class="runes" cx="12" cy="12" r="9.5" />
      <path class="core" d="M12 8.5 15.5 12 12 15.5 8.5 12z" />
    </svg>
    @if (said(); as said) {
      <span class="dg-visually-hidden">{{ said }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      width: var(--dg-spinner-size, 1.5em);
      height: var(--dg-spinner-size, 1.5em);
      flex: none;
      color: var(--dg-spinner-color, var(--dg-gold));
    }

    svg {
      width: 100%;
      height: 100%;
      overflow: visible;
    }

    circle {
      fill: none;
      stroke: currentColor;
    }

    .ring {
      stroke-width: 0.75;
      opacity: 0.35;
    }

    .runes {
      stroke-width: 2;
      stroke-dasharray: 1 3.2 6 3.2;
      stroke-linecap: round;
      transform-origin: center;
      animation: dg-spin 1.6s linear infinite;
    }

    .core {
      fill: currentColor;
      transform-origin: center;
      animation: dg-glow 1.6s ease-in-out infinite;
    }

    @keyframes dg-spin {
      to {
        transform: rotate(360deg);
      }
    }

    @keyframes dg-glow {
      50% {
        opacity: 0.4;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .runes {
        animation: dg-glow 2.4s ease-in-out infinite;
      }

      .core {
        animation-duration: 2.4s;
      }
    }
  `,
})
export class Spinner {
  private readonly t = inject(I18n).t;

  readonly label = input<string | null>(null);
  protected readonly said = computed(() => this.label() ?? this.t().common.loading.label);
}
