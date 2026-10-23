import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icon } from '../icon/icon';

/**
 * Previous / next pager over a list paged by the API. `page` counts from 0 like the API; the page
 * shown to the reader counts from 1. Hidden when everything fits on one page.
 */
@Component({
  selector: 'dg-paginator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (totalPages() > 1) {
      <nav class="dg-paginator" aria-label="Pagination">
        <button
          type="button"
          class="dg-paginator__step"
          aria-label="Page précédente"
          [disabled]="page() <= 0"
          (click)="pageChange.emit(page() - 1)"
        >
          <dg-icon name="chevron-left" />
        </button>
        <span class="dg-paginator__current" aria-current="page">
          Page {{ page() + 1 }} sur {{ totalPages() }}
        </span>
        <button
          type="button"
          class="dg-paginator__step"
          aria-label="Page suivante"
          [disabled]="page() >= totalPages() - 1"
          (click)="pageChange.emit(page() + 1)"
        >
          <dg-icon name="chevron-right" />
        </button>
      </nav>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .dg-paginator {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--dg-space-4);
      margin-top: var(--dg-space-5);
    }

    // Gold rules fading out on each side, as under a chapter heading.
    .dg-paginator::before,
    .dg-paginator::after {
      content: '';
      flex: 1;
      max-width: 6rem;
      height: 1px;
      background: linear-gradient(90deg, transparent, var(--dg-parchment-border));
    }

    .dg-paginator::after {
      background: linear-gradient(90deg, var(--dg-parchment-border), transparent);
    }

    .dg-paginator__current {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.12em;
      white-space: nowrap;
    }

    .dg-paginator__step {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.5rem;
      height: 2.5rem;
      padding: 0;
      color: var(--dg-quiet-color);
      background: transparent;
      border: 1px solid color-mix(in srgb, currentColor 45%, transparent);
      border-radius: 50%;
      cursor: pointer;
      transition:
        background-color var(--dg-transition),
        border-color var(--dg-transition);

      &:hover:not(:disabled) {
        background: color-mix(in srgb, currentColor 10%, transparent);
        border-color: currentColor;
      }

      &:disabled {
        cursor: not-allowed;
        opacity: 0.35;
      }
    }
  `,
})
export class Paginator {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();
}
