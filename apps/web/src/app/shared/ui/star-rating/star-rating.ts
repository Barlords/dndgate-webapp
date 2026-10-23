import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { Icon } from '../icon/icon';

const STARS = [1, 2, 3, 4, 5] as const;

/**
 * Rating from 1 to 5 gold stars. Read-only, it shows a value (an average is rounded to the nearest
 * star); otherwise each star is a choice and hovering previews it.
 */
@Component({
  selector: 'dg-star-rating',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (readonly()) {
      <span class="dg-star-rating" role="img" [attr.aria-label]="'Note : ' + shown() + ' sur 5'">
        @for (star of stars; track star) {
          <span class="dg-star" [class.dg-star--on]="star <= shown()">
            <dg-icon name="star" [filled]="star <= shown()" />
          </span>
        }
      </span>
    } @else {
      <span
        class="dg-star-rating dg-star-rating--editable"
        role="radiogroup"
        aria-label="Note"
        (mouseleave)="hovered.set(null)"
      >
        @for (star of stars; track star) {
          <button
            type="button"
            role="radio"
            class="dg-star"
            [class.dg-star--on]="star <= shown()"
            [attr.aria-checked]="star === rounded()"
            [attr.aria-label]="star + ' sur 5'"
            (mouseenter)="hovered.set(star)"
            (click)="valueChange.emit(star)"
          >
            <dg-icon name="star" [filled]="star <= shown()" />
          </button>
        }
      </span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .dg-star-rating {
      display: inline-flex;
      gap: 0.125rem;
      --dg-icon-size: 1.25rem;
    }

    .dg-star {
      display: inline-flex;
      padding: 0;
      color: var(--dg-gold-deep);
      background: none;
      border: 0;
      border-radius: var(--dg-radius-sm);
      opacity: 0.7;
    }

    .dg-star--on {
      color: var(--dg-star-color, var(--dg-gold));
      opacity: 1;
      filter: drop-shadow(0 0 1px #5c4528);
    }

    .dg-star-rating--editable {
      --dg-icon-size: 1.6rem;
    }

    button.dg-star {
      cursor: pointer;
      transition: transform var(--dg-transition);

      &:hover {
        transform: scale(1.12);
      }
    }
  `,
})
export class StarRating {
  readonly value = input<number | null>(null);
  readonly readonly = input(false);
  readonly valueChange = output<number>();

  protected readonly stars = STARS;
  protected readonly hovered = signal<number | null>(null);
  protected readonly rounded = computed(() => Math.round(this.value() ?? 0));
  protected readonly shown = computed(() =>
    this.readonly() ? this.rounded() : (this.hovered() ?? this.rounded()),
  );
}
